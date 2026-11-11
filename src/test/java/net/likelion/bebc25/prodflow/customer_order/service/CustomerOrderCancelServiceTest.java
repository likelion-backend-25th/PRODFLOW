package net.likelion.bebc25.prodflow.customer_order.service;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.client_notification.repository.PaymentNotificationRequestRepository;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDetailDto;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.VirtualAccountStatus;
import net.likelion.bebc25.prodflow.payment.repository.PaymentHistoryRepository;
import net.likelion.bebc25.prodflow.payment.repository.PaymentRepository;
import net.likelion.bebc25.prodflow.payment.repository.VirtualAccountRepository;
import net.likelion.bebc25.prodflow.payment.service.PaymentService;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import net.likelion.bebc25.prodflow.work_order.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CustomerOrderCancelService 단위 테스트.
 * <p>
 * Mockito로 DB 저장소와 PaymentService(PortOne 호출 담당)를 대체하므로
 * RDS와 실제 PortOne 계정에는 접근하지 않는다.
 * TransactionTemplate의 분기와 호출 순서를 검증하기 위해 가짜 트랜잭션 상태를 반환한다.
 */
@ExtendWith(MockitoExtension.class)
class CustomerOrderCancelServiceTest {

    private static final long ORDER_ID = 11L;
    private static final long QUOTATION_ID = 22L;
    private static final long USER_ID = 1L;
    private static final long PAYMENT_ID = 33L;

    @Mock
    private CustomerOrderRepository orderRepository;
    @Mock
    private QuotationRepository quotationRepository;
    @Mock
    private CommonCodeRepository commonCodeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;
    @Mock
    private VirtualAccountRepository accountRepository;
    @Mock
    private PaymentNotificationRequestRepository notificationRepository;
    @Mock
    private WorkOrderRepository workOrderRepository;
    @Mock
    private CustomerOrderProgressService progressService;
    @Mock
    private CustomerOrderService orderService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private CustomerOrderCancelService cancelService;

    private User actor;
    private Quotation quotation;
    private CustomerOrder order;
    private CommonCode orderCode;
    private CustomerOrderDetailDto.Response detail;

    @BeforeEach
    void setUp() {
        actor = mock(User.class);
        quotation = mock(Quotation.class);
        order = mock(CustomerOrder.class);
        orderCode = mock(CommonCode.class);
        detail = mock(CustomerOrderDetailDto.Response.class);

        // 실제 트랜잭션/DB를 열지 않고 TransactionTemplate의 콜백을 실행한다.
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());

        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(actor));
        lenient().when(actor.getStatus()).thenReturn(UserStatus.ACTIVE);
        lenient().when(actor.getRole()).thenReturn(UserRole.ORDER_MANAGER);
        // isMustChangePassword()의 Mockito 기본 반환값은 false.

        lenient().when(orderRepository.findQuotationIdById(ORDER_ID))
                .thenReturn(Optional.of(QUOTATION_ID));
        lenient().when(commonCodeRepository.findOrderWaitingForUpdate(CodeStatus.ACTIVE))
                .thenReturn(Optional.of(mock(CommonCode.class)));
        lenient().when(quotationRepository.findByIdForUpdate(QUOTATION_ID))
                .thenReturn(Optional.of(quotation));
        lenient().when(orderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        lenient().when(order.getStatusCode()).thenReturn(orderCode);
        lenient().when(orderCode.getCode()).thenReturn("WAITING");
        lenient().when(order.getPaymentStatus()).thenReturn(OrderPaymentStatus.UNPAID);
        lenient().when(quotation.getStatus()).thenReturn(QuotationStatus.APPROVED);
        lenient().when(paymentHistoryRepository.findByPayment_Order_Id(ORDER_ID))
                .thenReturn(List.of());
        lenient().when(notificationRepository.findFirstByOrder_IdOrderByUpdatedAtDesc(ORDER_ID))
                .thenReturn(Optional.empty());
        lenient().when(workOrderRepository.existsByOrderProduct_CustomerOrder_Id(ORDER_ID))
                .thenReturn(false);
        lenient().when(paymentRepository.findByOrder_IdOrderByIdAsc(ORDER_ID))
                .thenReturn(List.of());
        lenient().when(orderService.getOrder(ORDER_ID)).thenReturn(detail);
    }

    @Test
    @DisplayName("가상계좌 말소 성공 후 결제·계좌가 취소 상태라면 견적과 수주를 취소하고 사유를 기록한다")
    void cancelSuccessWithClosedVirtualAccount() {
        givenPayment(PaymentStatus.CANCELLED, VirtualAccountStatus.CANCELLED);
        var request = request("  고객 요청으로 취소  ");

        var result = cancelService.cancel(ORDER_ID, request, USER_ID);

        assertThat(result).isSameAs(detail);
        InOrder sequence = inOrder(transactionManager, paymentService, quotation, progressService);
        sequence.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
        sequence.verify(transactionManager).commit(any(TransactionStatus.class));
        sequence.verify(paymentService).closeUnpaidOrderPayments(ORDER_ID, USER_ID);
        sequence.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
        sequence.verify(quotation).cancel();
        sequence.verify(progressService)
                .changeProgress(order, "CANCELLED", actor, "고객 요청으로 취소");
        sequence.verify(transactionManager).commit(any(TransactionStatus.class));
        verify(transactionManager, never()).rollback(any(TransactionStatus.class));
    }

    @Test
    @DisplayName("결제건이 아예 없는 수주도 기존 서비스의 정책대로 취소 처리한다")
    void cancelSuccessWithoutPayments() {
        var result = cancelService.cancel(ORDER_ID, request("취소 사유"), USER_ID);

        assertThat(result).isSameAs(detail);
        verify(paymentService).closeUnpaidOrderPayments(ORDER_ID, USER_ID);
        verify(quotation).cancel();
        verify(progressService).changeProgress(order, "CANCELLED", actor, "취소 사유");
    }

    @Test
    @DisplayName("이미 취소된 수주는 PG 말소와 상태 이력을 반복 처리하지 않는다")
    void alreadyCancelledIsIdempotent() {
        when(orderCode.getCode()).thenReturn("CANCELLED");
        when(quotation.getStatus()).thenReturn(QuotationStatus.CANCELLED);

        var result = cancelService.cancel(ORDER_ID, request("다시 취소"), USER_ID);

        assertThat(result).isSameAs(detail);
        verifyNoInteractions(paymentService, progressService);
        verify(quotation, never()).cancel();
        verify(transactionManager).commit(any(TransactionStatus.class));
    }

    @Test
    @DisplayName("취소된 수주와 미취소 견적의 상태가 다르면 409를 반환한다")
    void alreadyCancelledButQuotationNotCancelled() {
        when(orderCode.getCode()).thenReturn("CANCELLED");

        assertConflict("취소 상태가 일치하지 않습니다");
        verifyNoInteractions(paymentService, progressService);
    }

    @Test
    @DisplayName("대기 상태가 아닌 수주는 취소하지 않는다")
    void rejectNonWaitingOrder() {
        when(orderCode.getCode()).thenReturn("IN_PRODUCTION");

        assertConflict("입금 전 대기 수주만");
        verifyNoInteractions(paymentService, progressService);
    }

    @Test
    @DisplayName("이미 입금 처리된 수주는 환불 없이 취소하지 않는다")
    void rejectPaidOrder() {
        when(order.getPaymentStatus()).thenReturn(OrderPaymentStatus.PAID);

        assertConflict("입금 전 대기 수주만");
        verifyNoInteractions(paymentService, progressService);
    }

    @Test
    @DisplayName("원본 견적이 승인 상태가 아니면 PG를 호출하지 않는다")
    void rejectUnapprovedQuotation() {
        when(quotation.getStatus()).thenReturn(QuotationStatus.DRAFT);

        assertConflict("원본 견적이 승인 상태가 아닙니다");
        verifyNoInteractions(paymentService, progressService);
    }

    @Test
    @DisplayName("입금 내역이 있으면 PG를 호출하기 전에 취소를 거부한다")
    void rejectOrderWithPaymentHistory() {
        when(paymentHistoryRepository.findByPayment_Order_Id(ORDER_ID))
                .thenReturn(List.of(mock(PaymentHistory.class)));

        assertConflict("입금 내역이 있는 수주");
        verifyNoInteractions(paymentService, progressService);
    }

    @Test
    @DisplayName("작업지시가 이미 생성되었다면 취소를 거부한다")
    void rejectOrderWithWorkOrder() {
        when(workOrderRepository.existsByOrderProduct_CustomerOrder_Id(ORDER_ID))
                .thenReturn(true);

        assertConflict("작업지시가 생성된 수주");
        verifyNoInteractions(paymentService, progressService);
    }

    @Test
    @DisplayName("권한이 없는 직원은 수주 취소 및 PG 호출을 할 수 없다")
    void rejectActorWithoutRole() {
        when(actor.getRole()).thenReturn(UserRole.PRODUCTION_MANAGER);

        assertThatThrownBy(() -> cancelService.cancel(ORDER_ID, request("권한 검사"), USER_ID))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("수주를 취소할 수 있습니다");
        verifyNoInteractions(paymentService, progressService);
    }

    @Test
    @DisplayName("비밀번호 변경이 필요한 직원은 수주를 취소할 수 없다")
    void rejectActorWhoMustChangePassword() {
        when(actor.isMustChangePassword()).thenReturn(true);

        assertThatThrownBy(() -> cancelService.cancel(ORDER_ID, request("직원 상태 확인"), USER_ID))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(paymentService, progressService);
    }

    @Test
    @DisplayName("PortOne 말소 서비스가 실패하면 견적·수주 취소를 확정하지 않는다")
    void doNotCancelOrderWhenPortOneCloseFails() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "PortOne 말소 결과 불확실"))
                .when(paymentService).closeUnpaidOrderPayments(ORDER_ID, USER_ID);

        assertConflict("PortOne 말소 결과 불확실");
        verify(quotation, never()).cancel();
        verifyNoInteractions(progressService);
        verify(transactionManager).commit(any(TransactionStatus.class));
        verify(transactionManager, never()).rollback(any(TransactionStatus.class));
    }

    @Test
    @DisplayName("말소 호출 후에도 Payment가 CANCELLED가 아니면 최종 취소를 거부한다")
    void rejectWhenPaymentIsNotCancelled() {
        givenPayment(PaymentStatus.WAITING, VirtualAccountStatus.CANCELLED);

        assertConflict("말소되지 않았거나 결과 확인 중인 결제건");
        verify(paymentService).closeUnpaidOrderPayments(ORDER_ID, USER_ID);
        verify(quotation, never()).cancel();
        verifyNoInteractions(progressService);
        verify(transactionManager).rollback(any(TransactionStatus.class));
    }

    @Test
    @DisplayName("Payment는 취소됐어도 가상계좌가 ACTIVE이면 최종 취소를 거부한다")
    void rejectWhenVirtualAccountIsActive() {
        givenPayment(PaymentStatus.CANCELLED, VirtualAccountStatus.ACTIVE);

        assertConflict("가상계좌 말소가 완료되지 않았습니다");
        verify(paymentService).closeUnpaidOrderPayments(ORDER_ID, USER_ID);
        verify(quotation, never()).cancel();
        verifyNoInteractions(progressService);
    }

    @Test
    @DisplayName("계좌 이력 중 EXPIRED가 남아 있으면 현재 구현은 최종 취소를 거부한다")
    void rejectWhenOldVirtualAccountIsExpired() {
        givenPayment(PaymentStatus.CANCELLED, VirtualAccountStatus.EXPIRED);

        assertConflict("가상계좌 말소가 완료되지 않았습니다");
        verify(quotation, never()).cancel();
    }

    @Test
    @DisplayName("PG 말소 후 결제 입금 이력이 새로 생겼다면 재검증에서 409를 반환한다")
    void rejectWhenPaymentHistoryAppearsAfterExternalCall() {
        when(paymentHistoryRepository.findByPayment_Order_Id(ORDER_ID))
                .thenReturn(List.of(), List.of(mock(PaymentHistory.class)));

        assertConflict("입금 내역이 있는 수주");
        verify(paymentService).closeUnpaidOrderPayments(ORDER_ID, USER_ID);
        verify(quotation, never()).cancel();
        verifyNoInteractions(progressService);
    }


    @Test
    @DisplayName("PG 호출 후 다른 요청이 이미 수주를 취소했다면 중복 기록하지 않는다")
    void anotherTransactionCancelledOrderAfterPgCall() {

        // 최초 검증에서는 WAITING / APPROVED 상태
        when(orderCode.getCode()).thenReturn("WAITING");
        when(quotation.getStatus()).thenReturn(QuotationStatus.APPROVED);

        // PortOne 말소 작업이 수행되는 동안
        // 다른 트랜잭션에서 수주와 견적을 취소했다고 가정
        doAnswer(invocation -> {
            doReturn("CANCELLED")
                    .when(orderCode).getCode();

            doReturn(QuotationStatus.CANCELLED)
                    .when(quotation).getStatus();

            return null;
        }).when(paymentService)
                .closeUnpaidOrderPayments(ORDER_ID, USER_ID);

        // 수주 취소 요청
        var result = cancelService.cancel(
                ORDER_ID,
                request("중복 취소"),
                USER_ID
        );

        // 이미 취소된 수주 정보를 반환
        assertThat(result).isSameAs(detail);

        // PG 말소 서비스는 한 번만 호출
        verify(paymentService, times(1))
                .closeUnpaidOrderPayments(ORDER_ID, USER_ID);

        // 수주·견적 취소는 중복 실행되지 않음
        verify(quotation, never()).cancel();
        verifyNoInteractions(progressService);
    }


    @Test
    @DisplayName("마감 처리 직전에 결제 이력이 있으면 수주·견적 상태를 변경하지 않는다")
    void rejectWhenPaymentHistoryExistsForIndividualPayment() {
        var payment = givenPayment(PaymentStatus.CANCELLED, VirtualAccountStatus.CANCELLED);
        when(paymentHistoryRepository.existsByPayment_Id(PAYMENT_ID)).thenReturn(true);

        assertConflict("입금 내역이 있는 결제");
        verify(paymentRepository).getForUpdate(payment.getId());
        verify(quotation, never()).cancel();
        verifyNoInteractions(progressService);
    }

    private Payment givenPayment(PaymentStatus paymentStatus, VirtualAccountStatus accountStatus) {
        Payment payment = mock(Payment.class);
        VirtualAccount account = mock(VirtualAccount.class);
        when(payment.getId()).thenReturn(PAYMENT_ID);
        lenient().when(payment.getStatus()).thenReturn(paymentStatus);
        lenient().when(account.getStatus()).thenReturn(accountStatus);
        when(paymentRepository.findByOrder_IdOrderByIdAsc(ORDER_ID))
                .thenReturn(List.of(payment));
        when(paymentRepository.getForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        lenient().when(accountRepository.findByPayment_IdOrderByIdDesc(PAYMENT_ID))
                .thenReturn(List.of(account));
        return payment;
    }

    private CustomerOrderDto.CancelRequest request(String reason) {
        return new CustomerOrderDto.CancelRequest(reason);
    }

    private void assertConflict(String expectedMessage) {
        assertThatThrownBy(() -> cancelService.cancel(ORDER_ID, request("취소 테스트"), USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(expectedMessage)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }
}
