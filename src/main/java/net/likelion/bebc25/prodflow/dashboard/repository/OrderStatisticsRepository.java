package net.likelion.bebc25.prodflow.dashboard.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface OrderStatisticsRepository extends JpaRepository<CustomerOrder, Long> {
    // 전체 수주 건수 조회 (취소 수주 제외)
    @Query("""
            SELECT COUNT(o)
            FROM CustomerOrder o
            JOIN o.statusCode sc
            WHERE sc.code <> 'CANCELLED'
            """)
    long countAllOrders();

    // 조회 기간에 등록된 수주 건수 조회 (취소 수주 제외)
    @Query("""
            SELECT COUNT(o)
            FROM CustomerOrder o
            JOIN o.statusCode sc
            WHERE o.createdAt >= :startDate
              AND o.createdAt < :endDate
              AND sc.code <> 'CANCELLED'
            """)
    long countOrdersByPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );


}
