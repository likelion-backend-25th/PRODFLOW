package net.likelion.bebc25.prodflow.dashboard.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface DeliveryPerformanceRepository
        extends JpaRepository<CustomerOrder, Long> {

    // 집계 대상 수주 건수
    @Query("""
            SELECT COUNT(o)
            FROM CustomerOrder o
            JOIN o.statusCode sc
            WHERE o.requestedDueDate >= :from
              AND o.requestedDueDate <= :to
              AND sc.code <> 'CANCELLED'
            """)
    long countTargetOrders(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    // 납기 내 생산 완료 수주 건수
    @Query("""
            SELECT COUNT(o)
            FROM CustomerOrder o
            JOIN o.statusCode sc
            WHERE o.requestedDueDate >= :from
              AND o.requestedDueDate <= :to
              AND sc.code <> 'CANCELLED'
              AND o.endDate IS NOT NULL
              AND o.endDate <= o.requestedDueDate
            """)
    long countOnTimeOrders(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}