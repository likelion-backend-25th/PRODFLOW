package net.likelion.bebc25.prodflow.line_notification.controller;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.line_notification.dto.LineNotificationDto;
import net.likelion.bebc25.prodflow.line_notification.service.LineNotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class LineNotificationController {

    private final LineNotificationService lineNotificationService;

    // 알림 목록 조회
    @GetMapping
    public List<LineNotificationDto> getNotifications(
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return lineNotificationService.getNotification(userId);
    }

    // 읽지 않은 알림 개수 조회
    @GetMapping("/unread-count")
    public long getUnreadCount(
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return lineNotificationService.getUnreadCount(userId);
    }

    // 알림 읽음 처리
    @PatchMapping("/{notificationId}/read")
    public LineNotificationDto markAsRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return lineNotificationService.markAsRead(notificationId, userId);
    }

}
