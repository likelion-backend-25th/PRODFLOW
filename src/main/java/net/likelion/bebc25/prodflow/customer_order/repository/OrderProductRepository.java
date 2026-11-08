package net.likelion.bebc25.prodflow.customer_order.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// 수주 품목을 id로 조회한다.
public interface OrderProductRepository extends JpaRepository<OrderProduct, Long> {

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
