package net.likelion.bebc25.prodflow.line_notification.dto;

import net.likelion.bebc25.prodflow.line_notification.entity.LineNotification;
import net.likelion.bebc25.prodflow.line_notification.enums.LineNotificationType;

import java.time.LocalDateTime;

public record LineNotificationDto(
        Long notification,
        Long lineId,
        String lineName,
        LineNotificationType notificationType,
        String message,
        LocalDateTime createdAt,
        LocalDateTime readAt,
        boolean isRead
) {
    public static LineNotificationDto from(LineNotification lineNotification) {
        return new LineNotificationDto(
                lineNotification.getId(),
                lineNotification.getProductionLine() == null
                        ? null
                        : lineNotification.getProductionLine().getId(),
                lineNotification.getProductionLine() == null
                        ? null
                        : lineNotification.getProductionLine().getLineName(),
                lineNotification.getNotificationType(),
                lineNotification.getMessage(),
                lineNotification.getCreatedAt(),
                lineNotification.getReadAt(),
                lineNotification.getReadAt() != null
        );
    }
}
