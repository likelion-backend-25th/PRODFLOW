package net.likelion.bebc25.prodflow.customer_order.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    // 견적 ID 목록에 연결된 수주 일괄 조회, 취소 수주 포함
    @EntityGraph(attributePaths = "quotation")
    List<CustomerOrder> findByQuotationIdIn(List<Long> quotationIds);
}
