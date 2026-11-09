package net.likelion.bebc25.prodflow.lineNotification;

import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.line.repository.LineRepository;
import net.likelion.bebc25.prodflow.line_notification.dto.LineNotificationDto;
import net.likelion.bebc25.prodflow.line_notification.entity.LineNotification;
import net.likelion.bebc25.prodflow.line_notification.enums.LineNotificationType;
import net.likelion.bebc25.prodflow.line_notification.repository.LineNotificationRepository;
import net.likelion.bebc25.prodflow.line_notification.service.LineNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class LineNotificationServiceTest {

    @Autowired
    private LineNotificationService lineNotificationService;

    @Autowired
    private LineNotificationRepository lineNotificationRepository;

    @Autowired
    private LineRepository lineRepository;

    @Test
    @DisplayName("사용자의 알림 목록을 조회한다")
    void getNotification() {
        // given
        Long userId = 4L;

        // when
        List<LineNotificationDto> notifications =
                lineNotificationService.getNotification(userId);

        // then
        assertThat(notifications).isNotNull();
        assertThat(notifications).isNotEmpty();

        // 조회된 모든 알림의 수신자가 해당 사용자여야 한다
        List<LineNotification> userNotifications =
                lineNotificationRepository.findByTarget_IdOrderByCreatedAtDesc(userId);

        assertThat(notifications)
                .hasSameSizeAs(userNotifications);
    }

    @Test
    @DisplayName("읽지 않은 알림 개수를 조회한다")
    void getUnreadCount() {
        // given
        Long userId = 4L;

        long expectedCount =
                lineNotificationRepository.countByTarget_IdAndReadAtIsNull(userId);

        // when
        long unreadCount = lineNotificationService.getUnreadCount(userId);

        // then
        assertThat(unreadCount).isEqualTo(expectedCount);
    }

    @Test
    @DisplayName("알림을 읽음 처리한다")
    void markAsRead() {
        // given
        Long notificationId = 1L;
        Long userId = 6L;

        // when
        LineNotificationDto result =
                lineNotificationService.markAsRead(notificationId, userId);

        // then
        assertThat(result.notification()).isEqualTo(notificationId);
        assertThat(result.readAt()).isNotNull();
        assertThat(result.isRead()).isTrue();

        LineNotification notification =
                lineNotificationRepository.findById(notificationId)
                        .orElseThrow();

        assertThat(notification.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("다른 사용자의 알림은 읽음 처리할 수 없다")
    void markAsReadByOtherUser() {
        // given
        Long notificationId = 1L;
        Long otherUserId = 5L;

        // when & then
        assertThatThrownBy(() ->
                lineNotificationService.markAsRead(notificationId, otherUserId)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알림을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("생산라인 고장 상태 변경 시 활성 생산 현장 관리자들에게 알림을 생성한다")
    void notifyLineStatusChangedWhenBroken() {
        // given
        Long lineId = 1L;

        long beforeUser4 =
                lineNotificationRepository.countByTarget_IdAndReadAtIsNull(4L);
        long beforeUser5 =
                lineNotificationRepository.countByTarget_IdAndReadAtIsNull(5L);
        long beforeUser6 =
                lineNotificationRepository.countByTarget_IdAndReadAtIsNull(6L);

        // when
        lineNotificationService.notifyLineStatusChanged(
                lineId,
                LineStatus.BROKEN
        );

        // then
        assertThat(lineNotificationRepository
                .countByTarget_IdAndReadAtIsNull(4L))
                .isEqualTo(beforeUser4 + 1);

        assertThat(lineNotificationRepository
                .countByTarget_IdAndReadAtIsNull(5L))
                .isEqualTo(beforeUser5 + 1);

        assertThat(lineNotificationRepository
                .countByTarget_IdAndReadAtIsNull(6L))
                .isEqualTo(beforeUser6 + 1);

        // 새 알림의 유형과 메시지 확인
        for (Long userId : List.of(4L, 5L, 6L)) {
            List<LineNotification> notifications =
                    lineNotificationRepository
                            .findByTarget_IdOrderByCreatedAtDesc(userId);

            LineNotification latestNotification = notifications.get(0);

            assertThat(latestNotification.getNotificationType())
                    .isEqualTo(LineNotificationType.BROKEN);

            assertThat(latestNotification.getMessage())
                    .isEqualTo(statusMessage(lineId, "고장"));
        }
    }

    @Test
    @DisplayName("생산라인이 가동 가능 상태가 되면 AVAILABLE 알림을 생성한다")
    void notifyLineStatusChangedWhenAvailable() {
        // given
        Long lineId = 1L;
        long beforeCount =
                lineNotificationRepository.countByTarget_IdAndReadAtIsNull(4L);

        // when
        lineNotificationService.notifyLineStatusChanged(
                lineId,
                LineStatus.AVAILABLE
        );

        // then
        assertThat(lineNotificationRepository
                .countByTarget_IdAndReadAtIsNull(4L))
                .isEqualTo(beforeCount + 1);

        LineNotification latestNotification =
                lineNotificationRepository
                        .findByTarget_IdOrderByCreatedAtDesc(4L)
                        .get(0);

        assertThat(latestNotification.getNotificationType())
                .isEqualTo(LineNotificationType.AVAILABLE);

        assertThat(latestNotification.getMessage())
                .isEqualTo(statusMessage(lineId, "가동 가능"));
    }

    @Test
    @DisplayName("생산라인 점검 상태 변경 시 MAINTENANCE 알림을 생성한다")
    void notifyLineStatusChangedWhenInspection() {
        // given
        Long lineId = 1L;
        long beforeCount =
                lineNotificationRepository.countByTarget_IdAndReadAtIsNull(4L);

        // when
        lineNotificationService.notifyLineStatusChanged(
                lineId,
                LineStatus.INSPECTION
        );

        // then
        assertThat(lineNotificationRepository
                .countByTarget_IdAndReadAtIsNull(4L))
                .isEqualTo(beforeCount + 1);

        LineNotification latestNotification =
                lineNotificationRepository
                        .findByTarget_IdOrderByCreatedAtDesc(4L)
                        .get(0);

        assertThat(latestNotification.getNotificationType())
                .isEqualTo(LineNotificationType.MAINTENANCE);

        assertThat(latestNotification.getMessage())
                .isEqualTo(statusMessage(lineId, "점검"));
    }

    @Test
    @DisplayName("생산라인이 OPERATING 상태이면 알림을 생성하지 않는다")
    void notifyLineStatusChangedWhenOperating() {
        // given
        Long lineId = 1L;

        long beforeUser4 =
                lineNotificationRepository.countByTarget_IdAndReadAtIsNull(4L);
        long beforeUser5 =
                lineNotificationRepository.countByTarget_IdAndReadAtIsNull(5L);
        long beforeUser6 =
                lineNotificationRepository.countByTarget_IdAndReadAtIsNull(6L);

        // when
        lineNotificationService.notifyLineStatusChanged(
                lineId,
                LineStatus.OPERATING
        );

        // then
        assertThat(lineNotificationRepository
                .countByTarget_IdAndReadAtIsNull(4L))
                .isEqualTo(beforeUser4);

        assertThat(lineNotificationRepository
                .countByTarget_IdAndReadAtIsNull(5L))
                .isEqualTo(beforeUser5);

        assertThat(lineNotificationRepository
                .countByTarget_IdAndReadAtIsNull(6L))
                .isEqualTo(beforeUser6);
    }

    @Test
    @DisplayName("라인 상태 알림은 활성 최고관리자, 수주 관리자, 생산 관리자에게만 만든다")
    void notifyLineStatusChangedReachesStatusRolesOnly() {
        long beforeAdmin = lineNotificationRepository.countByTarget_IdAndReadAtIsNull(1L);
        long beforeOrderManager2 = lineNotificationRepository.countByTarget_IdAndReadAtIsNull(2L);
        long beforeOrderManager = lineNotificationRepository.countByTarget_IdAndReadAtIsNull(3L);
        long beforeProductionManager = lineNotificationRepository.countByTarget_IdAndReadAtIsNull(4L);
        long beforeInquiry = lineNotificationRepository.countByTarget_IdAndReadAtIsNull(7L);
        long beforeInactive = lineNotificationRepository.countByTarget_IdAndReadAtIsNull(8L);
        long beforeDeleted = lineNotificationRepository.countByTarget_IdAndReadAtIsNull(9L);

        lineNotificationService.notifyLineStatusChanged(1L, LineStatus.BROKEN);

        assertThat(lineNotificationRepository.countByTarget_IdAndReadAtIsNull(1L)).isEqualTo(beforeAdmin + 1);
        assertThat(lineNotificationRepository.countByTarget_IdAndReadAtIsNull(2L)).isEqualTo(beforeOrderManager2 + 1);
        assertThat(lineNotificationRepository.countByTarget_IdAndReadAtIsNull(3L)).isEqualTo(beforeOrderManager + 1);
        assertThat(lineNotificationRepository.countByTarget_IdAndReadAtIsNull(4L)).isEqualTo(beforeProductionManager + 1);
        assertThat(lineNotificationRepository.countByTarget_IdAndReadAtIsNull(7L)).isEqualTo(beforeInquiry);
        assertThat(lineNotificationRepository.countByTarget_IdAndReadAtIsNull(8L)).isEqualTo(beforeInactive);
        assertThat(lineNotificationRepository.countByTarget_IdAndReadAtIsNull(9L)).isEqualTo(beforeDeleted);
    }

    @Test
    @DisplayName("상태 알림 문구는 저장된 라인 이름을 한 번만 쓴다")
    void statusMessageUsesStoredLineNameOnce() {
        Line line = lineRepository.save(new Line("야간라인", 100));

        lineNotificationService.notifyLineStatusChanged(line.getId(), LineStatus.INSPECTION);

        LineNotification latestNotification = lineNotificationRepository
                .findByTarget_IdOrderByCreatedAtDesc(4L)
                .get(0);

        assertThat(latestNotification.getNotificationType()).isEqualTo(LineNotificationType.MAINTENANCE);
        assertThat(latestNotification.getMessage()).isEqualTo("야간라인이 점검 상태로 변경되었습니다.");
        assertThat(latestNotification.getMessage()).doesNotContain("라인 라인");
    }

    @Test
    @DisplayName("없는 생산라인의 상태 알림은 만들지 않는다")
    void notifyLineStatusChangedWhenLineMissing() {
        assertThatThrownBy(() ->
                lineNotificationService.notifyLineStatusChanged(99999L, LineStatus.BROKEN)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("생산라인을 찾을 수 없습니다.");
    }

    private String statusMessage(Long lineId, String statusLabel) {
        String lineName = lineRepository.findById(lineId).orElseThrow().getLineName();
        return lineName + "이 " + statusLabel + " 상태로 변경되었습니다.";
    }
}
