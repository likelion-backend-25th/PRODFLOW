package net.likelion.bebc25.prodflow.work_order.repsitory;

import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    // 마감 날짜별 작업들로 그룹화하여 최신 등록순으로 정렬
    Page<WorkOrder> findAllByOrderByPlannedEndDateAscIdDesc(Pageable pageable);
}
