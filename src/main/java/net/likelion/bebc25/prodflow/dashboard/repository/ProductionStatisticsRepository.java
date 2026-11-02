package net.likelion.bebc25.prodflow.dashboard.repository;

import net.likelion.bebc25.prodflow.workorder.entity.ProductionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProductionStatisticsRepository
        extends JpaRepository<ProductionPlan, Long> {

    // 선택한 월의 생산 계획량
    @Query("""
            SELECT COALESCE(SUM(p.quantity), 0)
            FROM ProductionPlan p
            WHERE p.planDate >= :from
              AND p.planDate < :to
            """)
    long sumPlannedQuantity(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    // 날짜별 생산 계획량
    @Query("""
            SELECT p.planDate, SUM(p.quantity)
            FROM ProductionPlan p
            WHERE p.planDate >= :from
              AND p.planDate < :to
            GROUP BY p.planDate
            ORDER BY p.planDate
            """)
    List<Object[]> findDailyPlannedQuantity(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    // 선택한 월의 생산 완료량
    @Query("""
            SELECT COALESCE(SUM(w.quantity), 0)
            FROM WorkOrder w
            JOIN w.commonCode c
            WHERE w.completedAt >= :from
              AND w.completedAt < :to
              AND c.code = 'PACK_DONE'
            """)
    long sumCompletedQuantity(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    // 날짜별 생산 완료량
    @Query("""
            SELECT w.completedAt, SUM(w.quantity)
            FROM WorkOrder w
            JOIN w.commonCode c
            WHERE w.completedAt >= :from
              AND w.completedAt < :to
              AND c.code = 'PACK_DONE'
            GROUP BY w.completedAt
            ORDER BY w.completedAt
            """)
    List<Object[]> findDailyCompletedQuantity(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}