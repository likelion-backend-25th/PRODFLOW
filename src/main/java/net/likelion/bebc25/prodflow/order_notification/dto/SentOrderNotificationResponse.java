package net.likelion.bebc25.prodflow.order_notification.dto;

import net.likelion.bebc25.prodflow.order_notification.enums.OrderNotificationType;

public record SentOrderNotificationResponse(
        Long orderId,
        OrderNotificationType notificationType
) {
}
