package net.likelion.bebc25.prodflow.customer_order.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    // 견적 ID 목록에 연결된 수주 일괄 조회, 취소 수주 포함
    @EntityGraph(attributePaths = "quotation")
    List<CustomerOrder> findByQuotationIdIn(List<Long> quotationIds);

    // 배송 탭: 상태별 수주. 납기 빠른 순
    @EntityGraph(attributePaths = {"client", "statusCode"})
    @Query("""
            SELECT o FROM CustomerOrder o
            WHERE o.statusCode.code = :status
            ORDER BY o.estimatedDueDate ASC
            """)
    List<CustomerOrder> findByStatusCodeOrderByEstimatedDueDateAsc(@Param("status") String status);

    // 잔금 대기: 배송 완료인데 전액 입금이 아닌 수주
    @EntityGraph(attributePaths = {"client", "statusCode"})
    @Query("""
            SELECT o FROM CustomerOrder o
            WHERE o.statusCode.code = 'COMPLETED'
              AND o.paymentStatus <> :paid
            ORDER BY o.estimatedDueDate ASC
            """)
    List<CustomerOrder> findBalanceWaitingOrders(@Param("paid") OrderPaymentStatus paid);

    long countByStatusCode_Code(String code);

    long countByStatusCode_CodeAndPaymentStatusNot(String code, OrderPaymentStatus paymentStatus);

    // 특정 거래처의 누적 수주량
    Long countByClientId(Long clientId);

    // 특정 거래처의 누적 수주 금액
    @Query("""
                SELECT COALESCE(SUM(o.totalAmount), 0)
                FROM CustomerOrder o
                WHERE o.client.id = :clientId
                  AND o.statusCode.code IN ('COMPLETED')
            """)
    Long sumTotalAmountByClientId(@Param("clientId") Long clientId);

    // 특정 거래처의 현재 진행중인 수주량
    @Query("""
                SELECT COUNT(o)
                FROM CustomerOrder o
                WHERE o.client.id = :clientId
                  AND o.statusCode.code NOT IN ('COMPLETED', 'ON_HOLD', 'CANCELLED')
            """)
    Long countActiveOrdersByClientId(@Param("clientId") Long clientId);
}
