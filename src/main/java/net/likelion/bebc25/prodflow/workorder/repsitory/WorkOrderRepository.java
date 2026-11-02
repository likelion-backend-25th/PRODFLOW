package net.likelion.bebc25.prodflow.workorder.repsitory;

import net.likelion.bebc25.prodflow.workorder.entity.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    // 마감 날짜별 작업들로 그룹화하여 최신 등록순으로 정렬
    List<WorkOrder> findAllByOrderByPlannedEndDateAscIdDesc();
}
