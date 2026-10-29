package net.likelion.bebc25.prodflow.line_notification.entity;

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
import net.likelion.bebc25.prodflow.line_notification.enums.LineNotificationType;
import net.likelion.bebc25.prodflow.productionline.entity.Line;
import net.likelion.bebc25.prodflow.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_id", nullable = true)
    private Line productionLine;

    // 알림 수신자: 생산 현장 관리자
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    private User target;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private LineNotificationType notificationType;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    // 읽지 않았으면 null
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public LineNotification(
            Line productionLine,
            User target,
            LineNotificationType notificationType,
            String message
    ) {
        this.productionLine = productionLine;
        this.target = target;
        this.notificationType = notificationType;
        this.message = message;
        this.createdAt = LocalDateTime.now();
        this.readAt = null;
    }

    // 알림을 읽었을 때 호출
    public void markAsRead() {
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }
}
