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

@DisplayName("견적 상태·삭제 연동")
class QuotationStatusIntegrationTest extends QuotationIntegrationTestSupport {

    @Test
    void deletesDraftItemsAndPdfAfterCommitWhilePreservingNewClient() {
        var draft = pdfStorageService.create(newClientRequest("삭제 후 유지할 거래처", List.of(item(100, 1000L))), userId);
        Long id = draft.quotationId();
        var client = quotationRepository.findById(id).orElseThrow().getClient();
        String key = quotationRepository.findById(id).orElseThrow().getPdfObjectKey();
        org.mockito.Mockito.clearInvocations(storageClient, mailService);
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(quotationRepository.existsById(id)).isFalse();
            files.remove(key);
            return null;
        }).when(storageClient).delete(key);
        statusService.deleteDraft(id, userId);
        assertThat(quotationRepository.existsById(id)).isFalse();
        assertThat(itemRepository.findByQuotationIdOrderByIdAsc(id)).isEmpty();
        assertThat(clientRepository.existsById(client.getId())).isTrue();
        assertThat(files).doesNotContainKey(key);
        org.mockito.Mockito.verify(storageClient).delete(key);
        org.mockito.Mockito.verifyNoInteractions(mailService);
    }

    @Test
    void deletesDraftWithNullPdfKeyAndIgnoresStorageFailure() {
        var draft = service.create(request(List.of(item(100, 1000L))), userId);
        String key = "quotations/" + draft.quotationNo() + ".pdf";
        org.mockito.Mockito.doThrow(new IllegalStateException("S3 삭제 실패")).when(storageClient).delete(key);
        statusService.deleteDraft(draft.id(), userId);
        assertThat(quotationRepository.existsById(draft.id())).isFalse();
        assertThat(itemRepository.findByQuotationIdOrderByIdAsc(draft.id())).isEmpty();
        org.mockito.Mockito.verify(storageClient).delete(key);
    }

    @Test
    void deletionRejectsEveryNonDraftStateAndMissingQuotation() {
        for (QuotationStatus source : QuotationStatus.values()) {
            if (source == QuotationStatus.DRAFT) {
                continue;
            }
            Long id = createStatusFixture(source, today());
            assertStatusConflict(() -> statusService.deleteDraft(id, userId));
            assertThat(quotationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(source);
            assertThat(itemRepository.findByQuotationIdOrderByIdAsc(id)).isNotEmpty();
        }
        assertThatThrownBy(() -> statusService.deleteDraft(Long.MAX_VALUE, userId)).isInstanceOf(NoSuchElementException.class);
        org.mockito.Mockito.verifyNoInteractions(storageClient, mailService);
    }

    @Test
    void draftDeletionRequiresPermissionAndActiveAccount() {
        Long id = createStatusFixture(QuotationStatus.DRAFT, today());
        authenticateAs(UserRole.INQUIRY_MANAGER);
        assertThatThrownBy(() -> statusService.deleteDraft(id, userId)).isInstanceOf(AccessDeniedException.class);
        authenticateAs(UserRole.ORDER_MANAGER);
        tx().executeWithoutResult(status -> em.find(User.class, userId).deactivate());
        assertThatThrownBy(() -> statusService.deleteDraft(id, userId)).isInstanceOf(AccessDeniedException.class);
        assertThat(quotationRepository.existsById(id)).isTrue();
        org.mockito.Mockito.verifyNoInteractions(storageClient);
    }

    @Test
    void approvalRejectionAndCancellationPreserveDocumentAndRepeatedRequestIsIdempotent() {
        for (QuotationStatus target : List.of(QuotationStatus.APPROVED, QuotationStatus.REJECTED, QuotationStatus.CANCELLED)) {
            var preview = pdfStorageService.create(request(List.of(item(100, 1000L))), userId);
            Long id = preview.quotationId();
            tx().executeWithoutResult(status -> em.find(Quotation.class, id).sent());
            var before = service.getQuotationById(id);
            String key = quotationRepository.findById(id).orElseThrow().getPdfObjectKey();
            byte[] pdf = files.get(key).content().clone();
            org.mockito.Mockito.clearInvocations(storageClient, mailService);

            var result = switch (target) {
                case APPROVED -> statusService.approve(id, userId);
                case REJECTED -> statusService.reject(id, userId);
                default -> statusService.cancel(id, userId);
            };
            assertThat(result).usingRecursiveComparison().ignoringFields("status", "updatedAt").isEqualTo(before);
            assertThat(result.status()).isEqualTo(target);
            var changedAt = quotationRepository.findById(id).orElseThrow().getUpdatedAt();
            var duplicate = switch (target) {
                case APPROVED -> statusService.approve(id, userId);
                case REJECTED -> statusService.reject(id, userId);
                default -> statusService.cancel(id, userId);
            };
            assertThat(duplicate.status()).isEqualTo(target);
            assertThat(quotationRepository.findById(id).orElseThrow().getUpdatedAt()).isEqualTo(changedAt);
            assertThat(quotationRepository.findById(id).orElseThrow().getPdfObjectKey()).isEqualTo(key);
            assertThat(files.get(key).content()).containsExactly(pdf);
            org.mockito.Mockito.verifyNoInteractions(storageClient, mailService);
        }
    }

    @Test
    void statusActionsRejectDraftApprovedAndOtherTerminalStates() {
        for (QuotationStatus source : QuotationStatus.values()) {
            if (source == QuotationStatus.SENT) {
                continue;
            }
            var draft = createStatusFixture(source, today().plusDays(1));
            if (source != QuotationStatus.APPROVED) {
                assertStatusConflict(() -> statusService.approve(draft, userId));
            }
            if (source != QuotationStatus.REJECTED) {
                assertStatusConflict(() -> statusService.reject(draft, userId));
            }
            if (source != QuotationStatus.CANCELLED) {
                assertStatusConflict(() -> statusService.cancel(draft, userId));
            }
            assertThat(quotationRepository.findById(draft).orElseThrow().getStatus()).isEqualTo(source);
        }
        assertThatThrownBy(() -> statusService.reject(Long.MAX_VALUE, userId))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> statusService.approve(Long.MAX_VALUE, userId))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void expiredSentCannotBeManuallyRejectedOrCancelledButLastValidDayIsAllowed() {
        Long expired = createStatusFixture(QuotationStatus.SENT, today().minusDays(1));
        assertStatusConflict(() -> statusService.approve(expired, userId));
        assertStatusConflict(() -> statusService.reject(expired, userId));
        assertStatusConflict(() -> statusService.cancel(expired, userId));
        assertThat(quotationRepository.findById(expired).orElseThrow().getStatus()).isEqualTo(QuotationStatus.SENT);
        Long valid = createStatusFixture(QuotationStatus.SENT, today());
        assertThat(statusService.reject(valid, userId).status()).isEqualTo(QuotationStatus.REJECTED);
        Long lastDay = createStatusFixture(QuotationStatus.SENT, today());
        assertThat(statusService.approve(lastDay, userId).status()).isEqualTo(QuotationStatus.APPROVED);
        Long alreadyApproved = createStatusFixture(QuotationStatus.APPROVED, today().minusDays(1));
        assertThat(statusService.approve(alreadyApproved, userId).status()).isEqualTo(QuotationStatus.APPROVED);
    }

    @Test
    void entityApprovalRejectsEveryNonSentState() {
        for (QuotationStatus source : QuotationStatus.values()) {
            var quotation = instantiate(Quotation.class);
            ReflectionTestUtils.setField(quotation, "status", source);
            if (source == QuotationStatus.SENT) {
                quotation.approve();
                assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.APPROVED);
            } else {
                assertThatThrownBy(quotation::approve).isInstanceOf(IllegalArgumentException.class);
                assertThat(quotation.getStatus()).isEqualTo(source);
            }
        }
    }

    @Test
    void approvalRequiresPasswordChangeAndAllowsSuperAdmin() {
        Long id = createStatusFixture(QuotationStatus.SENT, today());
        tx().executeWithoutResult(status -> ReflectionTestUtils.setField(
                em.find(User.class, userId), "mustChangePassword", true));
        assertThatThrownBy(() -> statusService.approve(id, userId)).isInstanceOf(AccessDeniedException.class);
        tx().executeWithoutResult(status -> {
            var user = em.find(User.class, userId);
            user.changePassword("changed");
            ReflectionTestUtils.setField(user, "role", UserRole.SUPER_ADMIN);
        });
        authenticateAs(UserRole.SUPER_ADMIN);
        assertThat(statusService.approve(id, userId).status()).isEqualTo(QuotationStatus.APPROVED);
    }

    @Test
    void expirationChangesOnlyPastSentAndRecordsSystemAuditWithoutTouchingPdf() {
        var before = new java.util.HashMap<Long, QuotationDto.DetailResponse>();
        Long expiredSent = null;
        for (QuotationStatus source : QuotationStatus.values()) {
            for (LocalDate date : List.of(today().minusDays(1), today(), today().plusDays(1))) {
                Long id = createStatusFixture(source, date);
                before.put(id, service.getQuotationById(id));
                if (source == QuotationStatus.SENT && date.isBefore(today())) {
                    expiredSent = id;
                }
            }
        }
        SecurityContextHolder.clearContext();
        assertThat(statusService.expireQuotations()).isEqualTo(1);
        assertThat(statusService.expireQuotations()).isZero();
        for (var entry : before.entrySet()) {
            var after = service.getQuotationById(entry.getKey());
            if (entry.getKey().equals(expiredSent)) {
                assertThat(after.status()).isEqualTo(QuotationStatus.EXPIRED);
                assertThat(quotationRepository.findById(entry.getKey()).orElseThrow().getUpdatedBy()).isEqualTo("SYSTEM");
            } else {
                assertThat(after.status()).isEqualTo(entry.getValue().status());
            }
            assertThat(after).usingRecursiveComparison().ignoringFields("status", "updatedAt")
                    .isEqualTo(entry.getValue());
            assertThat(quotationRepository.findById(entry.getKey()).orElseThrow().getPdfObjectKey())
                    .isEqualTo("quotations/status-test.pdf");
        }
        org.mockito.Mockito.verifyNoInteractions(storageClient, mailService);
    }

    @Test
    void manualExpirationReusesDateRulesAndRepeatedRequestReturnsZero() {
        Long pastSent = createStatusFixture(QuotationStatus.SENT, today().minusDays(1));
        Long todaySent = createStatusFixture(QuotationStatus.SENT, today());
        Long pastApproved = createStatusFixture(QuotationStatus.APPROVED, today().minusDays(1));
        assertThat(statusService.expireQuotations(userId).expiredCount()).isEqualTo(1);
        assertThat(statusService.expireQuotations(userId).expiredCount()).isZero();
        assertThat(quotationRepository.findById(pastSent).orElseThrow().getStatus()).isEqualTo(QuotationStatus.EXPIRED);
        assertThat(quotationRepository.findById(todaySent).orElseThrow().getStatus()).isEqualTo(QuotationStatus.SENT);
        assertThat(quotationRepository.findById(pastApproved).orElseThrow().getStatus()).isEqualTo(QuotationStatus.APPROVED);
        assertThat(quotationRepository.findById(pastSent).orElseThrow().getUpdatedBy()).isEqualTo("서도윤");
        org.mockito.Mockito.verifyNoInteractions(storageClient, mailService);
    }

    @Test
    void manualExpirationRequiresPermissionAndActiveDatabaseAccount() {
        Long pastSent = createStatusFixture(QuotationStatus.SENT, today().minusDays(1));
        authenticateAs(UserRole.INQUIRY_MANAGER);
        assertThatThrownBy(() -> statusService.expireQuotations(userId)).isInstanceOf(AccessDeniedException.class);
        authenticateAs(UserRole.ORDER_MANAGER);
        tx().executeWithoutResult(status -> em.find(User.class, userId).deactivate());
        assertThatThrownBy(() -> statusService.expireQuotations(userId)).isInstanceOf(AccessDeniedException.class);
        assertThat(quotationRepository.findById(pastSent).orElseThrow().getStatus()).isEqualTo(QuotationStatus.SENT);
    }

    @Test
    void statusActionsCheckMethodPermissionAndCurrentDatabaseAccount() {
        Long id = createStatusFixture(QuotationStatus.SENT, today());
        authenticateAs(UserRole.INQUIRY_MANAGER);
        assertThatThrownBy(() -> statusService.approve(id, userId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> statusService.reject(id, userId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> statusService.cancel(id, userId)).isInstanceOf(AccessDeniedException.class);
        authenticateAs(UserRole.ORDER_MANAGER);
        tx().executeWithoutResult(status -> ReflectionTestUtils.setField(
                em.find(User.class, userId), "role", UserRole.INQUIRY_MANAGER));
        assertThatThrownBy(() -> statusService.approve(id, userId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> statusService.reject(id, userId)).isInstanceOf(AccessDeniedException.class);
        tx().executeWithoutResult(status -> {
            var user = em.find(User.class, userId);
            ReflectionTestUtils.setField(user, "role", UserRole.ORDER_MANAGER);
            user.deactivate();
        });
        assertThatThrownBy(() -> statusService.cancel(id, userId)).isInstanceOf(AccessDeniedException.class);
        assertThat(quotationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(QuotationStatus.SENT);
        assertThatThrownBy(() -> statusService.approve(id, userId)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void concurrentRejectionAndCancellationAllowOnlyOneTransition() throws Exception {
        Long id = createStatusFixture(QuotationStatus.SENT, today());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<QuotationStatus>>();
            for (QuotationStatus target : List.of(QuotationStatus.REJECTED, QuotationStatus.CANCELLED)) {
                tasks.add(executor.submit(() -> {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    try {
                        if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                            throw new IllegalStateException("동시 요청 대기 시간 초과");
                        }
                        return target == QuotationStatus.REJECTED
                                ? statusService.reject(id, userId).status() : statusService.cancel(id, userId).status();
                    } catch (org.springframework.web.server.ResponseStatusException ex) {
                        assertThat(ex.getStatusCode().value()).isEqualTo(409);
                        return null;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            start.countDown();
            var first = tasks.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS);
            var second = tasks.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat((first == null) != (second == null)).isTrue();
            assertThat(quotationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(first == null ? second : first);
        }
    }
}
