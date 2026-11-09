package net.likelion.bebc25.prodflow.payment.repository;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.VirtualAccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private PaymentHistoryRepository paymentHistoryRepository;
    @Autowired
    private VirtualAccountRepository virtualAccountRepository;

    @Test
    @DisplayName("선택한 수주의 결제만 생성 순서대로 조회한다")
    void findPaymentsByOrderId() {
        List<Payment> payments = paymentRepository.findByOrder_IdOrderByIdAsc(7L);

        assertThat(payments).extracting(Payment::getId).containsExactly(7L, 16L);
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
        Payment deposit = paymentRepository.findById(7L).orElseThrow();
        VirtualAccount latest = virtualAccountRepository.save(VirtualAccount.builder().payment(deposit)
                .bankCode("004").accountNumber("NEWEST-ACCOUNT").accountName("재발급 계좌")
                .expiresAt(LocalDateTime.of(2027, 1, 1, 0, 0)).build());
        latest.cancel();
        em.flush();
        em.clear();

        List<VirtualAccount> accounts = virtualAccountRepository.findLatestByOrderId(7L);

        assertThat(accounts).hasSize(2).allSatisfy(account ->
                assertThat(account.getPayment().getOrder().getId()).isEqualTo(7L));
        assertThat(accounts).filteredOn(account -> account.getPayment().getId().equals(7L))
                .singleElement().satisfies(account -> {
                    assertThat(account.getId()).isEqualTo(latest.getId());
                    assertThat(account.getStatus()).isEqualTo(VirtualAccountStatus.CANCELLED);
                });
        assertThat(virtualAccountRepository.findLatestByOrderId(Long.MAX_VALUE)).isEmpty();
    }
}
