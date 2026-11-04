package net.likelion.bebc25.prodflow.dashboard.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

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

    @Query("""
            SELECT new net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto$OrderStatusStatisticsResponse(
                sc.code,
                sc.name,
                COUNT(o)
            )
            FROM CustomerOrder o
            JOIN o.statusCode sc
            GROUP BY sc.code, sc.name
            ORDER BY sc.code
            """)
    List<DashboardDto.OrderStatusStatisticsResponse> findOrderStatusStatistics();

    // 취소되지 않은 수주의 미입금 금액 합계
    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0)
                 - COALESCE((
                     SELECT SUM(p.amount)
                     FROM Payment p
                     JOIN p.order paymentOrder
                     JOIN paymentOrder.statusCode paymentStatusCode
                     WHERE paymentStatusCode.code <> 'CANCELLED'
                       AND p.status = :paymentStatus
                 ), 0)
            FROM CustomerOrder o
            JOIN o.statusCode sc
            WHERE sc.code <> 'CANCELLED'
            """)
    long sumUnpaidAmount(
            @Param("paymentStatus") PaymentStatus paymentStatus
    );
}