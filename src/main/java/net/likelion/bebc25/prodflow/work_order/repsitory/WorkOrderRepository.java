package net.likelion.bebc25.prodflow.work_order.repsitory;

import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;


public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    // 마감 날짜별 작업들로 그룹화하여 최신 등록순으로 정렬
    Page<WorkOrder> findAllByOrderByPlannedEndDateAscIdDesc(Pageable pageable);

    // 조건이 없으면 전체 목록 조회 완료 예정일이 빠른 순이고, 같은 날이면 최근 등록이 앞이다.
    @Query("""
            select workOrder
            from WorkOrder workOrder
            join workOrder.orderProduct orderProduct
            join orderProduct.customerOrder customerOrder
            join customerOrder.client client
            where (:lineId is null or workOrder.line.id = :lineId)
              and (:status is null or workOrder.commonCode.code = :status)
              and (:from is null or workOrder.workOrderDate >= :from)
              and (:to is null or workOrder.workOrderDate <= :to)
              and (
                    :keyword is null
                    or customerOrder.orderNo like concat('%', :keyword, '%')
                    or orderProduct.productName like concat('%', :keyword, '%')
                    or client.company like concat('%', :keyword, '%')
                    or workOrder.user.name like concat('%', :keyword, '%')
              )
            order by workOrder.plannedEndDate asc, workOrder.id desc
            """)
    Page<WorkOrder> search(
            @Param("lineId") Long lineId,
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("keyword") String keyword,
            Pageable pageable);
}
