package net.likelion.bebc25.prodflow.work_order.repository;

import jakarta.persistence.LockModeType;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
                        commonCode.code,
                        orderProduct.id,
                        workOrder.plannedStartDate,
                        workOrder.completedAt
                    )
                    from WorkOrder workOrder
                    join workOrder.orderProduct orderProduct
                    join orderProduct.customerOrder customerOrder
                    join customerOrder.client client
                    join workOrder.line line
                    left join workOrder.user assignee
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
                    left join workOrder.user assignee
                    join workOrder.commonCode commonCode
                    """
    )
    Page<WorkOrderDto.PageResponse> findPage(Pageable pageable);

    // 마감 날짜별 작업들로 그룹화하여 최신 등록순으로 정렬
    Page<WorkOrder> findAllByOrderByPlannedEndDateAscIdDesc(Pageable pageable);

    // 작업지시서 검색 및 페이징 조회
    // 검색 조건 AND 적용, 미완료 작업 우선 정렬
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
                        commonCode.code,
                        orderProduct.id,
                        workOrder.plannedStartDate,
                        workOrder.completedAt
                    )
                    from WorkOrder workOrder
                    join workOrder.orderProduct orderProduct
                    join orderProduct.customerOrder customerOrder
                    join workOrder.line line
                    join customerOrder.client client
                    left join workOrder.user assignee
                    join workOrder.commonCode commonCode
                    where (:lineId is null or line.id = :lineId)
                      and (:orderId is null or customerOrder.id = :orderId)
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
                      and (:assigneeId is null or assignee.id = :assigneeId)
                    order by
                        case
                            when commonCode.code = 'PACK_DONE' then 1
                            else 0
                        end asc,
                        workOrder.plannedEndDate asc,
                        workOrder.id desc
                    """,
            countQuery = """
                    select count(workOrder)
                    from WorkOrder workOrder
                    join workOrder.orderProduct orderProduct
                    join orderProduct.customerOrder customerOrder
                    join customerOrder.client client
                    join workOrder.line line
                    left join workOrder.user assignee
                    join workOrder.commonCode commonCode
                    where (:lineId is null or line.id = :lineId)
                      and (:orderId is null or customerOrder.id = :orderId)
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
                      and (:assigneeId is null or assignee.id = :assigneeId)
                    """
    )
    Page<WorkOrderDto.PageResponse> searchPage(
            @Param("orderId") Long orderId,
            @Param("lineId") Long lineId,
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("keyword") String keyword,
            @Param("assigneeId") Long assigneeId,
            Pageable pageable);

    // 기존 목록 조회 호출은 수주·담당자 필터 없이 검색한다.
    default Page<WorkOrderDto.PageResponse> searchPage(
            Long lineId, String status, LocalDate from, LocalDate to,
            String keyword, Pageable pageable) {
        return searchPage(null, lineId, status, from, to, keyword, null, pageable);
    }

    // 라인 상세는 작업 엔티티가 필요하다. 목록 화면 조회와 분리한다.
    @Query("""
            select workOrder
            from WorkOrder workOrder
            join workOrder.orderProduct orderProduct
            join orderProduct.customerOrder customerOrder
            join customerOrder.client client
            left join workOrder.user assigned
            where (:lineId is null or workOrder.line.id = :lineId)
              and (:status is null or workOrder.commonCode.code = :status)
              and (:from is null or workOrder.workOrderDate >= :from)
              and (:to is null or workOrder.workOrderDate <= :to)
              and (
                    :keyword is null
                    or customerOrder.orderNo like concat('%', :keyword, '%')
                    or orderProduct.productName like concat('%', :keyword, '%')
                    or client.company like concat('%', :keyword, '%')
                    or assigned.name like concat('%', :keyword, '%')
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

    // 같은 라인에서 지정한 상태의 작업지시를 조회한다.
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

    // 지정한 담당자에게 해당 상태 코드의 작업지시가 하나라도 있는지 확인한다.
    boolean existsByUser_IdAndCommonCode_Code(Long userId, String code);

    // 지정한 담당자의 준비 작업 목표 수량을 합산한다. 작업이 없으면 0을 반환한다.
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

    // 수주에 지정 상태가 아닌 작업지시가 있는지 (출고대기 전환 전 포장완료 여부 확인)
    boolean existsByOrderProduct_CustomerOrder_IdAndCommonCode_CodeNotIn(
            Long customerOrderId,
            Collection<String> codes
    );

    // 지정한 수주들의 작업지시와 연관 정보를 ID 순으로 조회하고 쓰기 잠금을 건다.
    // 상태로 제한하지 않으며, 담당자가 없는 작업도 포함한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select w from WorkOrder w join fetch w.orderProduct p
            join fetch p.customerOrder o join fetch w.commonCode
            join fetch w.line left join fetch w.user
            where o.id in :ids order by w.id
            """)
    List<WorkOrder> findByOrderIds(@Param("ids") Collection<Long> orderIds);

    // 지정한 수주에서 준비 상태가 아니거나 생산·일시정지·포장·포장완료 전환 이력이 있는 작업 ID를 조회한다.
    // 준비로 돌아온 작업도 해당 이력이 있으면 조회하여 재배치 대상에서 제외한다.
    @Query("""
            select w.id from WorkOrder w
            where w.orderProduct.customerOrder.id in :ids
              and (w.commonCode.code <> 'PREPARE' or exists (
                   select h.id from StatusHistory h
                   where h.entity = net.likelion.bebc25.prodflow.common.enums.HistoryEntityType.WORK_ORDER
                     and h.entityId = w.id
                     and (h.fromStatus in ('PRODUCE', 'SUSPEND', 'PACK', 'PACK_DONE')
                          or h.toStatus in ('PRODUCE', 'SUSPEND', 'PACK', 'PACK_DONE'))))
            """)
    List<Long> findFixedWorkOrderIds(@Param("ids") Collection<Long> orderIds);

    // 생산대기·생산중·생산보류 수주에서 담당자 없는 준비 작업을 조회한다.
    // 수주 납기, 수주 등록일시, 수주 ID, 작업지시 ID 순으로 정렬한다.
    @Query("""
            select w from WorkOrder w join fetch w.orderProduct p join fetch p.customerOrder o
            where w.commonCode.code = 'PREPARE' and w.user is null
              and o.statusCode.code in ('CONFIRMED', 'IN_PRODUCTION', 'ON_HOLD')
            order by o.estimatedDueDate, o.createdAt, o.id, w.id
            """)
    List<WorkOrder> findUnassignedPrepareOrderByDueDate();

    // 지정한 담당자와 날짜에 생산계획이 있는 준비·생산·일시정지 작업지시 장수를 센다.
    // 취소 수주는 제외하며, 같은 작업지시의 계획이 여러 건이어도 한 장으로 센다.
    @Query("""
            select count(distinct w.id) from WorkOrder w
            join ProductionPlan p on p.workOrder = w
            where w.user.id = :userId and p.planDate = :date
              and w.commonCode.code in ('PREPARE', 'PRODUCE', 'SUSPEND')
              and w.orderProduct.customerOrder.statusCode.code <> 'CANCELLED'
            """)
    long countPlannedWorkByUserAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    // 지정한 수주의 품목에 연결된 작업지시가 상태와 관계없이 하나라도 있는지 확인한다.
    boolean existsByOrderProduct_CustomerOrder_Id(Long orderId);

    // 배송 목록용. 작업지시가 있는 수주 ID
    @Query("""
            select distinct w.orderProduct.customerOrder.id
            from WorkOrder w
            where w.orderProduct.customerOrder.id in :orderIds
            """)
    List<Long> findDistinctOrderIdsByOrderIdIn(@Param("orderIds") Collection<Long> orderIds);

    // 배송 목록용. 지정 상태가 아닌 작업이 하나라도 있는 수주 ID
    @Query("""
            select distinct w.orderProduct.customerOrder.id
            from WorkOrder w
            where w.orderProduct.customerOrder.id in :orderIds
              and w.commonCode.code not in :codes
            """)
    List<Long> findDistinctOrderIdsByOrderIdInAndCodeNotIn(
            @Param("orderIds") Collection<Long> orderIds,
            @Param("codes") Collection<String> codes
    );
}
