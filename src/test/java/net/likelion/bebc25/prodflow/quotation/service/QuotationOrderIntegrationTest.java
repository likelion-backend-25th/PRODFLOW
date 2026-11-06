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

@DisplayName("견적에서 수주 등록 연동")
class QuotationOrderIntegrationTest extends QuotationIntegrationTestSupport {

    @Test
    void registersOrderWithQuotedSnapshotsAndCompanyShippingWithoutPayments() {
        var quote = approvedForOrder();
        var before = service.getQuotationById(quote.id());
        var response = orderService.create(orderRequest(quote, 100, dueDate(), true, null), userId);
        assertThat(response.orderNo()).isEqualTo("PF-" + today().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + response.id());
        assertThat(response.totalAmount()).isEqualTo(110000L);
        assertThat(response.depositDueDate()).isEqualTo(today().plusDays(3));
        assertThat(response.plannedStartDate()).isAfterOrEqualTo(response.depositDueDate());
        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.confirmationStatus().name()).isEqualTo("PENDING");
        assertThat(response.paymentStatus().name()).isEqualTo("UNPAID");
        tx().executeWithoutResult(status -> {
            var order = em.find(CustomerOrder.class, response.id());
            assertThat(order.getShippingAddress()).isEqualTo("(12345) 서울시 테스트로 1 2층");
            assertThat(order.getRequestedDueDate()).isEqualTo(before.requestedDueDate());
            assertThat(order.getContactName()).isEqualTo(before.clientName());
            assertThat(order.getEmail()).isEqualTo(before.clientEmail());
            var products = em.createQuery("select p from OrderProduct p where p.customerOrder.id = :id",
                    net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct.class)
                    .setParameter("id", response.id()).getResultList();
            assertThat(products).singleElement().satisfies(p -> {
                assertThat(p.getQuantity()).isEqualTo(100);
                assertThat(p.getUnitPrice()).isEqualTo(1000L);
            });
            for (String entity : List.of("Payment", "VirtualAccount", "WorkOrder", "ProductionPlan")) {
                assertThat(em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult()).isZero();
            }
        });
        assertThat(service.getQuotationById(quote.id())).usingRecursiveComparison()
                .ignoringFields("customerOrderId", "orderNo").isEqualTo(before);
        var pending = service.getQuotationList(null, null,
                net.likelion.bebc25.prodflow.quotation.enums.QuotationSortType.CREATED_AT,
                org.springframework.data.domain.PageRequest.of(0, 12), true);
        assertThat(pending.getContent()).isEmpty();
    }

    @Test
    void orderUsesDirectShippingAndChangedDueDateWithoutConsentFields() {
        var quote = approvedForOrder();
        var address = new net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.ShippingAddress(
                "54321", "부산 새주소로 2", "3층");
        var response = orderService.create(orderRequest(quote, 200, dueDate().plusDays(7), false, address), userId);
        assertThat(response.dueDate()).isEqualTo(dueDate().plusDays(7));
        tx().executeWithoutResult(status -> {
            assertThat(em.find(CustomerOrder.class, response.id()).getShippingAddress()).isEqualTo("(54321) 부산 새주소로 2 3층");
            assertThat(em.find(Client.class, clientId).getAddress().getRoadAddress()).isEqualTo("서울시 테스트로 1");
        });
    }

    @Test
    void orderRejectsInvalidQuantitiesAndMissingOrDuplicateItems() {
        var quote = approvedForOrder();
        for (int quantity : List.of(99, 201)) {
            assertThatThrownBy(() -> orderService.create(orderRequest(quote, quantity, dueDate(), true, null), userId))
                    .isInstanceOfAny(ConstraintViolationException.class, IllegalArgumentException.class);
        }
        var valid = orderRequest(quote, 100, dueDate(), true, null);
        var foreign = new net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.ItemRequest(Long.MAX_VALUE, 100);
        for (var items : List.of(List.<net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.ItemRequest>of(),
                List.of(foreign), List.of(valid.items().getFirst(), valid.items().getFirst()))) {
            var invalid = new net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.CreateRequest(
                    quote.id(), items, dueDate(), true, null, "수신자", "010-1234-5678",
                    net.likelion.bebc25.prodflow.customer_order.enums.NotifyChannel.EMAIL, null);
            assertThatThrownBy(() -> orderService.create(invalid, userId))
                    .isInstanceOfAny(ConstraintViolationException.class, IllegalArgumentException.class);
        }
        Long orderCount = tx().execute(status -> em.createQuery("select count(o) from CustomerOrder o", Long.class).getSingleResult());
        assertThat(orderCount).isZero();
    }

    @Test
    void orderRejectsNonApprovedDuplicateAndImpossibleSchedule() {
        var quote = approvedForOrder();
        assertThatThrownBy(() -> orderService.create(orderRequest(quote, 100, today(), true, null), userId))
                .isInstanceOf(IllegalArgumentException.class);
        tx().executeWithoutResult(status -> ReflectionTestUtils.setField(em.find(Quotation.class, quote.id()), "status", QuotationStatus.SENT));
        assertThatThrownBy(() -> orderService.create(orderRequest(quote, 100, dueDate(), true, null), userId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("승인");
        tx().executeWithoutResult(status -> em.find(Quotation.class, quote.id()).approve());
        var response = orderService.create(orderRequest(quote, 100, dueDate(), true, null), userId);
        assertThatThrownBy(() -> orderService.create(orderRequest(quote, 100, dueDate(), true, null), userId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining(response.orderNo());
    }

    @Test
    void orderRejectsMissingShippingAndInvalidAccount() {
        var quote = approvedForOrder();
        assertThatThrownBy(() -> orderService.create(orderRequest(quote, 100, dueDate(), false, null), userId))
                .isInstanceOf(ConstraintViolationException.class);
        tx().executeWithoutResult(status -> ReflectionTestUtils.setField(em.find(Client.class, clientId), "address", new Address("", "", "")));
        assertThatThrownBy(() -> orderService.create(orderRequest(quote, 100, dueDate(), true, null), userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("회사 주소");
        tx().executeWithoutResult(status -> ReflectionTestUtils.setField(em.find(User.class, userId), "mustChangePassword", true));
        assertThatThrownBy(() -> orderService.create(orderRequest(quote, 100, dueDate(), true, null), userId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void concurrentOrderRequestsDoNotRegisterTheSameQuotationTwice() throws Exception {
        var quote = approvedForOrder();
        var request = orderRequest(quote, 100, dueDate(), true, null);
        var outcomes = registerConcurrently(request, request);
        assertThat(outcomes.stream().filter(net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.CreateResponse.class::isInstance))
                .hasSize(1);
        assertThat(outcomes.stream().filter(org.springframework.web.server.ResponseStatusException.class::isInstance))
                .hasSize(1);
    }

    @Test
    void concurrentDifferentQuotationsCannotReserveTheSameProductionCapacity() throws Exception {
        var first = approvedForOrder();
        var second = approvedForOrder();
        tx().executeWithoutResult(status -> em.createQuery("update Line l set l.productionCapacity = 200").executeUpdate());
        LocalDate earliest = orderScheduleService.previewSchedule(200, null, today()).earliestSchedule().estimatedDueDate();
        var outcomes = registerConcurrently(orderRequest(first, 200, earliest, true, null),
                orderRequest(second, 200, earliest, true, null));
        assertThat(outcomes.stream().filter(net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.CreateResponse.class::isInstance))
                .hasSize(1);
        assertThat(outcomes.stream().filter(IllegalArgumentException.class::isInstance)).hasSize(1);
    }
}
