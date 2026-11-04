package net.likelion.bebc25.prodflow.dashboard.repository;

import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProductProductionStatisticsRepository
        extends JpaRepository<WorkOrder, Long> {

    // 품목별 생산 완료 수량 조회
    @Query("""
            SELECT new net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto$ProductProductionResponse(
                op.product.id,
                op.productName,
                SUM(wo.quantity)
            )
            FROM WorkOrder wo
            JOIN wo.orderProduct op
            JOIN wo.commonCode cc
            JOIN cc.codeGroup cg
            WHERE wo.completedAt >= :startDate
            AND wo.completedAt < :endDate
            AND cg.groupCode = 'WORK_ORDER_STATUS'
            AND cc.code = 'PACK_DONE'
            GROUP BY op.product.id, op.productName
            ORDER BY op.product.id
            """)
    List<DashboardDto.ProductProductionResponse> findProductProductionQuantities(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // 전체 생산 완료 수량 조회
    @Query("""
            SELECT COALESCE(SUM(wo.quantity), 0)
            FROM WorkOrder wo
            JOIN wo.commonCode cc
            JOIN cc.codeGroup cg
            WHERE wo.completedAt >= :startDate
            AND wo.completedAt < :endDate
            AND cg.groupCode = 'WORK_ORDER_STATUS'
            AND cc.code = 'PACK_DONE'
            """)
    long sumCompletedProductionQuantity(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}