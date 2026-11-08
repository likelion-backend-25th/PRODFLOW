package net.likelion.bebc25.prodflow.order_notification.dto;

import net.likelion.bebc25.prodflow.order_notification.entity.OrderNotification;
import net.likelion.bebc25.prodflow.order_notification.enums.OrderNotificationType;

import java.time.LocalDateTime;

public record OrderNotificationDto(
        Long notification,
        Long orderId,
        String orderNo,
        String clientName,
        OrderNotificationType notificationType,
        String message,
        LocalDateTime createdAt,
        LocalDateTime readAt,
        boolean isRead
) {
    public static OrderNotificationDto from(OrderNotification notification) {
        return new OrderNotificationDto(
                notification.getId(),
                notification.getCustomerOrder() == null
                        ? null
                        : notification.getCustomerOrder().getId(),
                notification.getCustomerOrder() == null
                        ? null
                        : notification.getCustomerOrder().getOrderNo(),
                notification.getCustomerOrder() == null
                        || notification.getCustomerOrder().getClient() == null
                        ? null
                        : notification.getCustomerOrder().getClient().getCompany(),
                notification.getNotificationType(),
                notification.getMessage(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.getReadAt() != null
        );
    }
}
