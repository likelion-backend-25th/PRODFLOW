package net.likelion.bebc25.prodflow.workorder.repsitory;

import net.likelion.bebc25.prodflow.workorder.entity.ProductionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface ProductionPlanRepository extends JpaRepository<ProductionPlan, Long> {

    // 작업지시서의 라인으로 같은 날짜 계획 수량을 합한다.
    @Query("""
            select coalesce(sum(plan.quantity), 0)
            from ProductionPlan plan
            where plan.workOrder.line.id = :lineId
              and plan.planDate = :planDate
            """)
    long sumQuantityByLineAndDate(@Param("lineId") Long lineId, @Param("planDate") LocalDate planDate);
}
