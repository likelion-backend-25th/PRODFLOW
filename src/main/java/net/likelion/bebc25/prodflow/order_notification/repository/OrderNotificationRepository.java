package net.likelion.bebc25.prodflow.order_notification.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.order_notification.dto.SentOrderNotificationResponse;
import net.likelion.bebc25.prodflow.order_notification.entity.OrderNotification;
import net.likelion.bebc25.prodflow.order_notification.enums.OrderNotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderNotificationRepository extends JpaRepository<OrderNotification, Long> {

    List<OrderNotification> findByTarget_IdOrderByCreatedAtDesc(Long targetId);

    long countByTarget_IdAndReadAtIsNull(Long targetId);

    Optional<OrderNotification> findByIdAndTarget_Id(Long notificationId, Long targetId);

    boolean existsByCustomerOrder_IdAndNotificationType(
            Long customerOrderId,
            OrderNotificationType notificationType
    );

    boolean existsByCustomerOrder_IdAndNotificationTypeAndTarget_Id(
            Long customerOrderId,
            OrderNotificationType notificationType,
            Long targetId
    );

    @Query("""
            SELECT DISTINCT new net.likelion.bebc25.prodflow.order_notification.dto.SentOrderNotificationResponse(
                n.customerOrder.id,
                n.notificationType
            )
            FROM OrderNotification n
            WHERE n.customerOrder IS NOT NULL
              AND n.target.id = :targetId
              AND n.notificationType IN :types
            """)
    List<SentOrderNotificationResponse> findSentDueNotifications(
            @Param("targetId") Long targetId,
            @Param("types") Collection<OrderNotificationType> types
    );

    @Query("""
            SELECT o
            FROM CustomerOrder o
            LEFT JOIN FETCH o.user
            JOIN FETCH o.statusCode sc
            JOIN FETCH sc.codeGroup
            WHERE o.id = :orderId
            """)
    Optional<CustomerOrder> findOrderForNotification(@Param("orderId") Long orderId);

    @Query("""
            SELECT o
            FROM CustomerOrder o
            JOIN FETCH o.user
            JOIN FETCH o.client
            JOIN FETCH o.statusCode sc
            JOIN FETCH sc.codeGroup cg
            WHERE o.id = :orderId
              AND cg.groupCode = 'ORDER_STATUS'
              AND sc.code <> 'CANCELLED'
              AND o.requestedDueDate <= :riskDate
              AND NOT EXISTS (
                  SELECT s.id
                  FROM Shipment s
                  WHERE s.orderId = o.id
                    AND s.deliveredAt IS NOT NULL
              )
            """)
    Optional<CustomerOrder> findDueNotificationCandidate(
            @Param("orderId") Long orderId,
            @Param("riskDate") LocalDate riskDate
    );

    // 취소가 아니고 배송 완료가 없으며 납기가 기준일 이전인 수주
    @Query("""
            SELECT o
            FROM CustomerOrder o
            JOIN FETCH o.user
            JOIN FETCH o.client
            JOIN FETCH o.statusCode sc
            JOIN FETCH sc.codeGroup cg
            WHERE cg.groupCode = 'ORDER_STATUS'
              AND sc.code <> 'CANCELLED'
              AND o.requestedDueDate <= :riskDate
              AND NOT EXISTS (
                  SELECT s.id
                  FROM Shipment s
                  WHERE s.orderId = o.id
                    AND s.deliveredAt IS NOT NULL
              )
            """)
    List<CustomerOrder> findDueNotificationCandidates(
            @Param("riskDate") LocalDate riskDate
    );
}
