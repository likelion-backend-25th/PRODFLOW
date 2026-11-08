package net.likelion.bebc25.prodflow.order_notification.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.order_notification.entity.OrderNotification;
import net.likelion.bebc25.prodflow.order_notification.enums.OrderNotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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
