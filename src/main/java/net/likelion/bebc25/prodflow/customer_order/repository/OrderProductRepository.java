package net.likelion.bebc25.prodflow.customer_order.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

// 수주 품목을 id로 조회한다.
public interface OrderProductRepository extends JpaRepository<OrderProduct, Long> {

    @EntityGraph(attributePaths = "product")
    List<OrderProduct> findByCustomerOrder_IdIn(Collection<Long> orderIds);
}
