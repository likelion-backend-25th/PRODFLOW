package net.likelion.bebc25.prodflow.payment.service;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.enums.NotifyChannel;
import net.likelion.bebc25.prodflow.payment.dto.PaymentDto;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.Bank;
import net.likelion.bebc25.prodflow.payment.enums.PaymentMethod;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import net.likelion.bebc25.prodflow.payment.enums.VirtualAccountStatus;
import net.likelion.bebc25.prodflow.payment.repository.PaymentHistoryRepository;
import net.likelion.bebc25.prodflow.payment.repository.PaymentRepository;
import net.likelion.bebc25.prodflow.payment.repository.VirtualAccountRepository;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PaymentServiceTest {
    @Autowired
    private EntityManager em;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentHistoryRepository paymentHistoryRepository;
    @Autowired
    private VirtualAccountRepository virtualAccountRepository;

    private CustomerOrder order;

    @BeforeEach
    void setUp() {
        User user = User.builder().name("결제 조회 담당자").email("payment-query@test.com")
                .password("1234").phone("010-1111-2222").role(UserRole.ORDER_MANAGER).build();
        em.persist(user);
        Client client = new Client("결제 조회 거래처", "김담당", "client@test.com", "02-1234-5678",
                new Address("12345", "서울시 테스트로 1", "2층"), user);
        em.persist(client);
        LocalDate dueDate = LocalDate.now().plusDays(30);
        Quotation quotation = new Quotation("QT-PAYMENT-QUERY", client, client.getEmployee(), user,
                dueDate, dueDate, dueDate.minusDays(7), dueDate.minusDays(3), dueDate,
                true, QuotationStatus.APPROVED, 10001L);
        em.persist(quotation);
        order = new CustomerOrder("PF-PAYMENT-QUERY", quotation, client, client.getEmployee(),
                client.getEmail(), client.getPhone(), "회사 주소", user,
                dueDate, dueDate, dueDate.minusDays(7), dueDate.minusDays(3), LocalDate.now().plusDays(3),
                "배송 주소", "홍수신", "010-3333-4444", NotifyChannel.EMAIL,
                null, em.find(CommonCode.class, 6L), true, 10001L);
        em.persist(order);
        em.flush();
    }

    @Test
    @DisplayName("결제 미생성 수주도 계약금 내림 금액과 미수금을 반환하고 데이터를 생성하지 않는다")
    void getOrderWithoutPayments() {
        long paymentCount = paymentRepository.count();
        long historyCount = paymentHistoryRepository.count();
        long accountCount = virtualAccountRepository.count();

        PaymentDto.OrderResponse response = paymentService.getByOrderId(order.getId());

        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.totalAmount()).isEqualTo(10001);
        assertThat(response.depositAmount()).isEqualTo(5000);
        assertThat(response.paidAmount()).isZero();
        assertThat(response.balanceAmount()).isEqualTo(10001);
        assertThat(response.payments()).isEmpty();
        assertThat(paymentRepository.count()).isEqualTo(paymentCount);
        assertThat(paymentHistoryRepository.count()).isEqualTo(historyCount);
        assertThat(virtualAccountRepository.count()).isEqualTo(accountCount);
    }

    @Test
    @DisplayName("계약금 입금과 실제 입금 계좌는 보존하고 결제의 최신 발급 계좌를 반환한다")
    void getPartiallyPaidOrderWithLatestAccount() {
        Payment deposit = savePayment(PaymentType.DEPOSIT, 5000L);
        deposit.paidByVirtualAccount();
        VirtualAccount oldAccount = saveAccount(deposit, "OLD-ACCOUNT");
        oldAccount.expire();
        PaymentHistory history = saveHistory(deposit, oldAccount);
        VirtualAccount latestAccount = saveAccount(deposit, "LATEST-ACCOUNT");
        em.flush();
        em.clear();

        PaymentDto.OrderResponse response = paymentService.getByOrderId(order.getId());

        assertThat(response.paidAmount()).isEqualTo(5000);
        assertThat(response.balanceAmount()).isEqualTo(5001);
        assertThat(response.payments()).hasSize(1);
        PaymentDto.OrderPaymentResponse payment = response.payments().getFirst();
        assertThat(payment.paymentId()).isEqualTo(deposit.getId());
        assertThat(payment.virtualAccount().virtualAccountId()).isEqualTo(latestAccount.getId());
        assertThat(payment.virtualAccount().accountNumber()).isEqualTo("LATEST-ACCOUNT");
        assertThat(payment.paymentHistory().paymentHistoryId()).isEqualTo(history.getId());
        assertThat(payment.paymentHistory().virtualAccountId()).isEqualTo(oldAccount.getId());
        assertThat(payment.paymentHistory().paidAt())
                .isEqualTo(OffsetDateTime.parse("2026-11-06T10:00:00+09:00"));
    }

    @Test
    @DisplayName("전액 입금 시 미수금은 0이고 수동 입금은 계좌 없이 조회된다")
    void getFullyPaidOrderWithManualPayment() {
        Payment deposit = savePayment(PaymentType.DEPOSIT, 5000L);
        deposit.paidManually();
        saveHistory(deposit, null);
        Payment balance = savePayment(PaymentType.BALANCE, 5001L);
        balance.paidByVirtualAccount();
        em.flush();
        em.clear();

        PaymentDto.OrderResponse response = paymentService.getByOrderId(order.getId());

        assertThat(response.paidAmount()).isEqualTo(10001);
        assertThat(response.balanceAmount()).isZero();
        assertThat(response.payments()).hasSize(2);
        assertThat(response.payments().getFirst().paymentMethod()).isEqualTo(PaymentMethod.MD);
        assertThat(response.payments().getFirst().virtualAccount()).isNull();
        assertThat(response.payments().getFirst().paymentHistory().virtualAccountId()).isNull();
    }

    @Test
    @DisplayName("대기·실패·취소·생성 상태 결제는 입금 내역이 있어도 누적 입금에서 제외한다")
    void excludeUnpaidStatuses() {
        Payment deposit = savePayment(PaymentType.DEPOSIT, 5000L);
        deposit.waitForDeposit();
        saveHistory(deposit, null);
        Payment balance = savePayment(PaymentType.BALANCE, 5001L);
        balance.fail();

        assertThat(paymentService.getByOrderId(order.getId()).paidAmount()).isZero();
        deposit.cancel();
        balance.retry();
        assertThat(paymentService.getByOrderId(order.getId()).paidAmount()).isZero();
        assertThat(paymentService.getByOrderId(order.getId()).balanceAmount()).isEqualTo(10001);
    }

    @Test
    @DisplayName("최신 계좌가 만료·취소 상태여도 예전 활성 계좌로 대체하지 않는다")
    void getLatestAccountRegardlessOfStatus() {
        Payment deposit = savePayment(PaymentType.DEPOSIT, 5000L);
        saveAccount(deposit, "OLD-ACTIVE");
        VirtualAccount latest = saveAccount(deposit, "LATEST-EXPIRED");
        latest.expire();

        PaymentDto.OrderResponse expired = paymentService.getByOrderId(order.getId());
        assertThat(expired.payments().getFirst().virtualAccount().virtualAccountId()).isEqualTo(latest.getId());
        assertThat(expired.payments().getFirst().virtualAccount().status()).isEqualTo(VirtualAccountStatus.EXPIRED);

        latest.cancel();
        PaymentDto.OrderResponse cancelled = paymentService.getByOrderId(order.getId());
        assertThat(cancelled.payments().getFirst().virtualAccount().status()).isEqualTo(VirtualAccountStatus.CANCELLED);
        assertThat(cancelled.payments().getFirst().paymentHistory()).isNull();
    }

    @Test
    @DisplayName("초과 입금 데이터의 미수금은 음수가 되지 않는다")
    void clampBalanceAtZero() {
        Payment deposit = savePayment(PaymentType.DEPOSIT, 11000L);
        deposit.paidByVirtualAccount();

        assertThat(paymentService.getByOrderId(order.getId()).balanceAmount()).isZero();
    }

    @Test
    @DisplayName("없는 수주는 조회 실패한다")
    void rejectUnknownOrder() {
        assertThatThrownBy(() -> paymentService.getByOrderId(Long.MAX_VALUE))
                .isInstanceOf(NoSuchElementException.class).hasMessage("수주를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("수주 ID가 없으면 조회 실패한다")
    void rejectMissingOrderId() {
        assertThatThrownBy(() -> paymentService.getByOrderId(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("수주 ID를 입력해 주세요.");
    }

    private Payment savePayment(PaymentType type, long amount) {
        return paymentRepository.save(Payment.builder().order(order).paymentType(type).amount(amount)
                .portonePaymentId("PAYMENT-QUERY-" + type).createdBy("test").build());
    }

    private VirtualAccount saveAccount(Payment payment, String accountNumber) {
        return virtualAccountRepository.save(VirtualAccount.builder().payment(payment).bank(Bank.SHINHAN)
                .accountNumber(accountNumber).accountName("테스트 계좌")
                .expiresAt(OffsetDateTime.of(LocalDateTime.of(2027, 1, 1, 0, 0),
                        ZoneOffset.of("+09:00"))).build());
    }

    private PaymentHistory saveHistory(Payment payment, VirtualAccount account) {
        return paymentHistoryRepository.save(PaymentHistory.builder().payment(payment).virtualAccount(account)
                .paidAt(OffsetDateTime.of(LocalDateTime.of(2026, 11, 6, 10, 0),
                        ZoneOffset.of("+09:00"))).build());
    }
}
