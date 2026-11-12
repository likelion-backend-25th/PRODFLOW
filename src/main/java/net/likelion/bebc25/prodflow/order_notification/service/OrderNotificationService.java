package net.likelion.bebc25.prodflow.order_notification.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.order_notification.dto.OrderDueNotificationResponse;
import net.likelion.bebc25.prodflow.order_notification.dto.OrderNotificationDto;
import net.likelion.bebc25.prodflow.order_notification.dto.SentOrderNotificationResponse;
import net.likelion.bebc25.prodflow.order_notification.entity.OrderNotification;
import net.likelion.bebc25.prodflow.order_notification.enums.OrderNotificationType;
import net.likelion.bebc25.prodflow.order_notification.repository.OrderNotificationRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderNotificationService {

    private static final int IMMINENT_DAYS = 3;

    private final OrderNotificationRepository orderNotificationRepository;
    private final UserRepository userRepository;

    public List<OrderNotificationDto> getNotifications(Long userId) {
        return orderNotificationRepository
                .findByTarget_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(OrderNotificationDto::from)
                .toList();
    }

    public long getUnreadCount(Long userId) {
        return orderNotificationRepository.countByTarget_IdAndReadAtIsNull(userId);
    }

    @Transactional
    public OrderNotificationDto markAsRead(Long notificationId, Long userId) {
        OrderNotification notification = orderNotificationRepository
                .findByIdAndTarget_Id(notificationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다."));

        notification.markAsRead();
        return OrderNotificationDto.from(notification);
    }

    // 납기가 지났거나 3일 이내인 수주를 담당 직원과 활성 최고관리자에게 알린다. 수신자마다 한 번만 만든다.
    @Transactional
    public int createDueNotifications(LocalDate today) {
        LocalDate riskDate = today.plusDays(IMMINENT_DAYS);
        List<CustomerOrder> orders =
                orderNotificationRepository.findDueNotificationCandidates(riskDate);

        int created = 0;
        for (CustomerOrder order : orders) {
            created += saveDueNotifications(order, today);
        }
        return created;
    }

    // 전송 완료는 로그인한 수신자 기준이다. 담당자에게만 있는 알림 때문에 최고관리자 버튼이 사라지지 않는다.
    public List<SentOrderNotificationResponse> listSentDueNotifications(Long userId) {
        return orderNotificationRepository.findSentDueNotifications(userId, List.of(
                OrderNotificationType.DELIVERY_DELAYED,
                OrderNotificationType.DELIVERY_IMMINENT
        ));
    }

    // 테이블에서 고른 수주만 알린다. 위험 기준과 중복 방지는 전체 생성과 같다.
    @Transactional
    public OrderDueNotificationResponse createDueNotification(Long orderId, LocalDate today) {
        CustomerOrder loaded = orderNotificationRepository.findOrderForNotification(orderId)
                .orElseThrow(() -> new IllegalArgumentException("수주를 찾을 수 없습니다."));
        if (loaded.getUser() == null) {
            throw new IllegalArgumentException("담당 직원이 없습니다.");
        }

        LocalDate riskDate = today.plusDays(IMMINENT_DAYS);
        CustomerOrder order = orderNotificationRepository
                .findDueNotificationCandidate(orderId, riskDate)
                .orElseThrow(() -> new IllegalArgumentException("납기 위험 수주가 아니어서 알림을 보내지 않습니다."));

        OrderNotificationType type = resolveType(order, today);
        boolean created = saveDueNotifications(order, today) > 0;
        return new OrderDueNotificationResponse(order.getId(), type, created);
    }

    // 담당 직원이 최고관리자이면 같은 알림을 두 번 만들지 않는다. 비활성 최고관리자는 추가 수신자에서 뺀다.
    private List<User> dueNotificationTargets(CustomerOrder order) {
        Map<Long, User> targets = new LinkedHashMap<>();
        User assignee = order.getUser();
        if (assignee != null) {
            targets.put(assignee.getId(), assignee);
        }
        for (User admin : userRepository.findByRole(UserRole.SUPER_ADMIN)) {
            if (admin.getStatus() != UserStatus.ACTIVE || admin.getId() == null) {
                continue;
            }
            targets.putIfAbsent(admin.getId(), admin);
        }
        return List.copyOf(targets.values());
    }

    private int saveDueNotifications(CustomerOrder order, LocalDate today) {
        OrderNotificationType type = resolveType(order, today);
        String text = message(order, today, type);
        int created = 0;
        for (User target : dueNotificationTargets(order)) {
            if (orderNotificationRepository.existsByCustomerOrder_IdAndNotificationTypeAndTarget_Id(
                    order.getId(),
                    type,
                    target.getId()
            )) {
                continue;
            }
            orderNotificationRepository.save(new OrderNotification(order, target, type, text));
            created++;
        }
        return created;
    }

    private OrderNotificationType resolveType(CustomerOrder order, LocalDate today) {
        if (order.getRequestedDueDate().isBefore(today)) {
            return OrderNotificationType.DELIVERY_DELAYED;
        }
        return OrderNotificationType.DELIVERY_IMMINENT;
    }

    private String message(
            CustomerOrder order,
            LocalDate today,
            OrderNotificationType type
    ) {
        if (type == OrderNotificationType.DELIVERY_DELAYED) {
            return order.getOrderNo()
                    + " 납기 "
                    + order.getRequestedDueDate()
                    + "가 지났습니다.";
        }
        long daysUntilDue = ChronoUnit.DAYS.between(today, order.getRequestedDueDate());
        return order.getOrderNo()
                + " 납기까지 "
                + daysUntilDue
                + "일 남았습니다.";
    }
}
