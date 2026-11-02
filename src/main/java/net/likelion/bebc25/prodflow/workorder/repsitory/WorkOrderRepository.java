package net.likelion.bebc25.prodflow.workorder.repsitory;

import net.likelion.bebc25.prodflow.workorder.entity.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {
}
