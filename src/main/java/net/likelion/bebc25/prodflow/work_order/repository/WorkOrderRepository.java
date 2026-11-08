package net.likelion.bebc25.prodflow.work_order.repository;

import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    // 목록 화면 컬럼만 한 번에 조회한다. 연관 엔티티를 행마다 다시 읽지 않는다.
    // 완료 예정일이 빠른 순이고, 같은 날이면 최근 등록이 앞이다.
    @Query(
            value = """
                    select new net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto$PageResponse(
                        workOrder.id,
                        workOrder.workNo,
                        customerOrder.orderNo,
                        client.company,
                        orderProduct.productName,
                        workOrder.quantity,
                        line.lineName,
                        assignee.name,
                        workOrder.workOrderDate,
                        workOrder.plannedEndDate,
                        commonCode.code
                    )
                    from WorkOrder workOrder
                    join workOrder.orderProduct orderProduct
                    join orderProduct.customerOrder customerOrder
                    join customerOrder.client client
                    join workOrder.line line
                    join workOrder.user assignee
                    join workOrder.commonCode commonCode
                    order by workOrder.plannedEndDate asc, workOrder.id desc
                    """,
            countQuery = """
                    select count(workOrder)
                    from WorkOrder workOrder
                    join workOrder.orderProduct orderProduct
                    join orderProduct.customerOrder customerOrder
                    join customerOrder.client client
                    join workOrder.line line
                    join workOrder.user assignee
                    join workOrder.commonCode commonCode
                    """
    )
    Page<WorkOrderDto.PageResponse> findPage(Pageable pageable);

    // 목록 검색도 같은 컬럼만 조회한다. 조건이 없는 값은 거르지 않는다.
    @Query(
            value = """
                    select new net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto$PageResponse(
                        workOrder.id,
                        workOrder.workNo,
                        customerOrder.orderNo,
                        client.company,
                        orderProduct.productName,
                        workOrder.quantity,
                        line.lineName,
                        assignee.name,
                        workOrder.workOrderDate,
                        workOrder.plannedEndDate,
                        commonCode.code
                    )
                    from WorkOrder workOrder
                    join workOrder.orderProduct orderProduct
                    join orderProduct.customerOrder customerOrder
                    join customerOrder.client client
                    join workOrder.line line
                    join workOrder.user assignee
                    join workOrder.commonCode commonCode
                    where (:lineId is null or line.id = :lineId)
                      and (:status is null or commonCode.code = :status)
                      and (:from is null or workOrder.workOrderDate >= :from)
                      and (:to is null or workOrder.workOrderDate <= :to)
                      and (
                            :keyword is null
                            or customerOrder.orderNo like concat('%', :keyword, '%')
                            or orderProduct.productName like concat('%', :keyword, '%')
                            or client.company like concat('%', :keyword, '%')
                            or assignee.name like concat('%', :keyword, '%')
                      )
                    order by workOrder.plannedEndDate asc, workOrder.id desc
                    """,
            countQuery = """
                    select count(workOrder)
                    from WorkOrder workOrder
                    join workOrder.orderProduct orderProduct
                    join orderProduct.customerOrder customerOrder
                    join customerOrder.client client
                    join workOrder.line line
                    join workOrder.user assignee
                    join workOrder.commonCode commonCode
                    where (:lineId is null or line.id = :lineId)
                      and (:status is null or commonCode.code = :status)
                      and (:from is null or workOrder.workOrderDate >= :from)
                      and (:to is null or workOrder.workOrderDate <= :to)
                      and (
                            :keyword is null
                            or customerOrder.orderNo like concat('%', :keyword, '%')
                            or orderProduct.productName like concat('%', :keyword, '%')
                            or client.company like concat('%', :keyword, '%')
                            or assignee.name like concat('%', :keyword, '%')
                      )
                    """
    )
    Page<WorkOrderDto.PageResponse> searchPage(
            @Param("lineId") Long lineId,
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("keyword") String keyword,
            Pageable pageable);

    // 라인 상세는 작업 엔티티가 필요하다. 목록 화면 조회와 분리한다.
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

    // 이 작업을 뺀 같은 수주에 해당 상태의 작업이 있는지
    boolean existsByOrderProduct_CustomerOrder_IdAndCommonCode_CodeAndIdNot(
            Long customerOrderId,
            String code,
            Long workOrderId
    );

    // 이 작업을 뺀 같은 라인에 해당 상태의 작업이 있는지
    boolean existsByLine_IdAndCommonCode_CodeAndIdNot(Long lineId, String code, Long workOrderId);

    // 고장 라인에 남은 생산·일시정지 작업. 미래 계획이 없어도 라인을 비운다.
    List<WorkOrder> findByLine_IdAndCommonCode_CodeIn(Long lineId, Collection<String> codes);

    // 같은 품목, 같은 라인의 준비 작업. 재배치로 넘긴 수량을 새 장 대신 여기에 더한다.
    Optional<WorkOrder> findFirstByOrderProduct_IdAndLine_IdAndCommonCode_CodeAndIdNot(
            Long orderProductId,
            Long lineId,
            String code,
            Long workOrderId
    );

    // 재배치로 만든 작업 번호가 이미 있으면 다음 번호를 쓰기 위해 확인한다.
    boolean existsByWorkNo(String workNo);

    // 이 담당자에게 해당 상태의 작업지시가 있는지. 생산 중이면 준비 작업을 더 주지 않는다.
    boolean existsByUser_IdAndCommonCode_Code(Long userId, String code);

    // 담당자를 고를 때 준비 상태 수량의 합이 적은 사람을 본다.
    @Query("""
            select coalesce(sum(workOrder.quantity), 0)
            from WorkOrder workOrder
            where workOrder.user.id = :userId
              and workOrder.commonCode.code = 'PREPARE'
            """)
    long sumPrepareQuantityByUserId(@Param("userId") Long userId);

    // 이 작업을 뺀 같은 수주에 포장 이전 작업이 있는지
    boolean existsByOrderProduct_CustomerOrder_IdAndCommonCode_CodeNotInAndIdNot(
            Long customerOrderId,
            Collection<String> codes,
            Long workOrderId
    );
}
