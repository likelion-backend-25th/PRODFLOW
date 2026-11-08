package net.likelion.bebc25.prodflow.quotation.service;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.client.repository.ClientRepository;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.commoncode.entity.CodeGroup;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationRescheduleDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationItemDto;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationItemRepository;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import net.likelion.bebc25.prodflow.quotation.service.QuotationService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationPdfService;
import net.likelion.bebc25.prodflow.quotation.controller.QuotationController;
import net.likelion.bebc25.prodflow.quotation.service.QuotationRescheduleService;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("견적 납기 재산정 연동")
class QuotationRescheduleIntegrationTest extends QuotationIntegrationTestSupport {

    @Test
    void reschedulePreviewUsesStoredQuantityWithoutChangingQuotationOrReservingProduction() {
        var created = createSentQuotation(List.of(item(2500, 1000L)));
        var before = service.getQuotationById(created.id());

        var preview = rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(null));

        assertThat(preview.feasible()).isTrue();
        assertThat(preview.requestedDueDate()).isNull();
        assertThat(preview.plannedEndDate()).isAfter(preview.plannedStartDate());
        assertThat(service.getQuotationById(created.id())).isEqualTo(before);
        assertThat(quotationRepository.count()).isEqualTo(1);
        assertThat(itemRepository.count()).isEqualTo(1);
        tx().executeWithoutResult(status -> {
            assertThat(em.createQuery("select count(w) from WorkOrder w", Long.class).getSingleResult()).isZero();
            assertThat(em.createQuery("select count(p) from ProductionPlan p", Long.class).getSingleResult()).isZero();
        });
    }

    @Test
    void rescheduleChangesOnlyFourDatesAndPreservesIssuedItemsEvenIfProductIsDiscontinued() {
        var created = createSentQuotation(List.of(item(100, 1000L)));
        var before = service.getQuotationById(created.id());
        tx().executeWithoutResult(status ->
                ReflectionTestUtils.setField(em.find(Product.class, productId), "status", ProductStatus.DISCONTINUED));
        var earliest = rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(null));

        var updated = rescheduleService.updateSchedule(created.id(),
                new QuotationRescheduleDto.UpdateRequest(earliest.earliestDueDate()));

        assertThat(updated.requestedDueDate()).isEqualTo(earliest.earliestDueDate()).isNotEqualTo(before.requestedDueDate());
        assertThat(updated.estimatedDueDate()).isEqualTo(earliest.earliestDueDate());
        assertThat(updated.plannedStartDate()).isEqualTo(earliest.plannedStartDate());
        assertThat(updated.plannedEndDate()).isEqualTo(earliest.plannedEndDate());
        assertThat(updated).usingRecursiveComparison()
                .ignoringFields("requestedDueDate", "estimatedDueDate", "plannedStartDate", "plannedEndDate", "updatedAt")
                .isEqualTo(before);
        assertThat(service.getQuotationById(created.id())).usingRecursiveComparison()
                .ignoringFields("updatedAt").isEqualTo(updated);
        assertThat(quotationRepository.count()).isEqualTo(1);
    }

    @Test
    void impossibleRescheduleKeepsOriginalSchedule() {
        var created = createSentQuotation(List.of(item(100, 1000L)));
        var before = service.getQuotationById(created.id());

        var preview = rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(today()));
        assertThat(preview.feasible()).isFalse();
        assertThat(preview.plannedStartDate()).isNull();
        assertThatThrownBy(() -> rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(today())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("희망 납기");
        assertThat(service.getQuotationById(created.id())).isEqualTo(before);
    }

    @Test
    void rescheduleRejectsEveryNonSentStatus() {
        for (QuotationStatus quotationStatus : QuotationStatus.values()) {
            if (quotationStatus == QuotationStatus.SENT) {
                continue;
            }
            var created = createSentQuotation(List.of(item(100, 1000L)));
            tx().executeWithoutResult(status ->
                    ReflectionTestUtils.setField(em.find(Quotation.class, created.id()), "status", quotationStatus));
            assertThatThrownBy(() -> rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(null)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("발송완료");
            assertThatThrownBy(() -> rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(dueDate())))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("발송완료");
        }
    }

    @Test
    void rescheduleChecksExpiryDateEvenBeforeStatusIsExpiredAndAllowsLastValidDay() {
        var created = createSentQuotation(List.of(item(100, 1000L)));
        tx().executeWithoutResult(status ->
                ReflectionTestUtils.setField(em.find(Quotation.class, created.id()), "validUntil", today().minusDays(1)));
        assertThatThrownBy(() -> rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("유효기간");
        assertThatThrownBy(() -> rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(dueDate())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("유효기간");

        tx().executeWithoutResult(status ->
                ReflectionTestUtils.setField(em.find(Quotation.class, created.id()), "validUntil", today()));
        assertThat(rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(dueDate())).validUntil())
                .isEqualTo(today());
    }

    @Test
    void rescheduleRechecksStatusAndCapacityAfterPreview() {
        var created = createSentQuotation(List.of(item(100, 1000L)));
        var preview = rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(null));
        tx().executeWithoutResult(status -> em.find(Quotation.class, created.id()).approve());
        assertThatThrownBy(() -> rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(preview.earliestDueDate())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("발송완료");

        tx().executeWithoutResult(status -> {
            em.find(Quotation.class, created.id()).sent();
            em.createQuery("delete from Line").executeUpdate();
        });
        var before = service.getQuotationById(created.id());
        assertThatThrownBy(() -> rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(preview.earliestDueDate())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("가동 가능한 생산라인");
        assertThat(service.getQuotationById(created.id())).isEqualTo(before);
    }

    @Test
    void rescheduleRejectsMissingQuotationAndDatesBeyondNextYear() {
        assertThatThrownBy(() -> rescheduleService.previewSchedule(Long.MAX_VALUE, new QuotationRescheduleDto.PreviewRequest(null)))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> rescheduleService.updateSchedule(Long.MAX_VALUE, new QuotationRescheduleDto.UpdateRequest(dueDate())))
                .isInstanceOf(NoSuchElementException.class);
        var created = createSentQuotation(List.of(item(100, 1000L)));
        LocalDate beyond = LocalDate.of(today().getYear() + 2, 1, 1);
        assertThatThrownBy(() -> rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(beyond)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("다음 해");
        assertThatThrownBy(() -> rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(beyond)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("다음 해");
    }

    @Test
    void rescheduleRequiresOrderManagerOrSuperAdminRole() {
        var created = createSentQuotation(List.of(item(100, 1000L)));
        for (UserRole role : List.of(UserRole.PRODUCTION_MANAGER, UserRole.INQUIRY_MANAGER)) {
            authenticateAs(role);
            assertThatThrownBy(() -> rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(null)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(dueDate())))
                    .isInstanceOf(AccessDeniedException.class);
        }
        authenticateAs(UserRole.SUPER_ADMIN);
        assertThat(rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(null)).feasible()).isTrue();
    }
}
