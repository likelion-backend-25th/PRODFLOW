package net.likelion.bebc25.prodflow.client_notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import net.likelion.bebc25.prodflow.client_notification.enums.PaymentNotificationStatus;
import net.likelion.bebc25.prodflow.client_notification.enums.PaymentNotificationKind;
import org.hibernate.annotations.ColumnDefault;
import net.likelion.bebc25.prodflow.common.entity.BaseTimeEntity;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.user.entity.User;

@Entity
@Table(indexes = @Index(name = "idx_payment_notification_order_updated", columnList = "order_id, updated_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentNotificationRequest extends BaseTimeEntity {
    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'DEPOSIT'")
    private PaymentNotificationKind kind = PaymentNotificationKind.DEPOSIT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentNotificationStatus status;

    @Column(nullable = false, length = 500)
    private String message;

    public PaymentNotificationRequest(String id, CustomerOrder order, User user) {
        this.id = id;
        this.order = order;
        this.user = user;
        complete(PaymentNotificationStatus.PROCESSING, "가상계좌와 결제 안내를 준비하고 있습니다.");
    }

    public void complete(PaymentNotificationStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public static PaymentNotificationRequest shipment(String id, CustomerOrder order, User user) {
        PaymentNotificationRequest request = new PaymentNotificationRequest(id, order, user);
        request.kind = PaymentNotificationKind.SHIPMENT;
        request.complete(PaymentNotificationStatus.PROCESSING, "출고 및 잔금 안내를 준비하고 있습니다.");
        return request;
    }
}
