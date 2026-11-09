package net.likelion.bebc25.prodflow.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.likelion.bebc25.prodflow.common.entity.BaseTimeEntity;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.payment.enums.PaymentMethod;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;

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

