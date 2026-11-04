package net.likelion.bebc25.prodflow.work_order.repsitory;

import net.likelion.bebc25.prodflow.work_order.entity.WorkOrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderHistoryRepository extends JpaRepository<WorkOrderHistory, Long> {
}
