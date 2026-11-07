package net.likelion.bebc25.prodflow.dashboard.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface DeliveryPerformanceRepository
        extends JpaRepository<CustomerOrder, Long> {

    // 납기일이 조회 기간 안이고 오늘보다 이전인 취소가 아닌 수주
    @Query("""
            SELECT COUNT(o)
            FROM CustomerOrder o
            JOIN o.statusCode sc
            JOIN sc.codeGroup cg
            WHERE o.requestedDueDate >= :from
              AND o.requestedDueDate <= :to
              AND o.requestedDueDate < :today
              AND cg.groupCode = 'ORDER_STATUS'
              AND sc.code <> 'CANCELLED'
            """)
    long countTargetOrders(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("today") LocalDate today
    );

    // 배송 완료일이 요청 납기일 이전인 수주
    @Query("""
            SELECT COUNT(o)
            FROM CustomerOrder o
            JOIN o.statusCode sc
            JOIN sc.codeGroup cg
            WHERE o.requestedDueDate >= :from
              AND o.requestedDueDate <= :to
              AND o.requestedDueDate < :today
              AND cg.groupCode = 'ORDER_STATUS'
              AND sc.code <> 'CANCELLED'
              AND EXISTS (
                  SELECT s.id
                  FROM Shipment s
                  WHERE s.orderId = o.id
                    AND s.deliveredAt IS NOT NULL
                    AND cast(s.deliveredAt as localdate) <= o.requestedDueDate
              )
            """)
    long countOnTimeOrders(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("today") LocalDate today
    );
}
