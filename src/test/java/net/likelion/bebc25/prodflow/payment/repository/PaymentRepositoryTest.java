package net.likelion.bebc25.prodflow.payment.repository;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.Bank;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.VirtualAccountStatus;
import net.likelion.bebc25.prodflow.payment.service.PaymentQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PaymentRepositoryTest {
    @Autowired
    private EntityManager em;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentQueryService paymentQueryService;
    @Autowired
    private PaymentHistoryRepository paymentHistoryRepository;
    @Autowired
    private VirtualAccountRepository virtualAccountRepository;

    @Test
    @DisplayName("선택한 수주의 결제만 생성 순서대로 조회한다")
    void findPaymentsByOrderId() {
        List<Payment> payments = paymentRepository.findByOrder_IdOrderByIdAsc(7L);

        assertThat(payments).extracting(Payment::getId).containsExactly(9L, 10L);
        assertThat(payments).allSatisfy(payment -> assertThat(payment.getOrder().getId()).isEqualTo(7L));
        assertThat(paymentRepository.findByOrder_IdOrderByIdAsc(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("선택한 수주에 속한 입금 내역만 조회한다")
    void findHistoriesByOrderId() {
        List<PaymentHistory> histories = paymentHistoryRepository.findByPayment_Order_Id(7L);

        assertThat(histories).hasSize(2).allSatisfy(history ->
                assertThat(history.getPayment().getOrder().getId()).isEqualTo(7L));
        assertThat(paymentHistoryRepository.findByPayment_Order_Id(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("각 결제의 최신 계좌만 일괄 조회하고 다른 수주는 제외한다")
    void findLatestAccountsByOrderId() {
        Payment deposit = paymentRepository.findById(9L).orElseThrow();
        VirtualAccount latest = virtualAccountRepository.save(VirtualAccount.builder().payment(deposit)
                .bank(Bank.SHINHAN).accountNumber("NEWEST-ACCOUNT").accountName("재발급 계좌")
                .expiresAt(OffsetDateTime.of(
                        LocalDateTime.of(2027, 1, 1, 0, 0),
                        ZoneOffset.of("+09:00")
                )).build());
        latest.cancel();
        em.flush();
        em.clear();

        List<VirtualAccount> accounts = virtualAccountRepository.findLatestByOrderId(7L);

        assertThat(accounts).hasSize(2).allSatisfy(account ->
                assertThat(account.getPayment().getOrder().getId()).isEqualTo(7L));
        assertThat(accounts).filteredOn(account -> account.getPayment().getId().equals(9L))
                .singleElement().satisfies(account -> {
                    assertThat(account.getId()).isEqualTo(latest.getId());
                    assertThat(account.getStatus()).isEqualTo(VirtualAccountStatus.CANCELLED);
                });
        assertThat(virtualAccountRepository.findLatestByOrderId(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("입금관리 통계는 취소 수주를 제외하며 전체 금액을 대기·완료·미수로 구분한다")
    void getPaymentSummary() {
        var summary = paymentQueryService.getSummary();

        // 결제건이 아직 없는 수주의 잔금까지 전체 예정 금액에 반영한다.
        Long expectedOrderTotal = em.createQuery("""
                select coalesce(sum(o.totalAmount), 0)
                from CustomerOrder o
                where o.statusCode.code <> 'CANCELLED'
                """, Long.class).getSingleResult();

        assertThat(summary.totalScheduledAmount()).isEqualTo(expectedOrderTotal);
        assertThat(summary.waitingAmount())
                .isEqualTo(paymentRepository.sumAmountByStatus(PaymentStatus.WAITING));
        assertThat(summary.completedAmount())
                .isEqualTo(paymentRepository.sumAmountByStatus(PaymentStatus.PAID));

        assertThat(summary.unpaidAmount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(summary.unpaidAmount()).isEqualTo(Math.max(0L,
                summary.totalScheduledAmount()
                        - summary.waitingAmount()
                        - summary.completedAmount()));
    }

}
