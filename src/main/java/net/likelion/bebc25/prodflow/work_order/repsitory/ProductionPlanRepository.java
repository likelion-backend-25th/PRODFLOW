package net.likelion.bebc25.prodflow.work_order.repsitory;

import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProductionPlanRepository extends JpaRepository<ProductionPlan, Long> {

    // 그 라인, 그 날짜의 계획 수량 합. 취소된 수주와 포장 이후 작업은 자리를 차지하지 않는다.
    @Query("""
            select coalesce(sum(plan.quantity), 0)
            from ProductionPlan plan
            where plan.workOrder.line.id = :lineId
              and plan.planDate = :planDate
              and plan.workOrder.orderProduct.customerOrder.statusCode.code <> 'CANCELLED'
              and plan.workOrder.commonCode.code not in ('PACK', 'PACK_DONE')
            """)
    long sumQuantityByLineAndDate(@Param("lineId") Long lineId, @Param("planDate") LocalDate planDate);

    // 취소된 수주와 포장 이후 작업을 뺀 날짜별 합이 생산능력보다 큰 날이다.
    @Query("""
            select plan.planDate
            from ProductionPlan plan
            where plan.workOrder.line.id = :lineId
              and plan.workOrder.orderProduct.customerOrder.statusCode.code <> 'CANCELLED'
              and plan.workOrder.commonCode.code not in ('PACK', 'PACK_DONE')
            group by plan.planDate
            having sum(plan.quantity) > :capacity
            """)
    List<LocalDate> findDatesExceedingCapacity(@Param("lineId") Long lineId, @Param("capacity") int capacity);

    List<ProductionPlan> findByWorkOrder_Id(Long workOrderId);
}
