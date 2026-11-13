package net.likelion.bebc25.prodflow.orderNotification;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.order_notification.entity.OrderNotification;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

import net.likelion.bebc25.prodflow.order_notification.dto.OrderDueNotificationResponse;
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

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    @Autowired
    private OrderNotificationService orderNotificationService;

    @Autowired
    private OrderNotificationRepository orderNotificationRepository;

    @Autowired
    private EntityManager em;

    @BeforeEach
    void setUpNotifications() {
        // 시드 알림의 수신자·건수·생성일이 생성/읽음 처리 검증에 영향을 주지 않게 한다.
        orderNotificationRepository.deleteAllInBatch();
        em.createQuery("update CustomerOrder o set o.requestedDueDate = :date where o.id = 9")
                .setParameter("date", TODAY.plusDays(2)).executeUpdate();
        em.clear();
    }

    @Test
    @DisplayName("납기 3일 이내 수주는 담당 직원에게 임박 알림을 만든다")
    void createsImminentNotificationForAssignee() {
        orderNotificationService.createDueNotifications(TODAY);

        assertThat(orderNotificationRepository.existsByCustomerOrder_IdAndNotificationType(
                9L,
                OrderNotificationType.DELIVERY_IMMINENT
        )).isTrue();

        OrderNotificationDto created = orderNotificationService.getNotifications(3L)
                .stream()
                .filter(item -> "PF-20261106-9".equals(item.orderNo()))
                .findFirst()
                .orElseThrow();

        assertThat(created.notificationType())
                .isEqualTo(OrderNotificationType.DELIVERY_IMMINENT);
        assertThat(created.clientName()).isEqualTo("한빛케이스(주)");
        assertThat(created.isRead()).isFalse();

        // 담당자가 아닌 최고관리자도 같은 알림을 받는다. 다른 수주 관리자에게는 가지 않는다.
        assertThat(orderNotificationService.getNotifications(1L))
                .extracting(OrderNotificationDto::orderNo)
                .contains("PF-20261106-9");
        assertThat(orderNotificationService.getNotifications(2L))
                .extracting(OrderNotificationDto::orderNo)
                .doesNotContain("PF-20261106-9");
    }

    @Test
    @DisplayName("납기가 지난 미배송 수주는 지연 알림을 만든다")
    void createsDelayedNotification() {
        orderNotificationService.createDueNotifications(TODAY);

        assertThat(orderNotificationRepository.existsByCustomerOrder_IdAndNotificationType(
                3L,
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
        long unreadOnce = orderNotificationRepository.countByTarget_IdAndReadAtIsNull(3L);
        orderNotificationService.createDueNotifications(TODAY);

        assertThat(orderNotificationRepository.count()).isEqualTo(once);
        assertThat(orderNotificationRepository.existsByCustomerOrder_IdAndNotificationType(
                3L,
                OrderNotificationType.DELIVERY_DELAYED
        )).isTrue();
        assertThat(orderNotificationRepository.countByTarget_IdAndReadAtIsNull(3L))
                .isEqualTo(unreadOnce);
        assertThat(unreadOnce).isGreaterThan(before);
    }

    @Test
    @DisplayName("본인 납기 알림만 읽음 처리할 수 있다")
    void markAsReadOnlyOwner() {
        orderNotificationService.createDueNotifications(TODAY);

        OrderNotificationDto created = orderNotificationService.getNotifications(3L)
                .stream()
                .filter(item -> "PF-20261106-9".equals(item.orderNo()))
                .findFirst()
                .orElseThrow();

        OrderNotificationDto read = orderNotificationService.markAsRead(
                created.notification(),
                3L
        );

        assertThat(read.isRead()).isTrue();
        assertThat(read.readAt()).isNotNull();

        assertThatThrownBy(() ->
                orderNotificationService.markAsRead(created.notification(), 1L)
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

        long expected = orderNotificationRepository.countByTarget_IdAndReadAtIsNull(3L);

        assertThat(orderNotificationService.getUnreadCount(3L)).isEqualTo(expected);
        assertThat(orderNotificationService.getNotifications(3L)).hasSize(
                (int) orderNotificationRepository
                        .findByTarget_IdOrderByCreatedAtDesc(3L)
                        .size()
        );
    }

    @Test
    @DisplayName("납기 알림은 생산 현장 관리자에게 가지 않는다")
    void notifiesAssigneeOnly() {
        // given
        LocalDate today = LocalDate.of(2026, 11, 9);

        // when
        orderNotificationService.createDueNotifications(today);

        // then
        assertThat(orderNotificationService.getNotifications(3L))
                .extracting(OrderNotificationDto::orderNo)
                .contains("PF-20261106-9");

        for (Long managerId : List.of(4L, 5L, 6L, 8L)) {
            assertThat(orderNotificationService.getNotifications(managerId))
                    .extracting(OrderNotificationDto::orderNo)
                    .doesNotContain("PF-20261106-9");
        }
    }

    @Test
    @DisplayName("임박 알림이 있는 수주는 납기가 지나면 지연 알림을 따로 만든다")
    void createsDelayedNotificationAfterImminent() {
        // given
        LocalDate imminentDay = TODAY;
        LocalDate delayedDay = TODAY.plusDays(3);

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
        User recipient = em.find(User.class, 3L);
        OrderNotification earlier = new OrderNotification(
                em.find(CustomerOrder.class, 3L), recipient,
                OrderNotificationType.DELIVERY_DELAYED, "이전 지연 알림");
        OrderNotification later = new OrderNotification(
                em.find(CustomerOrder.class, 9L), recipient,
                OrderNotificationType.DELIVERY_IMMINENT, "최근 임박 알림");
        ReflectionTestUtils.setField(earlier, "createdAt", TODAY.atTime(8, 0));
        ReflectionTestUtils.setField(later, "createdAt", TODAY.atTime(9, 0));
        orderNotificationRepository.saveAll(List.of(earlier, later));

        List<OrderNotificationDto> notifications = orderNotificationService.getNotifications(3L);

        assertThat(notifications)
                .extracting(OrderNotificationDto::orderNo)
                .containsExactly("PF-20261106-9", "PF-20261027-3");
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

    @Test
    @DisplayName("선택한 납기 임박 수주는 담당 직원에게만 알림을 만든다")
    void createDueNotificationImminent() {
        makeUndeliveredDue(6L, TODAY.plusDays(2));
        OrderDueNotificationResponse created =
                orderNotificationService.createDueNotification(6L, TODAY);

        assertThat(created.created()).isTrue();
        assertThat(created.notificationType()).isEqualTo(OrderNotificationType.DELIVERY_IMMINENT);
        assertThat(orderNotificationService.getNotifications(1L))
                .extracting(OrderNotificationDto::orderNo)
                .contains("PF-20261030-6");
        assertThat(orderNotificationService.getNotifications(3L))
                .extracting(OrderNotificationDto::orderNo)
                .doesNotContain("PF-20261030-6");
    }

    @Test
    @DisplayName("선택한 납기 지연 수주는 지연 알림을 만든다")
    void createDueNotificationDelayed() {
        OrderDueNotificationResponse created =
                orderNotificationService.createDueNotification(3L, TODAY);

        assertThat(created.created()).isTrue();
        assertThat(created.notificationType()).isEqualTo(OrderNotificationType.DELIVERY_DELAYED);
        assertThat(orderNotificationService.getNotifications(3L))
                .filteredOn(item -> "PF-20261027-3".equals(item.orderNo()))
                .extracting(OrderNotificationDto::message)
                .contains("PF-20261027-3 납기 2026-10-20가 지났습니다.");
    }

    @Test
    @DisplayName("같은 수주와 유형의 알림이 있으면 다시 만들지 않는다")
    void createDueNotificationDoesNotDuplicate() {
        makeUndeliveredDue(6L, TODAY.plusDays(2));
        orderNotificationService.createDueNotification(6L, TODAY);
        long once = orderNotificationRepository.count();

        OrderDueNotificationResponse again =
                orderNotificationService.createDueNotification(6L, TODAY);

        assertThat(again.created()).isFalse();
        assertThat(again.notificationType()).isEqualTo(OrderNotificationType.DELIVERY_IMMINENT);
        assertThat(orderNotificationRepository.count()).isEqualTo(once);
    }

    @Test
    @DisplayName("자동 생성된 알림이 있으면 전송 완료로 조회된다")
    void sentListIncludesAutomaticNotification() {
        orderNotificationService.createDueNotifications(TODAY);

        assertThat(orderNotificationService.listSentDueNotifications(1L))
                .anySatisfy(item -> {
                    assertThat(item.orderId()).isEqualTo(3L);
                    assertThat(item.notificationType()).isEqualTo(OrderNotificationType.DELIVERY_DELAYED);
                })
                .anySatisfy(item -> {
                    assertThat(item.orderId()).isEqualTo(9L);
                    assertThat(item.notificationType()).isEqualTo(OrderNotificationType.DELIVERY_IMMINENT);
                });
    }

    @Test
    @DisplayName("없는 수주와 납기 위험이 아닌 수주는 알림을 만들지 않는다")
    void createDueNotificationRejectsInvalidOrder() {
        assertThatThrownBy(() -> orderNotificationService.createDueNotification(99999L, TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수주를 찾을 수 없습니다.");
        assertThatThrownBy(() -> orderNotificationService.createDueNotification(11L, TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("납기 위험 수주가 아니어서 알림을 보내지 않습니다.");
        assertThatThrownBy(() -> orderNotificationService.createDueNotification(7L, TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("납기 위험 수주가 아니어서 알림을 보내지 않습니다.");
        assertThatThrownBy(() -> orderNotificationService.createDueNotification(1L, TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("납기 위험 수주가 아니어서 알림을 보내지 않습니다.");
        assertThat(orderNotificationRepository.count()).isZero();
    }

    @Test
    @DisplayName("납기 임박 알림은 담당 직원과 활성 최고관리자에게 가고 다른 권한에는 가지 않는다")
    void imminentNoticeReachesAssigneeAndActiveAdmins() {
        User admin = em.find(User.class, 13L);
        assertThat(admin.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);

        OrderDueNotificationResponse created =
                orderNotificationService.createDueNotification(9L, TODAY);

        assertThat(created.created()).isTrue();
        assertThat(created.notificationType()).isEqualTo(OrderNotificationType.DELIVERY_IMMINENT);
        assertThat(orderNotificationService.getNotifications(3L))
                .filteredOn(item -> "PF-20261106-9".equals(item.orderNo()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.notificationType()).isEqualTo(OrderNotificationType.DELIVERY_IMMINENT);
                    assertThat(item.orderId()).isEqualTo(9L);
                    assertThat(item.message()).contains("PF-20261106-9");
                    assertThat(item.isRead()).isFalse();
                    assertThat(item.createdAt()).isNotNull();
                });
        assertThat(orderNotificationService.getNotifications(13L))
                .extracting(OrderNotificationDto::orderNo)
                .contains("PF-20261106-9");
        assertThat(orderNotificationService.getUnreadCount(13L)).isPositive();
        for (Long otherId : List.of(2L, 4L, 7L)) {
            assertThat(orderNotificationService.getNotifications(otherId))
                    .extracting(OrderNotificationDto::orderNo)
                    .doesNotContain("PF-20261106-9");
        }
    }

    @Test
    @DisplayName("납기 지연 알림은 담당 직원과 활성 최고관리자의 목록과 미확인 수에 반영된다")
    void delayedNoticeReachesAssigneeAndActiveAdmins() {
        long unreadBefore = orderNotificationService.getUnreadCount(13L);

        OrderDueNotificationResponse created =
                orderNotificationService.createDueNotification(3L, TODAY);

        assertThat(created.created()).isTrue();
        assertThat(created.notificationType()).isEqualTo(OrderNotificationType.DELIVERY_DELAYED);
        assertThat(orderNotificationService.getNotifications(3L))
                .filteredOn(item -> "PF-20261027-3".equals(item.orderNo()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.message()).contains("PF-20261027-3").contains("2026-10-20");
                    assertThat(item.isRead()).isFalse();
                });
        assertThat(orderNotificationService.getNotifications(13L))
                .extracting(OrderNotificationDto::orderNo)
                .contains("PF-20261027-3");
        assertThat(orderNotificationService.getUnreadCount(13L)).isEqualTo(unreadBefore + 1);
        assertThat(orderNotificationService.listSentDueNotifications(13L))
                .filteredOn(item -> item.orderId().equals(3L)
                        && item.notificationType() == OrderNotificationType.DELIVERY_DELAYED)
                .hasSize(1);
    }

    @Test
    @DisplayName("담당 직원이 최고관리자이면 같은 유형의 알림은 한 건만 만든다")
    void assigneeWhoIsAdminReceivesOneNotification() {
        makeUndeliveredDue(6L, TODAY.plusDays(2));
        orderNotificationService.createDueNotification(6L, TODAY);

        assertThat(orderNotificationService.getNotifications(1L))
                .filteredOn(item -> "PF-20261030-6".equals(item.orderNo())
                        && item.notificationType() == OrderNotificationType.DELIVERY_IMMINENT)
                .hasSize(1);

        long recipients = orderNotificationRepository.findAll().stream()
                .filter(item -> item.getCustomerOrder() != null
                        && Long.valueOf(6L).equals(item.getCustomerOrder().getId())
                        && item.getNotificationType() == OrderNotificationType.DELIVERY_IMMINENT)
                .map(item -> item.getTarget().getId())
                .distinct()
                .count();
        long activeAdmins = em.createQuery(
                        "select count(u) from User u where u.role = :role and u.status = :status",
                        Long.class)
                .setParameter("role", UserRole.SUPER_ADMIN)
                .setParameter("status", UserStatus.ACTIVE)
                .getSingleResult();
        assertThat(recipients).isEqualTo(activeAdmins);
    }

    @Test
    @DisplayName("비활성 최고관리자에게는 납기 알림을 만들지 않는다")
    void inactiveAdminDoesNotReceiveDueNotification() {
        em.find(User.class, 15L).deactivate();
        em.flush();

        orderNotificationService.createDueNotification(3L, TODAY);

        assertThat(orderNotificationService.getNotifications(15L))
                .extracting(OrderNotificationDto::orderNo)
                .doesNotContain("PF-20261027-3");
        assertThat(orderNotificationService.getNotifications(13L))
                .extracting(OrderNotificationDto::orderNo)
                .contains("PF-20261027-3");
        assertThat(orderNotificationService.getNotifications(4L))
                .extracting(OrderNotificationDto::orderNo)
                .doesNotContain("PF-20261027-3");
    }

    private void makeUndeliveredDue(long orderId, LocalDate dueDate) {
        em.createQuery("update CustomerOrder o set o.requestedDueDate = :date where o.id = :id")
                .setParameter("date", dueDate)
                .setParameter("id", orderId)
                .executeUpdate();
        em.createQuery("update Shipment s set s.deliveredAt = null where s.orderId = :id")
                .setParameter("id", orderId)
                .executeUpdate();
        em.clear();
    }
}
