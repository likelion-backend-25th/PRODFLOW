package net.likelion.bebc25.prodflow.quotation.service;

import jakarta.validation.ConstraintViolationException;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.client_notification.entity.PaymentNotificationRequest;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.commoncode.entity.CodeGroup;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDetailDto;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.enums.NotifyChannel;
import net.likelion.bebc25.prodflow.customer_order.service.CustomerOrderCancelService;
import net.likelion.bebc25.prodflow.payment.client.PortOneClient;
import net.likelion.bebc25.prodflow.payment.config.PortOneProperties;
import net.likelion.bebc25.prodflow.payment.dto.PaymentNotificationDto;
import net.likelion.bebc25.prodflow.payment.dto.PortOneDto;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.Bank;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import net.likelion.bebc25.prodflow.payment.service.PaymentNotificationService;
import net.likelion.bebc25.prodflow.payment.service.PaymentStore;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationSortType;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("견적에서 수주 등록 연동")
class QuotationOrderIntegrationTest extends QuotationIntegrationTestSupport {

    @Autowired
    private PaymentNotificationService paymentNotificationService;

    @Autowired
    private CustomerOrderCancelService cancelService;

    @Autowired
    private PaymentStore paymentStore;

    @Autowired
    private PortOneProperties portOneProperties;

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
                            portOneProperties.storeId(),
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
                                    portOneProperties.channelKey(),
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
    @DisplayName("수주 등록은 수주와 품목만 저장하고 계약금 결제건과 가상계좌를 생성하지 않는다")
    void registersOrderWithQuotedSnapshotsAndDepositPayment() {
        QuotationDto.DetailResponse quote = approvedForOrder();
        QuotationDto.DetailResponse before = service.getQuotationById(quote.id());

        CustomerOrderDto.CreateResponse response = orderService.create(
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
            CustomerOrder order = em.find(CustomerOrder.class, response.id());

            assertThat(order.getShippingAddress())
                    .isEqualTo("(12345) 서울시 테스트로 1 2층");
            assertThat(order.getRequestedDueDate())
                    .isEqualTo(before.requestedDueDate());
            assertThat(order.getContactName())
                    .isEqualTo(before.clientName());
            assertThat(order.getEmail())
                    .isEqualTo(before.clientEmail());

            List<OrderProduct> products = em.createQuery(
                    "select p from OrderProduct p " +
                            "where p.customerOrder.id = :id",
                    OrderProduct.class
            ).setParameter("id", response.id()).getResultList();

            assertThat(products).singleElement().satisfies(p -> {
                assertThat(p.getQuantity()).isEqualTo(100);
                assertThat(p.getUnitPrice()).isEqualTo(1000L);
            });

            // 추가: 계약금 결제건이 생성되었는지 확인
            List<Payment> payments = em.createQuery(
                            "select p from Payment p " +
                                    "where p.order.id = :orderId",
                            Payment.class
                    )
                    .setParameter("orderId", response.id())
                    .getResultList();

            assertThat(payments).isEmpty();

            // 추가: 가상계좌 정보가 저장되었는지 확인
            List<VirtualAccount> accounts = em.createQuery(
                            "select v from VirtualAccount v " +
                                    "where v.payment.order.id = :orderId",
                            VirtualAccount.class
                    )
                    .setParameter("orderId", response.id())
                    .getResultList();

            assertThat(accounts).isEmpty();

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

        PageResponse<QuotationDto.Response> pending = service.getQuotationList(
                null,
                null,
                QuotationSortType.CREATED_AT,
                PageRequest.of(0, 12),
                true
        );

        assertThat(pending.getContent()).isEmpty();
        assertThat(issuedRequests).isEmpty();
        assertThat(orderService.getHistories(response.id())).singleElement().satisfies(history -> {
            assertThat(history.fromStatus()).isEqualTo("NONE");
            assertThat(history.toStatus()).isEqualTo("WAITING");
            assertThat(history.userId()).isEqualTo(userId);
            assertThat(history.createdAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("수주 등록이 롤백되면 수주·품목·등록 이력도 함께 롤백된다")
    void rollsBackInitialHistoryWithOrder() {
        QuotationDto.DetailResponse quote = approvedForOrder();
        Long[] orderId = new Long[1];
        tx().executeWithoutResult(status -> {
            CustomerOrderDto.CreateResponse response = orderService.create(orderRequest(quote, 100, dueDate(), true,
                    null), userId);
            orderId[0] = response.id();
            em.flush();
            assertThat(orderService.getHistories(response.id())).hasSize(1);
            status.setRollbackOnly();
        });

        tx().executeWithoutResult(status -> {
            assertThat(em.find(CustomerOrder.class, orderId[0])).isNull();
            assertThat(em.createQuery("select count(p) from OrderProduct p where p.customerOrder.id = :id", Long.class)
                    .setParameter("id", orderId[0]).getSingleResult()).isZero();
            assertThat(em.createQuery("select count(h) from StatusHistory h "
                            + "where h.entity = :entity and h.entityId = :id", Long.class)
                    .setParameter("entity", HistoryEntityType.ORDER)
                    .setParameter("id", orderId[0]).getSingleResult()).isZero();
        });
    }

    @Test
    @DisplayName("수주 취소는 원본 견적과 상태 이력을 함께 반영하고 재요청으로 중복 기록하지 않는다")
    void cancelsWaitingOrderAndQuotationOnce() {
        QuotationDto.DetailResponse quote = approvedForOrder();
        prepareCancellationStatus();
        CustomerOrderDto.CreateResponse order = orderService.create(orderRequest(quote, 100, dueDate(), true, null),
                userId);

        CustomerOrderDetailDto.Response response = cancelService.cancel(order.id(), new CustomerOrderDto
                .CancelRequest("  거래처 요청  "), userId);

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.confirmationStatus().name()).isEqualTo("CANCELLED");
        assertThat(service.getQuotationById(quote.id()).status()).isEqualTo(QuotationStatus.CANCELLED);
        assertThat(orderService.getHistories(order.id())).hasSize(2).first().satisfies(history -> {
            assertThat(history.fromStatus()).isEqualTo("WAITING");
            assertThat(history.toStatus()).isEqualTo("CANCELLED");
            assertThat(history.userId()).isEqualTo(userId);
            assertThat(history.note()).isEqualTo("거래처 요청");
        });
        cancelService.cancel(order.id(), new CustomerOrderDto.CancelRequest("재요청"), userId);
        assertThat(orderService.getHistories(order.id())).hasSize(2);
        assertThat(issuedRequests).isEmpty();
        verifyNoInteractions(portOneClient);
    }

    @Test
    @DisplayName("가상계좌 발급 전 READY 결제는 수주와 함께 취소한다")
    void cancelsReadyPaymentWithOrder() {
        CustomerOrderDto.CreateResponse order = orderService.create(orderRequest(approvedForOrder(), 100, dueDate(),
                true, null), userId);
        prepareCancellationStatus();
        Long paymentId = tx().execute(status -> {
            Payment payment = Payment.builder().order(em.find(CustomerOrder.class, order.id()))
                    .paymentType(PaymentType.DEPOSIT).amount(55000L)
                    .portonePaymentId("PF" + UUID.randomUUID().toString().replace("-", "")).build();
            em.persist(payment);
            return payment.getId();
        });

        cancelService.cancel(order.id(), new CustomerOrderDto.CancelRequest("거래처 요청"), userId);

        tx().executeWithoutResult(status ->
                assertThat(em.find(Payment.class, paymentId).getStatus()).isEqualTo(PaymentStatus.CANCELLED));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"REQUESTED", "ISSUE_UNCERTAIN", "WAITING", "PAID", "FAILED"})
    @DisplayName("발급 시작·결과 불명확·입금 상태의 결제가 있으면 수주 취소를 거부한다")
    void rejectsCancellationAfterExternalIssueStarts(PaymentStatus paymentStatus) {
        QuotationDto.DetailResponse quote = approvedForOrder();
        CustomerOrderDto.CreateResponse order = orderService.create(orderRequest(quote, 100, dueDate(), true, null),
                userId);
        tx().executeWithoutResult(status -> {
            Payment payment = Payment.builder().order(em.find(CustomerOrder.class, order.id()))
                    .paymentType(PaymentType.DEPOSIT).amount(55000L)
                    .portonePaymentId("PF" + UUID.randomUUID().toString().replace("-", "")).build();
            payment.updateStatus(paymentStatus);
            em.persist(payment);
        });

        assertThatThrownBy(() -> cancelService.cancel(order.id(),
                new CustomerOrderDto.CancelRequest("거래처 요청"), userId))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(orderService.getOrder(order.id()).status()).isEqualTo("WAITING");
        assertThat(service.getQuotationById(quote.id()).status()).isEqualTo(QuotationStatus.APPROVED);
        assertThat(orderService.getHistories(order.id())).hasSize(1);
    }

    @Test
    @DisplayName("READY 결제에 가상계좌 이력이 남은 불일치 상태는 자동 취소를 거부한다")
    void rejectsCancellationWhenAccountExists() {
        QuotationDto.DetailResponse quote = approvedForOrder();
        CustomerOrderDto.CreateResponse order = orderService.create(
                orderRequest(quote, 100, dueDate(), true, null), userId);
        tx().executeWithoutResult(status -> {
            Payment payment = Payment.builder().order(em.find(CustomerOrder.class, order.id()))
                    .paymentType(PaymentType.DEPOSIT).amount(55000L)
                    .portonePaymentId("PF-CANCEL-ACCOUNT").build();
            em.persist(payment);
            em.persist(VirtualAccount.builder().payment(payment).bank(Bank.SHINHAN)
                    .accountNumber("CANCEL-TEST-ACCOUNT").accountName("테스트")
                    .expiresAt(OffsetDateTime.now().plusDays(1)).build());
        });

        assertThatThrownBy(() -> cancelService.cancel(order.id(),
                new CustomerOrderDto.CancelRequest("거래처 요청"), userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("현재 결제 상태에서는 자동 취소할 수 없습니다.");

        assertThat(orderService.getOrder(order.id()).status()).isEqualTo("WAITING");
        assertThat(service.getQuotationById(quote.id()).status()).isEqualTo(QuotationStatus.APPROVED);
        assertThat(orderService.getHistories(order.id())).hasSize(1);
        verifyNoInteractions(portOneClient);
    }

    @Test
    @DisplayName("수주 상태가 미입금이어도 실제 입금 내역이 있으면 취소하지 않는다")
    void rejectsCancellationWhenPaymentHistoryExists() {
        CustomerOrderDto.CreateResponse order = orderService.create(orderRequest(approvedForOrder(), 100, dueDate(),
                true, null), userId);
        tx().executeWithoutResult(status -> {
            Payment payment = Payment.builder().order(em.find(CustomerOrder.class, order.id()))
                    .paymentType(PaymentType.DEPOSIT).amount(55000L).portonePaymentId("PF-CANCEL-HISTORY").build();
            em.persist(payment);
            em.persist(PaymentHistory.builder().payment(payment).paidAt(OffsetDateTime.now()).build());
        });
        assertThatThrownBy(() -> cancelService.cancel(order.id(),
                new CustomerOrderDto.CancelRequest("거래처 요청"), userId)).hasMessageContaining("입금 내역");
        assertThat(orderService.getOrder(order.id()).status()).isEqualTo("WAITING");
    }

    @Test
    @DisplayName("독립 트랜잭션에서 완료된 수주 취소는 호출자 트랜잭션 롤백으로 되돌아가지 않는다")
    void outerRollbackDoesNotUndoCommittedCancellation() {
        QuotationDto.DetailResponse quote = approvedForOrder();
        prepareCancellationStatus();
        CustomerOrderDto.CreateResponse order = orderService.create(
                orderRequest(quote, 100, dueDate(), true, null), userId);

        tx().executeWithoutResult(status -> {
            CustomerOrderDetailDto.Response response = cancelService.cancel(
                    order.id(), new CustomerOrderDto.CancelRequest("거래처 요청"), userId);
            assertThat(response.status()).isEqualTo("CANCELLED");
            status.setRollbackOnly(); // 바깥쪽 트랜잭션만 롤백
        });

        assertThat(orderService.getOrder(order.id()).status()).isEqualTo("CANCELLED");
        assertThat(service.getQuotationById(quote.id()).status()).isEqualTo(QuotationStatus.CANCELLED);
        assertThat(orderService.getHistories(order.id())).hasSize(2).first().satisfies(history -> {
            assertThat(history.fromStatus()).isEqualTo("WAITING");
            assertThat(history.toStatus()).isEqualTo("CANCELLED");
            assertThat(history.note()).isEqualTo("거래처 요청");
        });
        verifyNoInteractions(portOneClient);
    }

    @Test
    @DisplayName("없는 수주의 취소는 404로 거부한다")
    void rejectsCancellationOfMissingOrder() {
        assertThatThrownBy(() -> cancelService.cancel(Long.MAX_VALUE,
                new CustomerOrderDto.CancelRequest("거래처 요청"), userId))
                .isInstanceOf(NoSuchElementException.class);
    }

    private void prepareCancellationStatus() {
        tx().executeWithoutResult(status -> {
            CodeGroup group = em.createQuery("select g from CodeGroup g where g.groupCode = 'ORDER_STATUS'",
                    CodeGroup.class).getSingleResult();
            CommonCode cancelled = instantiate(CommonCode.class);
            ReflectionTestUtils.setField(cancelled, "codeGroup", group);
            ReflectionTestUtils.setField(cancelled, "code", "CANCELLED");
            ReflectionTestUtils.setField(cancelled, "name", "취소");
            ReflectionTestUtils.setField(cancelled, "sortOrder", 99);
            ReflectionTestUtils.setField(cancelled, "status",
                    CodeStatus.ACTIVE);
            em.persist(cancelled);
        });
    }

    @Test
    @DisplayName("동시 취소 요청은 한 번만 상태 이력을 기록한다")
    void concurrentCancellationsWriteOneHistory() throws Exception {
        CustomerOrderDto.CreateResponse order = orderService.create(orderRequest(approvedForOrder(), 100, dueDate(),
                true, null), userId);
        prepareCancellationStatus();
        CustomerOrderDto.CancelRequest request = new CustomerOrderDto.CancelRequest("거래처 요청");

        List<Object> outcomes = runCancellationRace(() -> cancelService.cancel(order.id(), request, userId),
                () -> cancelService.cancel(order.id(), request, userId));

        assertThat(outcomes).allSatisfy(outcome ->
                assertThat(outcome).isInstanceOf(
                        CustomerOrderDetailDto.Response.class));
        assertThat(orderService.getHistories(order.id())).hasSize(2);
        assertThat(orderService.getOrder(order.id()).status()).isEqualTo("CANCELLED");
    }

    @RepeatedTest(5)
    @DisplayName("수주 취소와 외부 발급 시작이 경합하면 취소 또는 발급 시작 한쪽만 반영한다")
    void cancellationAndIssueClaimCannotBothWin() throws Exception {
        CustomerOrderDto.CreateResponse order = orderService.create(orderRequest(approvedForOrder(), 100, dueDate(),
                true, null), userId);
        prepareCancellationStatus();
        Long paymentId = tx().execute(status -> {
            Payment payment = Payment.builder().order(em.find(CustomerOrder.class, order.id()))
                    .paymentType(PaymentType.DEPOSIT).amount(55000L).portonePaymentId("PF-CANCEL-RACE").build();
            em.persist(payment);
            return payment.getId();
        });

        List<Object> outcomes = runCancellationRace(
                () -> cancelService.cancel(order.id(), new CustomerOrderDto.CancelRequest("거래처 요청"), userId),
                () -> paymentStore.claimIssue(paymentId));

        tx().executeWithoutResult(status -> {
            Payment payment = em.find(Payment.class, paymentId);
            if ("CANCELLED".equals(orderService.getOrder(order.id()).status())) {
                assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
                assertThat(outcomes.get(1)).isEqualTo(false);
            } else {
                assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
                assertThat(outcomes.get(1)).isEqualTo(true);
                assertThat(outcomes.get(0)).isInstanceOf(
                        ResponseStatusException.class);
            }
        });
        verifyNoInteractions(portOneClient);
    }

    @Test
    @DisplayName("수주 관리 권한이 없으면 취소하지 않는다")
    void rejectsUnauthorizedCancellation() {
        CustomerOrderDto.CreateResponse order = orderService.create(orderRequest(approvedForOrder(), 100, dueDate(),
                true, null), userId);
        authenticateAs(UserRole.INQUIRY_MANAGER);
        assertThatThrownBy(() -> cancelService.cancel(order.id(),
                new CustomerOrderDto.CancelRequest("거래처 요청"), userId)).isInstanceOf(AccessDeniedException.class);
        assertThat(orderService.getOrder(order.id()).status()).isEqualTo("WAITING");
    }

    private List<Object> runCancellationRace(
            Callable<?> first, Callable<?> second
    ) throws Exception {
        Authentication authentication = SecurityContextHolder
                .getContext().getAuthentication();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                    try {
                        start.await();
                        return task.call();
                    } catch (Exception ex) {
                        return ex;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            start.countDown();
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS),
                    futures.get(1).get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("별도 발송 요청은 커밋 후 기존 계좌 발급 기능과 이메일 전송을 연결한다")
    void sendsDepositOnlyAfterExplicitNotificationRequest() {
        QuotationDto.DetailResponse quote = approvedForOrder();
        CustomerOrderDto.CreateResponse order = orderService.create(orderRequest(quote, 100, dueDate(), true, null),
                userId);
        CustomerOrderDetailDto.Response detail = orderService.getOrder(order.id());
        List<PaymentNotificationDto.Item> items = detail.items().stream().map(item ->
                        new PaymentNotificationDto.Item(
                                item.id(), item.productName(), item.quantity(), item.unitPrice(), item.supplyAmount()))
                .toList();
        PaymentNotificationDto.Request request = new PaymentNotificationDto.Request(
                UUID.randomUUID().toString(), detail.client().email(), detail.totalAmount(),
                detail.supplyAmount(), detail.vatAmount(), detail.totalAmount() / 2,
                detail.vatIncluded(), detail.depositDueDate(), items);

        PaymentNotificationDto.Response result = paymentNotificationService.send(order.id(), request, userId);

        assertThat(result.status().name()).isEqualTo("SENT");
        tx().executeWithoutResult(status -> {
            PaymentNotificationRequest savedRequest = em.find(PaymentNotificationRequest.class, request.requestId());
            assertThat(savedRequest.getOrder().getId()).isEqualTo(order.id());
            assertThat(savedRequest.getUser().getId()).isEqualTo(userId);
            assertThat(savedRequest.getCreatedAt()).isNotNull();
            assertThat(savedRequest.getUpdatedAt()).isNotNull();
            assertThat(savedRequest.getCreatedBy()).isNotBlank();
            assertThat(savedRequest.getUpdatedBy()).isNotBlank();
        });
        assertThat(issuedRequests).hasSize(1);
        verify(mailService).send(
                eq(detail.client().email()),
                contains(detail.orderNo()),
                contains("계약금 청구액: 55,000원"));
        tx().executeWithoutResult(status -> {
            Payment payment = em.createQuery("select p from Payment p where p.order.id = :id", Payment.class)
                    .setParameter("id", order.id()).getSingleResult();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.WAITING);
            assertThat(em.createQuery("select count(l) from ClientNotiLog l "
                            + "where l.customerOrder.id = :id and l.kind = 'DEPOSIT'", Long.class)
                    .setParameter("id", order.id()).getSingleResult()).isEqualTo(1);
        });
        assertThat(paymentNotificationService.send(order.id(), request, userId).status().name()).isEqualTo("SENT");
        verify(mailService, times(1)).send(
                anyString(), anyString(),
                anyString());
    }

    @Test
    void orderUsesDirectShippingAndChangedDueDateWithoutConsentFields() {
        QuotationDto.DetailResponse quote = approvedForOrder();

        CustomerOrderDto.ShippingAddress address = new CustomerOrderDto.ShippingAddress(
                "54321", "부산 새주소로 2", "3층"
        );

        CustomerOrderDto.CreateResponse response = orderService.create(
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
        QuotationDto.DetailResponse quote = approvedForOrder();

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

        CustomerOrderDto.CreateRequest valid = orderRequest(
                quote, 100, dueDate(), true, null
        );

        CustomerOrderDto.ItemRequest foreign = new CustomerOrderDto.ItemRequest(
                Long.MAX_VALUE, 100
        );

        for (List<CustomerOrderDto.ItemRequest> items : List.of(
                List.<CustomerOrderDto.ItemRequest>of(),
                List.of(foreign),
                List.of(
                        valid.items().getFirst(),
                        valid.items().getFirst()
                )
        )) {
            CustomerOrderDto.CreateRequest invalid = new CustomerOrderDto.CreateRequest(
                    quote.id(),
                    items,
                    dueDate(),
                    true,
                    null,
                    "수신자",
                    "010-1234-5678",
                    NotifyChannel.EMAIL,
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
        QuotationDto.DetailResponse quote = approvedForOrder();

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
                        ResponseStatusException.class
                )
                .hasMessageContaining("승인");

        tx().executeWithoutResult(status ->
                em.find(Quotation.class, quote.id()).approve()
        );

        CustomerOrderDto.CreateResponse response = orderService.create(
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
                        ResponseStatusException.class
                )
                .hasMessageContaining(response.orderNo());
    }

    @Test
    void orderRejectsMissingShippingAndInvalidAccount() {
        QuotationDto.DetailResponse quote = approvedForOrder();

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

        QuotationDto.DetailResponse quote = approvedForOrder();

        CustomerOrderDto.CreateRequest request = orderRequest(
                quote, 100, dueDate(), true, null
        );

        List<Object> outcomes = registerConcurrently(request, request);

        assertThat(
                outcomes.stream().filter(
                        CustomerOrderDto.CreateResponse.class::isInstance
                )
        ).hasSize(1);

        assertThat(
                outcomes.stream().filter(
                        ResponseStatusException.class::isInstance
                )
        ).hasSize(1);
    }

    @Test
    void concurrentDifferentQuotationsCannotReserveTheSameProductionCapacity()
            throws Exception {

        QuotationDto.DetailResponse first = approvedForOrder();
        QuotationDto.DetailResponse second = approvedForOrder();

        tx().executeWithoutResult(status ->
                em.createQuery(
                        "update Line l set l.productionCapacity = 200"
                ).executeUpdate()
        );

        LocalDate earliest = orderScheduleService
                .previewSchedule(200, null, today())
                .earliestSchedule()
                .estimatedDueDate();

        List<Object> outcomes = registerConcurrently(
                orderRequest(first, 200, earliest, true, null),
                orderRequest(second, 200, earliest, true, null)
        );

        assertThat(
                outcomes.stream().filter(
                        CustomerOrderDto.CreateResponse.class::isInstance
                )
        ).hasSize(1);

        assertThat(
                outcomes.stream().filter(
                        IllegalArgumentException.class::isInstance
                )
        ).hasSize(1);
    }
}