package net.likelion.bebc25.prodflow.dashboard.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DeliveryRiskRepository extends JpaRepository<CustomerOrder, Long> {

    // 납기일이 기준일 이전이고 아직 배송 완료되지 않은 수주
    @Query("""
            SELECT o
            FROM CustomerOrder o
            JOIN FETCH o.statusCode sc
            JOIN FETCH sc.codeGroup cg
            JOIN FETCH o.client
            WHERE cg.groupCode = 'ORDER_STATUS'
              AND sc.code <> 'CANCELLED'
              AND o.requestedDueDate <= :riskDate
              AND NOT EXISTS (
                  SELECT s.id
                  FROM Shipment s
                  WHERE s.orderId = o.id
                    AND s.deliveredAt IS NOT NULL
              )
            ORDER BY o.requestedDueDate ASC
            """)
    List<CustomerOrder> findDeliveryRiskOrders(
            @Param("riskDate") LocalDate riskDate
    );

    @Query("""
            SELECT op
            FROM OrderProduct op
            WHERE op.customerOrder.id IN :orderIds
            ORDER BY op.id ASC
            """)
    List<OrderProduct> findOrderProducts(
            @Param("orderIds") List<Long> orderIds
    );
}
