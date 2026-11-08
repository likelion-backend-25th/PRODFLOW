package net.likelion.bebc25.prodflow.order_notification.controller;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.order_notification.dto.OrderNotificationDto;
import net.likelion.bebc25.prodflow.order_notification.service.OrderNotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/order-notifications")
@RequiredArgsConstructor
public class OrderNotificationController {

    private final OrderNotificationService orderNotificationService;

    @GetMapping
    public List<OrderNotificationDto> getNotifications(
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return orderNotificationService.getNotifications(userId);
    }

    @GetMapping("/unread-count")
    public long getUnreadCount(
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return orderNotificationService.getUnreadCount(userId);
    }

    @PatchMapping("/{notificationId}/read")
    public OrderNotificationDto markAsRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return orderNotificationService.markAsRead(notificationId, userId);
    }
}
