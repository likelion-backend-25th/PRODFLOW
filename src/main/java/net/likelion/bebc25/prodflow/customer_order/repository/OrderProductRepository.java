package net.likelion.bebc25.prodflow.customer_order.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

// 수주 품목을 id로 조회한다.
public interface OrderProductRepository extends JpaRepository<OrderProduct, Long> {

    @EntityGraph(attributePaths = "product")
    List<OrderProduct> findByCustomerOrder_IdIn(Collection<Long> orderIds);

    // 수주 상세에 표시할 품목·기종 조회
    @Query("""
            select op from OrderProduct op
            join fetch op.product product
            join fetch product.device
            where op.customerOrder.id = :orderId
            order by op.id
            """)
    List<OrderProduct> findAllByCustomerOrderId(@Param("orderId") Long orderId);

    // 품목별 최근 수주 이력 조회
    @Query("""
            SELECT op
            FROM OrderProduct op
            JOIN FETCH op.customerOrder co
            JOIN FETCH co.client
            JOIN FETCH co.statusCode
            WHERE op.product.id = :productId
            ORDER BY co.createdAt DESC
            """)
    List<OrderProduct> findRecentByProductId(@Param("productId") Long productId, Pageable pageable);
}
