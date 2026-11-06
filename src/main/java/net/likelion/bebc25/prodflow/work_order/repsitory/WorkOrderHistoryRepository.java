package net.likelion.bebc25.prodflow.work_order.repsitory;

import net.likelion.bebc25.prodflow.work_order.entity.WorkOrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderHistoryRepository extends JpaRepository<WorkOrderHistory, Long> {

    // 이 작업이 그 라인에서 빠진 이력. 최근 이동이 앞이다.
    List<WorkOrderHistory> findByWorkOrder_IdAndPreviousLine_IdOrderByIdDesc(Long workOrderId, Long previousLineId);
}
