package net.likelion.bebc25.prodflow.order_notification.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.order_notification.dto.OrderDueNotificationResponse;
import net.likelion.bebc25.prodflow.order_notification.dto.OrderNotificationDto;
import net.likelion.bebc25.prodflow.order_notification.dto.SentOrderNotificationResponse;
import net.likelion.bebc25.prodflow.order_notification.entity.OrderNotification;
import net.likelion.bebc25.prodflow.order_notification.enums.OrderNotificationType;
import net.likelion.bebc25.prodflow.order_notification.repository.OrderNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderNotificationService {

    private static final int IMMINENT_DAYS = 3;

    private final OrderNotificationRepository orderNotificationRepository;

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

    // 납기가 지났거나 3일 이내인 수주를 담당 직원에게 한 번만 알린다.
    @Transactional
    public int createDueNotifications(LocalDate today) {
        LocalDate riskDate = today.plusDays(IMMINENT_DAYS);
        List<CustomerOrder> orders =
                orderNotificationRepository.findDueNotificationCandidates(riskDate);

        int created = 0;
        for (CustomerOrder order : orders) {
            OrderNotificationType type = resolveType(order, today);
            if (orderNotificationRepository.existsByCustomerOrder_IdAndNotificationType(
                    order.getId(),
                    type
            )) {
                continue;
            }

            orderNotificationRepository.save(new OrderNotification(
                    order,
                    order.getUser(),
                    type,
                    message(order, today, type)
            ));
            created++;
        }
        return created;
    }

    public List<SentOrderNotificationResponse> listSentDueNotifications() {
        return orderNotificationRepository.findSentDueNotifications(List.of(
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
        if (orderNotificationRepository.existsByCustomerOrder_IdAndNotificationType(order.getId(), type)) {
            return new OrderDueNotificationResponse(order.getId(), type, false);
        }

        orderNotificationRepository.save(new OrderNotification(
                order,
                order.getUser(),
                type,
                message(order, today, type)
        ));
        return new OrderDueNotificationResponse(order.getId(), type, true);
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
