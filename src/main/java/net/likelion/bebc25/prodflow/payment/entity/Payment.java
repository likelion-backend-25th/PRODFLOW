package net.likelion.bebc25.prodflow.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.likelion.bebc25.prodflow.common.entity.BaseTimeEntity;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.payment.enums.PaymentMethod;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_order_type",
                        columnNames = {"order_id", "payment_type"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "order_id",
            nullable = false)
    private CustomerOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod = PaymentMethod.VA;

    @Column(nullable = false)
    private String portonePaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType paymentType;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.READY;

    // 재발급 요청의 새 입금기한. 원래 수주의 계약금 납부일이 지났어도 재발급할 수 있다.
    @Column(name = "reissue_due_at")
    private OffsetDateTime reissueDueAt;

    // PortOne 말소 응답 또는 말소 완료 상태 조회로 확인한 시각.
    // 이 값이 없으면 절대로 새 PortOne 결제 ID로 전환하지 않는다.
    @Column(name = "reissue_closed_at")
    private OffsetDateTime reissueClosedAt;

    @Builder
    private Payment(
            CustomerOrder order,
            String portonePaymentId,
            PaymentType paymentType,
            Long amount
    ) {
        this.order = order;
        this.portonePaymentId = portonePaymentId;
        this.paymentType = paymentType;
        this.amount = amount;
        this.status = PaymentStatus.READY;
    }

    // 외부 API를 호출하기 전 발급 의도를 DB에 먼저 확정한다.
    public void requestIssue() {
        if (this.status != PaymentStatus.READY) {
            throw new IllegalStateException("발급 준비 상태인 결제만 발급 요청할 수 있습니다.");
        }
        this.status = PaymentStatus.REQUESTED;
    }

    public void issueUncertain() {
        if (this.status == PaymentStatus.REQUESTED) {
            this.status = PaymentStatus.ISSUE_UNCERTAIN;
        }
    }

    // 가상계좌 발급 성공
    public void waitForDeposit() {
        this.status = PaymentStatus.WAITING;
    }

    // 입금 완료
    public void paidByVirtualAccount() {
        this.status = PaymentStatus.PAID;
    }

    // 가상계좌 발급 또는 결제 처리 실패
    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    // 결제건 취소
    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
    }

    public void beginReissue(OffsetDateTime dueAt) {
        if ((status != PaymentStatus.WAITING
                && status != PaymentStatus.CANCELLED
                && status != PaymentStatus.FAILED)
                || paymentMethod != PaymentMethod.VA) {
            throw new IllegalStateException("미입금 가상계좌 결제만 재발급할 수 있습니다.");
        }
        this.status = PaymentStatus.REISSUE_CLOSING;
        this.reissueDueAt = dueAt;
        this.reissueClosedAt = null;
    }

    public void confirmReissueClosed(OffsetDateTime closedAt) {
        if (status != PaymentStatus.REISSUE_CLOSING || closedAt == null) {
            throw new IllegalStateException("재발급할 이전 가상계좌의 말소를 확인하지 못했습니다.");
        }
        this.reissueClosedAt = closedAt;
    }

    public void switchToNewPortonePaymentId(String newId) {
        if (status != PaymentStatus.REISSUE_CLOSING || reissueClosedAt == null
                || newId == null || newId.isBlank() || newId.equals(portonePaymentId)) {
            throw new IllegalStateException("이전 계좌 말소가 확정되지 않아 새로운 결제를 생성할 수 없습니다.");
        }
        this.portonePaymentId = newId;
        this.status = PaymentStatus.READY;
        this.reissueClosedAt = null;
        // reissueDueAt은 새 계좌 발급 때 사용할 때까지 보존한다.
    }

    // 실패 후 가상계좌 발급 재시도
    public void retry() {
        this.status = PaymentStatus.READY;
    }

    // 수동입금 완료
    public void paidManually() {

        this.status = PaymentStatus.PAID;
        this.paymentMethod = PaymentMethod.MD;
    }

    public void updateStatus(PaymentStatus status) {
        this.status = status;
    }


}

