package net.likelion.bebc25.prodflow.customer_order.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    // 견적 ID 목록에 연결된 수주 일괄 조회, 취소 수주 포함
    @EntityGraph(attributePaths = "quotation")
    List<CustomerOrder> findByQuotationIdIn(List<Long> quotationIds);

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

    Optional<CustomerOrder> findFirstByClient_IdOrderByCreatedAtDesc(Long clientId);
}
