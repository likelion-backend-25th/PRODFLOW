package net.likelion.bebc25.prodflow.notification.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.notification.enums.NotificationType;
import net.likelion.bebc25.prodflow.productionline.entity.Line;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 납기 관련 알림의 수주 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_order_id")
    private CustomerOrder customerOrder;

    // 생산라인 관련 알림의 생산라인 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_id")
    private Line productionLine;

    // 알림 유형
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    // 알림 메시지
    @Column(name = "message", nullable = false, length = 500)
    private String message;

    // 알림 생성 시각
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Notification(
            CustomerOrder customerOrder,
            Line productionLine,
            NotificationType notificationType,
            String message
    ) {
        this.customerOrder = customerOrder;
        this.productionLine = productionLine;
        this.notificationType = notificationType;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }
}
