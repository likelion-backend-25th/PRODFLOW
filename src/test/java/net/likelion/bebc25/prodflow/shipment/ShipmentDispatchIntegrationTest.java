package net.likelion.bebc25.prodflow.shipment;

import net.likelion.bebc25.prodflow.payment.client.PortOneClient;
import net.likelion.bebc25.prodflow.payment.config.PortOneProperties;
import net.likelion.bebc25.prodflow.payment.dto.PortOneDto;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.client_notification.entity.PaymentNotificationRequest;
import net.likelion.bebc25.prodflow.client_notification.enums.PaymentNotificationKind;
import net.likelion.bebc25.prodflow.client_notification.enums.PaymentNotificationStatus;
import net.likelion.bebc25.prodflow.client_notification.repository.ClientNotiLogRepository;
import net.likelion.bebc25.prodflow.client_notification.repository.PaymentNotificationRequestRepository;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.mail.service.MailService;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.Bank;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import net.likelion.bebc25.prodflow.payment.policy.PaymentAmountPolicy;
import net.likelion.bebc25.prodflow.payment.repository.PaymentRepository;
import net.likelion.bebc25.prodflow.payment.repository.VirtualAccountRepository;
import net.likelion.bebc25.prodflow.payment.service.PaymentService;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentDto;
import net.likelion.bebc25.prodflow.shipment.repository.ShipmentRepository;
import net.likelion.bebc25.prodflow.shipment.service.ShipmentDispatchService;
import net.likelion.bebc25.prodflow.shipment.service.ShipmentTrackingService;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 테스트 자체에는 @Transactional을 두지 않아 요청의 단계별 커밋·롤백을 검증한다.
@SpringBootTest
class ShipmentDispatchIntegrationTest {
    private static final long ORDER_ID = 4L;
    private static final ShipmentDto.CreateRequest INPUT =
            new ShipmentDto.CreateRequest(ORDER_ID, "04", "9900-0000-0004");

    @Autowired ShipmentDispatchService service;
    @Autowired ShipmentTrackingService trackingService;
    @Autowired CustomerOrderRepository orders;
    @Autowired ShipmentRepository shipments;
    @Autowired PaymentRepository payments;
    @Autowired VirtualAccountRepository accounts;
    @Autowired PaymentNotificationRequestRepository requests;
    @Autowired ClientNotiLogRepository logs;
    @Autowired CommonCodeRepository codes;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean PaymentService paymentService;
    @MockitoBean MailService mail;
    @MockitoBean StatusHistoryRepository histories;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            em.createQuery("delete from PaymentNotificationRequest r where r.order.id = :id")
                    .setParameter("id", ORDER_ID).executeUpdate();
            em.createQuery("delete from ClientNotiLog l where l.customerOrder.id = :id")
                    .setParameter("id", ORDER_ID).executeUpdate();
            em.createQuery("delete from Shipment s where s.orderId = :id")
                    .setParameter("id", ORDER_ID).executeUpdate();
            em.createQuery("delete from PaymentHistory h where h.payment.order.id = :id")
                    .setParameter("id", ORDER_ID).executeUpdate();
            em.createQuery("delete from VirtualAccount a where a.payment.order.id = :id")
                    .setParameter("id", ORDER_ID).executeUpdate();
            em.createQuery("delete from Payment p where p.order.id = :id")
                    .setParameter("id", ORDER_ID).executeUpdate();
            CustomerOrder order = orders.findById(ORDER_ID).orElseThrow();
            order.changeProgress(codes.findByCodeGroup_GroupCodeAndCodeAndStatus(
                    "ORDER_STATUS", "READY_TO_SHIP", CodeStatus.ACTIVE).orElseThrow());
            Payment deposit = Payment.builder().order(order).portonePaymentId("test-deposit")
                    .paymentType(PaymentType.DEPOSIT)
                    .amount(PaymentAmountPolicy.calculate(order.getTotalAmount(), PaymentType.DEPOSIT)).build();
            deposit.paidManually();
            payments.save(deposit);
        });
        var user = users.findById(1L).orElseThrow();
        var principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        doAnswer(call -> {
            CustomerOrder order = call.getArgument(0);
            if (payments.findByOrder_IdAndPaymentType(order.getId(), PaymentType.BALANCE).isEmpty()) {
                payments.saveAndFlush(Payment.builder().order(order)
                        .portonePaymentId("test-balance-" + UUID.randomUUID())
                        .paymentType(PaymentType.BALANCE)
                        .amount(PaymentAmountPolicy.calculate(order.getTotalAmount(), PaymentType.BALANCE)).build());
            }
            return null;
        }).when(paymentService).prepareAndScheduleIssue(any(), eq(PaymentType.BALANCE));
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            tx.executeWithoutResult(status -> {
                Payment payment = payments.findById(call.getArgument(0)).orElseThrow();
                if (accounts.findFirstByPayment_IdOrderByIdDesc(payment.getId()).isEmpty()) {
                    accounts.save(VirtualAccount.builder().payment(payment).bank(Bank.SHINHAN)
                            .portonePaymentId(payment.getPortonePaymentId()).accountNumber("1234567890")
                            .accountName("프로드플로우").expiresAt(OffsetDateTime.now().plusDays(7)).build());
                }
                payment.waitForDeposit();
            });
            return null;
        }).when(paymentService).issuePayment(anyLong());
    }

    @AfterEach
    void cleanAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("메일은 트랜잭션 밖에서 보내고 전송 완료 전까지 출고대기를 유지한다")
    void dispatchAfterMailAndReuseCompletedResult() {
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertReady();
            assertThat(request().getStatus()).isEqualTo(PaymentNotificationStatus.SENDING);
            assertThat(call.getArgument(2, String.class)).contains("990000000004", "1234567890", "잔금");
            return null;
        }).when(mail).send(anyString(), anyString(), anyString());
        assertThat(service.dispatch(INPUT, 1L).orderStatus()).isEqualTo("SHIPPING");
        assertThat(service.dispatch(INPUT, 1L).orderStatus()).isEqualTo("SHIPPING");
        assertThat(request().getStatus()).isEqualTo(PaymentNotificationStatus.SENT);
        assertThat(shipments.findByOrderId(ORDER_ID).orElseThrow().getStartedAt()).isNotNull();
        verify(mail, times(1)).send(anyString(), contains("배송 시작"), anyString());
        verify(histories, times(1)).save(any(StatusHistory.class));
        assertThat(logs.findAll()).filteredOn(l -> "SHIPPING".equals(l.getKind())
                && l.getCustomerOrder().getId().equals(ORDER_ID)).hasSize(1);
    }

    @Test
    @DisplayName("연결 실패 후 같은 출고 버튼으로 기존 운송장·잔금 계좌를 재사용한다")
    void retriesKnownMailFailure() {
        doThrow(new MailSendException("연결 실패", new ConnectException("refused")))
                .doNothing().when(mail).send(anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.dispatch(INPUT, 1L)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("기존 유효 계좌");
        assertReady();
        assertThat(request().getStatus()).isEqualTo(PaymentNotificationStatus.FAILED);
        Long shipmentId = shipments.findByOrderId(ORDER_ID).orElseThrow().getId();
        Long paymentId = payments.findByOrder_IdAndPaymentType(ORDER_ID, PaymentType.BALANCE).orElseThrow().getId();
        Long accountId = accounts.findFirstByPayment_IdOrderByIdDesc(paymentId).orElseThrow().getId();
        service.dispatch(INPUT, 1L);
        assertThat(shipments.findByOrderId(ORDER_ID).orElseThrow().getId()).isEqualTo(shipmentId);
        assertThat(accounts.findByPayment_IdOrderByIdDesc(paymentId)).extracting(VirtualAccount::getId)
                .containsExactly(accountId);
        verify(mail, times(2)).send(anyString(), anyString(), contains("1234567890"));
    }

    @Test
    @DisplayName("PG 실패는 이메일을 보내지 않고 출고대기·기존 요청을 보존한다")
    void recordsAccountFailure() {
        doThrow(new IllegalStateException("PG 연결 실패")).when(paymentService).issuePayment(anyLong());
        assertThatThrownBy(() -> service.dispatch(INPUT, 1L)).isInstanceOf(ResponseStatusException.class);
        assertReady();
        assertThat(request().getStatus()).isEqualTo(PaymentNotificationStatus.ISSUE_UNCERTAIN);
        verifyNoInteractions(mail);
    }

    @Test
    @DisplayName("메일 접수 여부가 불명확한 타임아웃은 재클릭해도 다시 전송하지 않는다")
    void doesNotResendUncertainMail() {
        doThrow(new MailSendException("응답 유실", new SocketTimeoutException("timeout")))
                .when(mail).send(anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.dispatch(INPUT, 1L)).isInstanceOf(ResponseStatusException.class);
        assertThat(request().getStatus()).isEqualTo(PaymentNotificationStatus.SEND_UNCERTAIN);
        assertThatThrownBy(() -> service.dispatch(INPUT, 1L)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("확인");
        assertReady();
        verify(mail, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("메일 성공 후 마지막 DB 작업 실패 시 메일 없이 출고 확정만 재시도한다")
    void retriesOnlyConfirmation() {
        when(histories.save(any())).thenThrow(new IllegalStateException("DB 저장 실패"))
                .thenAnswer(call -> call.getArgument(0));
        assertThatThrownBy(() -> service.dispatch(INPUT, 1L)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("확정만 재시도");
        assertReady();
        assertThat(request().getStatus()).isEqualTo(PaymentNotificationStatus.MAIL_SENT);
        assertThat(service.dispatch(INPUT, 1L).orderStatus()).isEqualTo("SHIPPING");
        verify(mail, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("발송 중 재클릭은 중복 처리하지 않는다")
    void rejectsConcurrentClick() {
        doAnswer(call -> {
            assertThatThrownBy(() -> service.dispatch(INPUT, 1L)).isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("중복 처리");
            return null;
        }).when(mail).send(anyString(), anyString(), anyString());
        service.dispatch(INPUT, 1L);
        verify(mail, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("계약금 발송 성공 기록이 있어도 출고 메일 요청과 충돌하지 않는다")
    void separatesDepositAndShipment() {
        tx.executeWithoutResult(status -> {
            var deposit = new PaymentNotificationRequest(UUID.randomUUID().toString(),
                    orders.findById(ORDER_ID).orElseThrow(), users.findById(1L).orElseThrow());
            deposit.complete(PaymentNotificationStatus.SENT, "계약금 안내 완료");
            requests.save(deposit);
        });
        service.dispatch(INPUT, 1L);
        assertThat(request().getKind()).isEqualTo(PaymentNotificationKind.SHIPMENT);
        assertThat(requests.findFirstByOrder_IdAndKindOrderByUpdatedAtDesc(ORDER_ID, PaymentNotificationKind.DEPOSIT)
                .orElseThrow().getStatus()).isEqualTo(PaymentNotificationStatus.SENT);
    }

    @Test
    @DisplayName("준비 중인 운송장은 자동·수동 배송 조회 대상이 아니다")
    void excludesPreparedShipmentFromTracking() {
        doThrow(new MailSendException("연결 실패", new ConnectException())).when(mail)
                .send(anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.dispatch(INPUT, 1L)).isInstanceOf(ResponseStatusException.class);
        Long id = shipments.findByOrderId(ORDER_ID).orElseThrow().getId();
        assertThat(shipments.findIncompleteShipments()).noneMatch(s -> s.getId().equals(id));
        assertThatThrownBy(() -> trackingService.refreshByShipmentId(id, 1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("출고만 배송 조회");
    }

    @Test
    @DisplayName("재시도 시 저장된 운송장 변경을 거절한다")
    void rejectsChangedTracking() {
        doThrow(new MailSendException("연결 실패", new ConnectException())).when(mail)
                .send(anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.dispatch(INPUT, 1L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.dispatch(new ShipmentDto.CreateRequest(ORDER_ID, "04", "99999999"), 1L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("운송장 번호가 다릅니다");
        verify(mail, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("만료된 잔금 계좌는 안내하지 않고 계좌 재발급 확인을 요구한다")
    void doesNotEmailExpiredAccount() {
        doThrow(new MailSendException("연결 실패", new ConnectException())).when(mail)
                .send(anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.dispatch(INPUT, 1L)).isInstanceOf(ResponseStatusException.class);
        tx.executeWithoutResult(status -> {
            Payment payment = payments.findByOrder_IdAndPaymentType(ORDER_ID, PaymentType.BALANCE).orElseThrow();
            accounts.findFirstByPayment_IdOrderByIdDesc(payment.getId()).orElseThrow()
                    .updateExpiresAt(OffsetDateTime.now().minusMinutes(1));
        });
        assertThatThrownBy(() -> service.dispatch(INPUT, 1L)).isInstanceOf(ResponseStatusException.class);
        assertReady();
        verify(mail, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("계약금 미입금이면 운송장·발송 요청을 만들지 않는다")
    void rejectsUnpaidDeposit() {
        tx.executeWithoutResult(status -> payments.findByOrder_IdAndPaymentType(ORDER_ID, PaymentType.DEPOSIT)
                .orElseThrow().updateStatus(PaymentStatus.READY));
        assertThatThrownBy(() -> service.dispatch(INPUT, 1L)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("계약금 입금 완료");
        assertThat(shipments.findByOrderId(ORDER_ID)).isEmpty();
        assertThat(requests.findFirstByOrder_IdAndKindOrderByUpdatedAtDesc(ORDER_ID, PaymentNotificationKind.SHIPMENT))
                .isEmpty();
        verifyNoInteractions(mail, paymentService);
    }

    private PaymentNotificationRequest request() {
        return requests.findFirstByOrder_IdAndKindOrderByUpdatedAtDesc(ORDER_ID, PaymentNotificationKind.SHIPMENT)
                .orElseThrow();
    }

    @Test
    @DisplayName("출고대기가 아닌 수주는 운송장과 발송 요청을 만들지 않는다")
    void rejectsNonReadyOrder() {
        tx.executeWithoutResult(status -> orders.findById(ORDER_ID).orElseThrow().changeProgress(
                codes.findByCodeGroup_GroupCodeAndCodeAndStatus(
                        "ORDER_STATUS", "PACKING", CodeStatus.ACTIVE).orElseThrow()));
        assertThatThrownBy(() -> service.dispatch(INPUT, 1L)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("출고대기 수주만");
        assertThat(shipments.findByOrderId(ORDER_ID)).isEmpty();
        verifyNoInteractions(paymentService, mail);
    }

    @Test
    @DisplayName("다른 수주에 이미 등록된 택배사·운송장 조합은 거절한다")
    void rejectsDuplicateTrackingAcrossOrders() {
        ShipmentDto.CreateRequest duplicate = tx.execute(status -> {
            var existing = shipments.findAll().stream().filter(s -> s.getOrderId() != ORDER_ID)
                    .findFirst().orElseThrow();
            return new ShipmentDto.CreateRequest(ORDER_ID, existing.getCarrier().getCode(), existing.getTrackingNumber());
        });
        assertThatThrownBy(() -> service.dispatch(duplicate, 1L)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이미 등록된 운송장");
        assertThat(shipments.findByOrderId(ORDER_ID)).isEmpty();
        verifyNoInteractions(paymentService, mail);
    }

    private void assertReady() {
        String orderStatus = tx.execute(status -> orders.findById(ORDER_ID).orElseThrow().getStatusCode().getCode());
        assertThat(orderStatus).isEqualTo("READY_TO_SHIP");
        assertThat(shipments.findByOrderId(ORDER_ID).orElseThrow().getStartedAt()).isNull();
    }
}

// 실제 결제 서비스와 요청 단위 영속성 컨텍스트를 함께 검증한다.
// PortOne과 SMTP만 대체하여 발급 후 별도 트랜잭션의 상태 반영 경로를 유지한다.
@SpringBootTest(properties = "spring.jpa.open-in-view=true")
@AutoConfigureMockMvc
class ShipmentDispatchRealPaymentIntegrationTest {
    private static final long ORDER_ID = 4L;
    private static final ShipmentDto.CreateRequest INPUT =
            new ShipmentDto.CreateRequest(ORDER_ID, "04", "990000000004");

    @Autowired MockMvc mvc;
    @Autowired ShipmentDispatchService dispatch;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired CustomerOrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired VirtualAccountRepository accounts;
    @Autowired CommonCodeRepository codes;
    @Autowired UserRepository users;
    @Autowired PortOneProperties properties;
    @MockitoBean PortOneClient portOne;
    @MockitoBean MailService mail;

    private final Map<String, PortOneDto.IssueRequest> issued = new ConcurrentHashMap<>();
    private TransactionTemplate tx;
    private CustomUserDetails principal;

    @BeforeEach
    void setUp() {
        issued.clear();
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            em.createQuery("delete from PaymentNotificationRequest r where r.order.id = :id")
                    .setParameter("id", ORDER_ID).executeUpdate();
            em.createQuery("delete from ClientNotiLog l where l.customerOrder.id = :id")
                    .setParameter("id", ORDER_ID).executeUpdate();
            em.createQuery("delete from Shipment s where s.orderId = :id")
                    .setParameter("id", ORDER_ID).executeUpdate();
            em.createQuery("delete from PaymentHistory h where h.payment.order.id = :id")
                    .setParameter("id", ORDER_ID).executeUpdate();
            em.createQuery("delete from VirtualAccount a where a.payment.order.id = :id")
                    .setParameter("id", ORDER_ID).executeUpdate();
            em.createQuery("delete from Payment p where p.order.id = :id")
                    .setParameter("id", ORDER_ID).executeUpdate();
            CustomerOrder order = orders.findById(ORDER_ID).orElseThrow();
            order.changeProgress(codes.findByCodeGroup_GroupCodeAndCodeAndStatus(
                    "ORDER_STATUS", "READY_TO_SHIP", CodeStatus.ACTIVE).orElseThrow());
            Payment deposit = Payment.builder().order(order).portonePaymentId("repro-deposit")
                    .paymentType(PaymentType.DEPOSIT)
                    .amount(PaymentAmountPolicy.calculate(order.getTotalAmount(), PaymentType.DEPOSIT)).build();
            deposit.paidManually();
            payments.save(deposit);
        });
        principal = new CustomUserDetails(users.findById(1L).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));

        doAnswer(call -> {
            PortOneDto.IssueRequest request = call.getArgument(0);
            issued.put(request.paymentId(), request);
            return null;
        }).when(portOne).issueInstantPayment(any());
        when(portOne.getPayment(anyString())).thenAnswer(call -> {
            String id = call.getArgument(0);
            PortOneDto.IssueRequest request = issued.get(id);
            if (request == null) throw new IllegalStateException("발급 요청 없이 결과 조회됨: " + id);

            // 외부 상태 지연 없이 즉시 정상 발급 결과를 반환한다.
            return new PortOneDto.PaymentSnapshot(id, "pg-" + id, "VIRTUAL_ACCOUNT_ISSUED",
                    properties.storeId(), null, null, "KRW",
                    new PortOneDto.PaymentSnapshot.Amount(request.amount(), null),
                    new PortOneDto.PaymentSnapshot.Method("PaymentMethodVirtualAccount", request.bank(),
                            "1234567890", "재현 계좌", request.dueDate()),
                    new PortOneDto.PaymentSnapshot.Channel(properties.channelKey(), "TOSSPAYMENTS"),
                    OffsetDateTime.now(), null, null, null);
        });
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("첫 HTTP 출고부터 발급 결과를 읽어 배송중으로 전환하고 재클릭해도 중복 처리하지 않는다")
    void firstHttpDispatchUsesIssuedAccountAndDoesNotDuplicateOnRetry() throws Exception {
        mvc.perform(post("/api/v1/shipments").with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":4,\"carrierCode\":\"04\",\"trackingNumber\":\"990000000004\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderStatus").value("SHIPPING"));
        tx.executeWithoutResult(status -> {
            Payment balance = payments.findByOrder_IdAndPaymentType(ORDER_ID, PaymentType.BALANCE).orElseThrow();
            VirtualAccount account = accounts.findFirstByPayment_IdOrderByIdDesc(balance.getId()).orElseThrow();
            assertThat(balance.getStatus()).isEqualTo(PaymentStatus.WAITING);
            assertThat(account.isActive()).isTrue();
            assertThat(account.isExpired()).isFalse();
            assertThat(account.getPortonePaymentId()).isEqualTo(balance.getPortonePaymentId());
            assertThat(orders.findById(ORDER_ID).orElseThrow().getStatusCode().getCode()).isEqualTo("SHIPPING");
        });
        verify(mail, times(1)).send(anyString(), anyString(), anyString());
        mvc.perform(post("/api/v1/shipments").with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":4,\"carrierCode\":\"04\",\"trackingNumber\":\"990000000004\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderStatus").value("SHIPPING"));
        verify(portOne, times(1)).issueInstantPayment(any());
        verify(mail, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("대조군: HTTP 영속성 컨텍스트 없이 실제 결제 서비스를 호출하면 첫 출고가 성공한다")
    void firstDispatchWithoutRequestPersistenceContextSucceeds() {
        assertThat(dispatch.dispatch(INPUT, 1L).orderStatus()).isEqualTo("SHIPPING");
        verify(portOne, times(1)).issueInstantPayment(any());
        verify(mail, times(1)).send(anyString(), anyString(), anyString());
    }
}
