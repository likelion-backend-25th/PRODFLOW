package net.likelion.bebc25.prodflow.production_calender.repository;

import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductionCalenderRepository extends JpaRepository<WorkOrder, Long> {

    // 확정된 수주의 진행 중인 작업지시 조회
    @Query("""
            SELECT DISTINCT workOrder
            FROM WorkOrder workOrder
            JOIN FETCH workOrder.line
            JOIN FETCH workOrder.commonCode commonCode
            JOIN FETCH workOrder.orderProduct orderProduct
            JOIN FETCH orderProduct.customerOrder customerOrder
            WHERE customerOrder.confirmationStatus = :confirmationStatus
            AND commonCode.code IN :statuses
            ORDER BY workOrder.line.id, workOrder.plannedStartDate
            """)
    List<WorkOrder> findCalenderSchedules(
            @Param("confirmationStatus") ConfirmationStatus confirmationStatus,
            @Param("statuses") List<String> statuses
    );

}
