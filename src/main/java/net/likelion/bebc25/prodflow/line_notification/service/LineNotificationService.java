package net.likelion.bebc25.prodflow.line_notification.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.line.repsitory.LineRepository;
import net.likelion.bebc25.prodflow.line_notification.dto.LineNotificationDto;
import net.likelion.bebc25.prodflow.line_notification.entity.LineNotification;
import net.likelion.bebc25.prodflow.line_notification.enums.LineNotificationType;
import net.likelion.bebc25.prodflow.line_notification.repository.LineNotificationRepository;
import net.likelion.bebc25.prodflow.line_notification.repository.NotificationUserRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LineNotificationService {

    private final LineNotificationRepository lineNotificationRepository;
    private final LineRepository lineRepository;
    private final UserRepository userRepository;
    private final NotificationUserRepository notificationUserRepository;

    // 알림 목록 조회
    public List<LineNotificationDto> getNotification(Long userId) {
        return lineNotificationRepository
                .findByTarget_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(LineNotificationDto::from)
                .toList();
    }

    // 읽지 않은 알림 수 조회
    public long getUnreadCount(Long userId) {
        return lineNotificationRepository.countByTarget_IdAndReadAtIsNull(userId);
    }

    // 알림 읽음 처리
    @Transactional
    public LineNotificationDto markAsRead(Long notificationId, Long userId) {
        LineNotification notification = lineNotificationRepository
                .findByIdAndTarget_Id(notificationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다."));

        notification.markAsRead();

        return LineNotificationDto.from(notification);
    }

    // 생산라인 상태 변경 알림 생성
    @Transactional
    public void createNotification(
            Long lineId,
            Long targetUserId,
            LineNotificationType lineNotificationType,
            String message
    ) {
        Line line = lineRepository.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("생산라인을 찾을 수 없습니다."));

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("알림 대상 사용자를 찾을 수 없습니다."));

        LineNotification notification = new LineNotification(
                line,
                target,
                lineNotificationType,
                message
        );

        lineNotificationRepository.save(notification);
    }

    // 생산현장관리자 전체에게 알림 생성
    @Transactional
    public void notificationProductionManagers(
            Long lineId,
            LineNotificationType notificationType,
            String message
    ) {
        Line line = lineRepository.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("생산라인을 찾을 수 없습니다."));

        List<User> managers = notificationUserRepository.findByRoleAndStatus(
                UserRole.PRODUCTION_MANAGER,
                UserStatus.ACTIVE
        );

        for (User manager : managers) {
            LineNotification notification = new LineNotification(
                    line,
                    manager,
                    notificationType,
                    message
            );

            lineNotificationRepository.save(notification);
        }
    }

    // 생산라인 상태 변경 시 생산현장관리자 전체에게 알림 생성
    @Transactional
    public void notifyLineStatusChanged(Long lineId, LineStatus status) {
        LineNotificationType notificationType;
        String message;

        Line line = lineRepository.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("생산라인을 찾을 수 없습니다."));

        switch (status) {
            case AVAILABLE -> {
                notificationType = LineNotificationType.AVAILABLE;
                message = line.getLineName() + " 라인이 가동 가능 상태로 변경되었습니다.";
            }
            case INSPECTION -> {
                notificationType = LineNotificationType.MAINTENANCE;
                message = line.getLineName() + " 라인이 점검 상태로 변경되었습니다.";
            }
            case BROKEN -> {
                notificationType = LineNotificationType.BROKEN;
                message = line.getLineName() + " 라인이 고장 상태로 변경되었습니다.";
            }
            default -> {
                return;
            }
        }

        notificationProductionManagers(lineId, notificationType, message);
    }

}
