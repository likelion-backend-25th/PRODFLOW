package net.likelion.bebc25.prodflow.work_order.repository;

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

    // 작업지시 한 장의 날짜별 계획. 상세 조회와 재배치가 같이 쓴다.
    List<ProductionPlan> findByWorkOrder_Id(Long workOrderId);

    // 같은 품목의 계획 수량 합. 상태와 상관없이 전부 센다. 재배치 전후가 같은지 확인할 때 쓴다.
    @Query("""
            select coalesce(sum(plan.quantity), 0)
            from ProductionPlan plan
            where plan.workOrder.orderProduct.id = :orderProductId
            """)
    long sumQuantityByOrderProductId(@Param("orderProductId") Long orderProductId);

    // 오늘 이후 생산·일시 정지 계획. 점검·고장으로 바꾸기 전에 남으면 상태를 바꾸지 않는다.
    @Query("""
            select count(plan)
            from ProductionPlan plan
            where plan.workOrder.line.id = :lineId
              and plan.planDate >= :fromDate
              and plan.workOrder.orderProduct.customerOrder.statusCode.code <> 'CANCELLED'
              and plan.workOrder.commonCode.code in ('PRODUCE', 'SUSPEND')
            """)
    long countInProgressPlans(@Param("lineId") Long lineId, @Param("fromDate") LocalDate fromDate);

    // 그 라인의 준비 계획만, fromDate부터. 취소 수주는 빼고 계획일·작업지시 id 순이다.
    // 생산 중·일시 정지·생산 중지는 여기 없다. 그 계획은 자동으로 옮기지 않는다.
    @Query("""
            select plan
            from ProductionPlan plan
            join fetch plan.workOrder wo
            where wo.line.id = :lineId
              and plan.planDate >= :fromDate
              and wo.orderProduct.customerOrder.statusCode.code <> 'CANCELLED'
              and wo.commonCode.code = 'PREPARE'
            order by plan.planDate asc, wo.id asc
            """)
    List<ProductionPlan> findRescheduleTargets(
            @Param("lineId") Long lineId,
            @Param("fromDate") LocalDate fromDate
    );

    // 고장 라인의 오늘 이후 준비·생산·일시정지 계획. 취소 수주는 뺀다.
    @Query("""
            select plan
            from ProductionPlan plan
            join fetch plan.workOrder wo
            where wo.line.id = :lineId
              and plan.planDate >= :fromDate
              and wo.orderProduct.customerOrder.statusCode.code <> 'CANCELLED'
              and wo.commonCode.code in ('PREPARE', 'PRODUCE', 'SUSPEND')
            order by plan.planDate asc, wo.id asc
            """)
    List<ProductionPlan> findBrokenTargets(
            @Param("lineId") Long lineId,
            @Param("fromDate") LocalDate fromDate
    );
}
