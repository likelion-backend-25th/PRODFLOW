package net.likelion.bebc25.prodflow.dashboard.repository;

import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DeliveryRiskRepository extends JpaRepository<WorkOrder, Long> {

    @Query("""
            SELECT wo
            FROM WorkOrder wo
            JOIN FETCH wo.orderProduct op
            JOIN FETCH op.customerOrder co
            JOIN FETCH co.client c
            JOIN FETCH wo.commonCode cc
            WHERE co.requestedDueDate <= :riskDate
            AND cc.code <> 'PACK_DONE'
            AND co.statusCode.code <> 'CANCELLED'
            ORDER BY co.requestedDueDate ASC
            """)
    List<WorkOrder> findDeliveryRiskWorkOrders(
            @Param("riskDate") LocalDate riskDate
    );

}
