package net.likelion.bebc25.prodflow.work_order.repository;

import net.likelion.bebc25.prodflow.work_order.entity.WorkOrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderHistoryRepository extends JpaRepository<WorkOrderHistory, Long> {

    // 이 작업이 그 라인에서 빠진 이력. 최근 이동이 앞이다.
    List<WorkOrderHistory> findByWorkOrder_IdAndPreviousLine_IdOrderByIdDesc(
            Long workOrderId,
            Long previousLineId);

    /**
     * 특정 작업지시서의 라인 및 담당자 변경 이력 최근 5개 조회
     *
     * @param workOrderId 작업지시서 ID
     * @return 변경 일시 기준 최신 5개 이력
     */
    List<WorkOrderHistory> findTop5ByWorkOrder_IdOrderByChangedAtDesc(
            Long workOrderId
    );
}
