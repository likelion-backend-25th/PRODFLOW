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

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 생산라인 상태 알림과 생산라인 연결
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id", nullable = false)
    private Line productionLine;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private LineNotificationType notificationType;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public LineNotification(
            Line productionLine,
            LineNotificationType notificationType,
            String message
    ) {
        this.productionLine = productionLine;
        this.notificationType = notificationType;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }
}