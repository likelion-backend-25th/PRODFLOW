package net.likelion.bebc25.prodflow.payment.service;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.PaymentMethod;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.VirtualAccountStatus;
import net.likelion.bebc25.prodflow.payment.repository.PaymentHistoryRepository;
import net.likelion.bebc25.prodflow.payment.repository.PaymentRepository;
import net.likelion.bebc25.prodflow.payment.repository.VirtualAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // 공통 fixture의 일부 stub은 조기 반환 시 사용되지 않음
class PaymentReissueAvailabilityTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private VirtualAccountRepository virtualAccountRepository;
    @Mock private PaymentHistoryRepository paymentHistoryRepository;
    @InjectMocks private PaymentQueryService paymentQueryService;

    private static final long PAYMENT_ID = 31L;

    private Payment arrange(PaymentStatus status, PaymentMethod method,
                            VirtualAccountStatus accountStatus, String orderStatus) {
        Payment payment = mock(Payment.class);
        CustomerOrder order = mock(CustomerOrder.class, RETURNS_DEEP_STUBS);
        VirtualAccount account = mock(VirtualAccount.class);

        when(payment.getId()).thenReturn(PAYMENT_ID);
        when(payment.getOrder()).thenReturn(order);
        when(payment.getPaymentType()).thenReturn(null);
        when(payment.getAmount()).thenReturn(50_000L);
        when(payment.getStatus()).thenReturn(status);
        when(payment.getPaymentMethod()).thenReturn(method);
        when(payment.getPortonePaymentId()).thenReturn("PF-EXAMPLE-31");

        when(order.getId()).thenReturn(12L);
        when(order.getOrderNo()).thenReturn("PO-TEST-12");
        when(order.getClient().getCompany()).thenReturn("테스트 거래처");
        when(order.getTotalAmount()).thenReturn(100_000L);
        when(order.getStatusCode().getCode()).thenReturn(orderStatus);

        when(account.getPayment()).thenReturn(payment);
        when(account.getId()).thenReturn(45L);
        when(account.getEffectiveStatus()).thenReturn(accountStatus);

        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(virtualAccountRepository.findFirstByPayment_IdOrderByIdDesc(PAYMENT_ID))
                .thenReturn(Optional.of(account));
        when(paymentHistoryRepository.findByPayment_Id(PAYMENT_ID))
                .thenReturn(Optional.empty());
        return payment;
    }

    @Test
    void expiredWaitingVirtualAccountIsAvailable() {
        arrange(PaymentStatus.WAITING, PaymentMethod.VA,
                VirtualAccountStatus.EXPIRED, "WAITING");

        assertThat(paymentQueryService.getPaymentDetail(PAYMENT_ID).reissueAvailable()).isTrue();
    }

    @Test
    void activeVirtualAccountIsTechnicallyAvailableUnderExistingServicePolicy() {
        arrange(PaymentStatus.WAITING, PaymentMethod.VA,
                VirtualAccountStatus.ACTIVE, "WAITING");

        // 현재 POST 재발급 서비스는 ACTIVE 계좌도 허용한다.
        // '만료 계좌에서만 버튼 노출'은 프론트에서 EXPIRED 조건을 함께 사용한다.
        assertThat(paymentQueryService.getPaymentDetail(PAYMENT_ID).reissueAvailable()).isTrue();
    }

    @Test
    void usedVirtualAccountIsUnavailable() {
        arrange(PaymentStatus.WAITING, PaymentMethod.VA,
                VirtualAccountStatus.USED, "WAITING");

        assertThat(paymentQueryService.getPaymentDetail(PAYMENT_ID).reissueAvailable()).isFalse();
    }

    @Test
    void paidPaymentIsUnavailable() {
        arrange(PaymentStatus.PAID, PaymentMethod.VA,
                VirtualAccountStatus.USED, "WAITING");

        assertThat(paymentQueryService.getPaymentDetail(PAYMENT_ID).reissueAvailable()).isFalse();
    }

    @Test
    void reissueInProgressIsUnavailableForNewButtonRequest() {
        arrange(PaymentStatus.REISSUE_CLOSING, PaymentMethod.VA,
                VirtualAccountStatus.EXPIRED, "WAITING");

        assertThat(paymentQueryService.getPaymentDetail(PAYMENT_ID).reissueAvailable()).isFalse();
    }

    @Test
    void cancelledOrderIsUnavailable() {
        arrange(PaymentStatus.WAITING, PaymentMethod.VA,
                VirtualAccountStatus.EXPIRED, "CANCELLED");

        assertThat(paymentQueryService.getPaymentDetail(PAYMENT_ID).reissueAvailable()).isFalse();
    }

    @Test
    void manualDepositIsUnavailable() {
        arrange(PaymentStatus.WAITING, PaymentMethod.MD,
                VirtualAccountStatus.EXPIRED, "WAITING");

        assertThat(paymentQueryService.getPaymentDetail(PAYMENT_ID).reissueAvailable()).isFalse();
    }

    @Test
    void paymentWithHistoryIsUnavailable() {
        Payment payment = arrange(PaymentStatus.WAITING, PaymentMethod.VA,
                VirtualAccountStatus.EXPIRED, "WAITING");
        PaymentHistory history = mock(PaymentHistory.class);
        when(history.getPayment()).thenReturn(payment);
        when(paymentHistoryRepository.findByPayment_Id(PAYMENT_ID))
                .thenReturn(Optional.of(history));

        assertThat(paymentQueryService.getPaymentDetail(PAYMENT_ID).reissueAvailable()).isFalse();
    }
}
