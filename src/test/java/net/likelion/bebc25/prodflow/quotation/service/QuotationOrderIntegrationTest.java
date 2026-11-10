package net.likelion.bebc25.prodflow.quotation.service;

import jakarta.validation.ConstraintViolationException;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.payment.client.PortOneClient;
import net.likelion.bebc25.prodflow.payment.dto.PortOneDto;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.Bank;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@DisplayName("견적에서 수주 등록 연동")
@TestPropertySource(properties = {
        "portone.api-secret=test-only-api-secret",
        "portone.store-id=test-store-id",
        "portone.channel-key=test-channel-key",
        "portone.webhook-url=https://example.org/api/v1/payments/webhook",
        "payment.virtual-account-bank=SHINHAN"
})
class QuotationOrderIntegrationTest extends QuotationIntegrationTestSupport {

    @MockitoBean
    private PortOneClient portOneClient;

    // 동시 테스트에서도 안전하도록 발급 요청을 저장
    private final Map<String, PortOneDto.IssueRequest> issuedRequests =
            new ConcurrentHashMap<>();

    @BeforeEach
    void setUpPortOneMock() {
        issuedRequests.clear();

        // 실제 PortOne POST 호출 대신 발급 요청 저장
        doAnswer(invocation -> {
            PortOneDto.IssueRequest request = invocation.getArgument(0);
            issuedRequests.put(request.paymentId(), request);
            return null;
        }).when(portOneClient).issueInstantPayment(
                any(PortOneDto.IssueRequest.class)
        );

        // 실제 PortOne GET 호출 대신 가짜 발급 결과 반환
        when(portOneClient.getPayment(anyString()))
                .thenAnswer(invocation -> {
                    String paymentId = invocation.getArgument(0);
                    PortOneDto.IssueRequest request =
                            issuedRequests.get(paymentId);

                    if (request == null) {
                        throw new IllegalStateException(
                                "테스트에서 발급 요청하지 않은 결제입니다: " + paymentId
                        );
                    }

                    return new PortOneDto.PaymentSnapshot(
                            paymentId,
                            "test-pg-" + paymentId,
                            "VIRTUAL_ACCOUNT_ISSUED",
                            "test-store-id",
                            null,
                            null,
                            "KRW",
                            new PortOneDto.PaymentSnapshot.Amount(
                                    request.amount(), null
                            ),
                            new PortOneDto.PaymentSnapshot.Method(
                                    "PaymentMethodVirtualAccount",
                                    request.bank(),
                                    "999" + Integer.toUnsignedString(
                                            paymentId.hashCode()
                                    ),
                                    "테스트 가상계좌",
                                    request.dueDate()
                            ),
                            new PortOneDto.PaymentSnapshot.Channel(
                                    "test-channel-key",
                                    "TOSSPAYMENTS"
                            ),
                            OffsetDateTime.now(),
                            null,
                            null,
                            null
                    );
                });
    }

    @Test
    @DisplayName("수주 등록 시 계약금 결제건과 가상계좌가 생성된다")
    void registersOrderWithQuotedSnapshotsAndDepositPayment() {
        var quote = approvedForOrder();
        var before = service.getQuotationById(quote.id());

        var response = orderService.create(
                orderRequest(quote, 100, dueDate(), true, null),
                userId
        );

        assertThat(response.orderNo()).isEqualTo(
                "PF-" + today().format(DateTimeFormatter.BASIC_ISO_DATE)
                        + "-" + response.id()
        );

        assertThat(response.totalAmount()).isEqualTo(110000L);
        assertThat(response.depositDueDate())
                .isEqualTo(today().plusDays(3));
        assertThat(response.plannedStartDate())
                .isAfterOrEqualTo(response.depositDueDate());

        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.confirmationStatus().name())
                .isEqualTo("PENDING");
        assertThat(response.paymentStatus().name())
                .isEqualTo("UNPAID");

        tx().executeWithoutResult(status -> {
            var order = em.find(CustomerOrder.class, response.id());

            assertThat(order.getShippingAddress())
                    .isEqualTo("(12345) 서울시 테스트로 1 2층");
            assertThat(order.getRequestedDueDate())
                    .isEqualTo(before.requestedDueDate());
            assertThat(order.getContactName())
                    .isEqualTo(before.clientName());
            assertThat(order.getEmail())
                    .isEqualTo(before.clientEmail());

            var products = em.createQuery(
                    "select p from OrderProduct p " +
                            "where p.customerOrder.id = :id",
                    net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct.class
            ).setParameter("id", response.id()).getResultList();

            assertThat(products).singleElement().satisfies(p -> {
                assertThat(p.getQuantity()).isEqualTo(100);
                assertThat(p.getUnitPrice()).isEqualTo(1000L);
            });

            // 추가: 계약금 결제건이 생성되었는지 확인
            var payments = em.createQuery(
                            "select p from Payment p " +
                                    "where p.order.id = :orderId",
                            Payment.class
                    )
                    .setParameter("orderId", response.id())
                    .getResultList();

            assertThat(payments).singleElement().satisfies(payment -> {
                assertThat(payment.getPaymentType())
                        .isEqualTo(PaymentType.DEPOSIT);
                assertThat(payment.getAmount()).isEqualTo(55000L);
                assertThat(payment.getStatus())
                        .isEqualTo(PaymentStatus.WAITING);
            });

            // 추가: 가상계좌 정보가 저장되었는지 확인
            var accounts = em.createQuery(
                            "select v from VirtualAccount v " +
                                    "where v.payment.order.id = :orderId",
                            VirtualAccount.class
                    )
                    .setParameter("orderId", response.id())
                    .getResultList();

            assertThat(accounts).singleElement().satisfies(account -> {
                assertThat(account.getBank())
                        .isEqualTo(Bank.SHINHAN);
                assertThat(account.getAccountNumber()).isNotBlank();
            });

            // 입금 전이므로 작업지시와 생산계획은 없어야 함
            for (String entity : List.of(
                    "WorkOrder", "ProductionPlan"
            )) {
                assertThat(
                        em.createQuery(
                                "select count(e) from " + entity + " e",
                                Long.class
                        ).getSingleResult()
                ).isZero();
            }
        });

        assertThat(service.getQuotationById(quote.id()))
                .usingRecursiveComparison()
                .ignoringFields("customerOrderId", "orderNo")
                .isEqualTo(before);

        var pending = service.getQuotationList(
                null,
                null,
                net.likelion.bebc25.prodflow.quotation.enums.QuotationSortType.CREATED_AT,
                org.springframework.data.domain.PageRequest.of(0, 12),
                true
        );

        assertThat(pending.getContent()).isEmpty();
    }

    @Test
    void orderUsesDirectShippingAndChangedDueDateWithoutConsentFields() {
        var quote = approvedForOrder();

        var address =
                new net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.ShippingAddress(
                        "54321", "부산 새주소로 2", "3층"
                );

        var response = orderService.create(
                orderRequest(
                        quote,
                        200,
                        dueDate().plusDays(7),
                        false,
                        address
                ),
                userId
        );

        assertThat(response.dueDate())
                .isEqualTo(dueDate().plusDays(7));

        tx().executeWithoutResult(status -> {
            assertThat(
                    em.find(CustomerOrder.class, response.id())
                            .getShippingAddress()
            ).isEqualTo("(54321) 부산 새주소로 2 3층");

            assertThat(
                    em.find(Client.class, clientId)
                            .getAddress()
                            .getRoadAddress()
            ).isEqualTo("서울시 테스트로 1");
        });
    }

    @Test
    void orderRejectsInvalidQuantitiesAndMissingOrDuplicateItems() {
        var quote = approvedForOrder();

        for (int quantity : List.of(99, 201)) {
            assertThatThrownBy(() ->
                    orderService.create(
                            orderRequest(
                                    quote, quantity, dueDate(), true, null
                            ),
                            userId
                    )
            ).isInstanceOfAny(
                    ConstraintViolationException.class,
                    IllegalArgumentException.class
            );
        }

        var valid = orderRequest(
                quote, 100, dueDate(), true, null
        );

        var foreign =
                new net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.ItemRequest(
                        Long.MAX_VALUE, 100
                );

        for (var items : List.of(
                List.<net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.ItemRequest>of(),
                List.of(foreign),
                List.of(
                        valid.items().getFirst(),
                        valid.items().getFirst()
                )
        )) {
            var invalid =
                    new net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.CreateRequest(
                            quote.id(),
                            items,
                            dueDate(),
                            true,
                            null,
                            "수신자",
                            "010-1234-5678",
                            net.likelion.bebc25.prodflow.customer_order.enums.NotifyChannel.EMAIL,
                            null
                    );

            assertThatThrownBy(() ->
                    orderService.create(invalid, userId)
            ).isInstanceOfAny(
                    ConstraintViolationException.class,
                    IllegalArgumentException.class
            );
        }

        Long orderCount = tx().execute(status ->
                em.createQuery(
                        "select count(o) from CustomerOrder o",
                        Long.class
                ).getSingleResult()
        );

        assertThat(orderCount).isZero();
    }

    @Test
    void orderRejectsNonApprovedDuplicateAndImpossibleSchedule() {
        var quote = approvedForOrder();

        assertThatThrownBy(() ->
                orderService.create(
                        orderRequest(
                                quote, 100, today(), true, null
                        ),
                        userId
                )
        ).isInstanceOf(IllegalArgumentException.class);

        tx().executeWithoutResult(status ->
                ReflectionTestUtils.setField(
                        em.find(Quotation.class, quote.id()),
                        "status",
                        QuotationStatus.SENT
                )
        );

        assertThatThrownBy(() ->
                orderService.create(
                        orderRequest(
                                quote, 100, dueDate(), true, null
                        ),
                        userId
                )
        )
                .isInstanceOf(
                        org.springframework.web.server.ResponseStatusException.class
                )
                .hasMessageContaining("승인");

        tx().executeWithoutResult(status ->
                em.find(Quotation.class, quote.id()).approve()
        );

        var response = orderService.create(
                orderRequest(
                        quote, 100, dueDate(), true, null
                ),
                userId
        );

        assertThatThrownBy(() ->
                orderService.create(
                        orderRequest(
                                quote, 100, dueDate(), true, null
                        ),
                        userId
                )
        )
                .isInstanceOf(
                        org.springframework.web.server.ResponseStatusException.class
                )
                .hasMessageContaining(response.orderNo());
    }

    @Test
    void orderRejectsMissingShippingAndInvalidAccount() {
        var quote = approvedForOrder();

        assertThatThrownBy(() ->
                orderService.create(
                        orderRequest(
                                quote, 100, dueDate(), false, null
                        ),
                        userId
                )
        ).isInstanceOf(ConstraintViolationException.class);

        tx().executeWithoutResult(status ->
                ReflectionTestUtils.setField(
                        em.find(Client.class, clientId),
                        "address",
                        new Address("", "", "")
                )
        );

        assertThatThrownBy(() ->
                orderService.create(
                        orderRequest(
                                quote, 100, dueDate(), true, null
                        ),
                        userId
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("회사 주소");

        tx().executeWithoutResult(status ->
                ReflectionTestUtils.setField(
                        em.find(User.class, userId),
                        "mustChangePassword",
                        true
                )
        );

        assertThatThrownBy(() ->
                orderService.create(
                        orderRequest(
                                quote, 100, dueDate(), true, null
                        ),
                        userId
                )
        ).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void concurrentOrderRequestsDoNotRegisterTheSameQuotationTwice()
            throws Exception {

        var quote = approvedForOrder();

        var request = orderRequest(
                quote, 100, dueDate(), true, null
        );

        var outcomes = registerConcurrently(request, request);

        assertThat(
                outcomes.stream().filter(
                        net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.CreateResponse.class::isInstance
                )
        ).hasSize(1);

        assertThat(
                outcomes.stream().filter(
                        org.springframework.web.server.ResponseStatusException.class::isInstance
                )
        ).hasSize(1);
    }

    @Test
    void concurrentDifferentQuotationsCannotReserveTheSameProductionCapacity()
            throws Exception {

        var first = approvedForOrder();
        var second = approvedForOrder();

        tx().executeWithoutResult(status ->
                em.createQuery(
                        "update Line l set l.productionCapacity = 200"
                ).executeUpdate()
        );

        LocalDate earliest = orderScheduleService
                .previewSchedule(200, null, today())
                .earliestSchedule()
                .estimatedDueDate();

        var outcomes = registerConcurrently(
                orderRequest(first, 200, earliest, true, null),
                orderRequest(second, 200, earliest, true, null)
        );

        assertThat(
                outcomes.stream().filter(
                        net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.CreateResponse.class::isInstance
                )
        ).hasSize(1);

        assertThat(
                outcomes.stream().filter(
                        IllegalArgumentException.class::isInstance
                )
        ).hasSize(1);
    }
}
