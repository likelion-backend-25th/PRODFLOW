package net.likelion.bebc25.prodflow.dashboard.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface OrderStatisticsRepository extends JpaRepository<CustomerOrder, Long> {
    // 전체 수주 건수 조회
    @Query("""
            SELECT COUNT(o)
            FROM CustomerOrder o
            """)
    long countAllOrders();

    // 조회 기간에 등록된 수주 건수 조회
    @Query("""
            SELECT COUNT(o)
            FROM CustomerOrder o
            WHERE o.createdAt >= :startDate
              AND o.createdAt < :endDate
            """)
    long countOrdersByPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );


}
