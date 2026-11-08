package net.likelion.bebc25.prodflow.orderNotification;

import net.likelion.bebc25.prodflow.order_notification.dto.OrderNotificationDto;
import net.likelion.bebc25.prodflow.order_notification.enums.OrderNotificationType;
import net.likelion.bebc25.prodflow.order_notification.repository.OrderNotificationRepository;
import net.likelion.bebc25.prodflow.order_notification.service.OrderNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class OrderNotificationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    @Autowired
    private OrderNotificationService orderNotificationService;

    @Autowired
    private OrderNotificationRepository orderNotificationRepository;

    @Test
    @DisplayName("납기 3일 이내 수주는 담당 직원에게 임박 알림을 만든다")
    void createsImminentNotificationForAssignee() {
        orderNotificationService.createDueNotifications(TODAY);

        assertThat(orderNotificationRepository.existsByCustomerOrder_IdAndNotificationType(
                9L,
                OrderNotificationType.DELIVERY_IMMINENT
        )).isTrue();

        OrderNotificationDto created = orderNotificationService.getNotifications(2L)
                .stream()
                .filter(item -> "PF-2026-009".equals(item.orderNo()))
                .findFirst()
                .orElseThrow();

        assertThat(created.notificationType())
                .isEqualTo(OrderNotificationType.DELIVERY_IMMINENT);
        assertThat(created.clientName()).isEqualTo("한빛케이스(주)");
        assertThat(created.isRead()).isFalse();

        assertThat(orderNotificationService.getNotifications(1L))
                .extracting(OrderNotificationDto::orderNo)
                .doesNotContain("PF-2026-009");
    }

    @Test
    @DisplayName("납기가 지난 미배송 수주는 지연 알림을 만든다")
    void createsDelayedNotification() {
        orderNotificationService.createDueNotifications(TODAY);

        assertThat(orderNotificationRepository.existsByCustomerOrder_IdAndNotificationType(
                1L,
                OrderNotificationType.DELIVERY_DELAYED
        )).isTrue();
    }

    @Test
    @DisplayName("취소 수주와 배송 완료 수주는 알림을 만들지 않는다")
    void excludesCancelledAndDeliveredOrders() {
        orderNotificationService.createDueNotifications(TODAY);

        assertThat(orderNotificationRepository.existsByCustomerOrder_IdAndNotificationType(
                11L,
                OrderNotificationType.DELIVERY_DELAYED
        )).isFalse();
        assertThat(orderNotificationRepository.existsByCustomerOrder_IdAndNotificationType(
                11L,
                OrderNotificationType.DELIVERY_IMMINENT
        )).isFalse();
        assertThat(orderNotificationRepository.existsByCustomerOrder_IdAndNotificationType(
                7L,
                OrderNotificationType.DELIVERY_DELAYED
        )).isFalse();
        assertThat(orderNotificationRepository.existsByCustomerOrder_IdAndNotificationType(
                7L,
                OrderNotificationType.DELIVERY_IMMINENT
        )).isFalse();
    }

    @Test
    @DisplayName("같은 수주와 유형의 알림은 다시 만들지 않는다")
    void doesNotDuplicateExistingNotification() {
        long before = orderNotificationRepository
                .countByTarget_IdAndReadAtIsNull(3L);

        orderNotificationService.createDueNotifications(TODAY);
        long once = orderNotificationRepository.count();
        orderNotificationService.createDueNotifications(TODAY);

        assertThat(orderNotificationRepository.count()).isEqualTo(once);
        assertThat(orderNotificationRepository.existsByCustomerOrder_IdAndNotificationType(
                3L,
                OrderNotificationType.DELIVERY_DELAYED
        )).isTrue();
        assertThat(orderNotificationRepository.countByTarget_IdAndReadAtIsNull(3L))
                .isEqualTo(before);
    }

    @Test
    @DisplayName("본인 납기 알림만 읽음 처리할 수 있다")
    void markAsReadOnlyOwner() {
        orderNotificationService.createDueNotifications(TODAY);

        OrderNotificationDto created = orderNotificationService.getNotifications(2L)
                .stream()
                .filter(item -> "PF-2026-009".equals(item.orderNo()))
                .findFirst()
                .orElseThrow();

        OrderNotificationDto read = orderNotificationService.markAsRead(
                created.notification(),
                2L
        );

        assertThat(read.isRead()).isTrue();
        assertThat(read.readAt()).isNotNull();

        assertThatThrownBy(() ->
                orderNotificationService.markAsRead(created.notification(), 3L)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알림을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("없는 납기 알림은 읽음 처리할 수 없다")
    void markAsReadMissingNotification() {
        assertThatThrownBy(() -> orderNotificationService.markAsRead(99999L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알림을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("읽지 않은 납기 알림 개수는 대상 사용자 알림만 센다")
    void unreadCountMatchesRepository() {
        orderNotificationService.createDueNotifications(TODAY);

        long expected = orderNotificationRepository.countByTarget_IdAndReadAtIsNull(2L);

        assertThat(orderNotificationService.getUnreadCount(2L)).isEqualTo(expected);
        assertThat(orderNotificationService.getNotifications(2L)).hasSize(
                (int) orderNotificationRepository
                        .findByTarget_IdOrderByCreatedAtDesc(2L)
                        .size()
        );
    }

    @Test
    @DisplayName("납기 알림은 담당 직원에게만 가고 생산 관리자에게는 가지 않는다")
    void notifiesAssigneeOnly() {
        // given
        LocalDate today = LocalDate.of(2026, 11, 4);

        // when
        orderNotificationService.createDueNotifications(today);

        // then
        assertThat(orderNotificationService.getNotifications(2L))
                .extracting(OrderNotificationDto::orderNo)
                .contains("PF-2026-009");

        for (Long managerId : List.of(4L, 5L, 6L, 8L)) {
            assertThat(orderNotificationService.getNotifications(managerId))
                    .extracting(OrderNotificationDto::orderNo)
                    .doesNotContain("PF-2026-009");
        }
    }

    @Test
    @DisplayName("임박 알림이 있는 수주는 납기가 지나면 지연 알림을 따로 만든다")
    void createsDelayedNotificationAfterImminent() {
        // given
        LocalDate imminentDay = LocalDate.of(2026, 11, 4);
        LocalDate delayedDay = LocalDate.of(2026, 11, 7);

        // when
        orderNotificationService.createDueNotifications(imminentDay);
        orderNotificationService.createDueNotifications(delayedDay);

        // then
        assertThat(orderNotificationRepository.existsByCustomerOrder_IdAndNotificationType(
                9L,
                OrderNotificationType.DELIVERY_IMMINENT
        )).isTrue();
        assertThat(orderNotificationRepository.existsByCustomerOrder_IdAndNotificationType(
                9L,
                OrderNotificationType.DELIVERY_DELAYED
        )).isTrue();
    }

    @Test
    @DisplayName("담당 수주의 납기 알림 목록을 최신순으로 조회한다")
    void getNotifications() {
        // given
        Long userId = 3L;

        // when
        List<OrderNotificationDto> notifications =
                orderNotificationService.getNotifications(userId);

        // then
        assertThat(notifications)
                .extracting(OrderNotificationDto::orderNo)
                .containsExactly("PF-2026-004", "PF-2026-012");
        assertThat(notifications)
                .extracting(OrderNotificationDto::createdAt)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    @DisplayName("알림이 없는 사용자는 빈 목록과 미읽음 0을 받는다")
    void getNotificationsWhenEmpty() {
        // given
        Long userId = 7L;

        // when
        List<OrderNotificationDto> notifications =
                orderNotificationService.getNotifications(userId);

        // then
        assertThat(notifications).isEmpty();
        assertThat(orderNotificationService.getUnreadCount(userId)).isZero();
    }
}
