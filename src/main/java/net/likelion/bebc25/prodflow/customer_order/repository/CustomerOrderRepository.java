package net.likelion.bebc25.prodflow.customer_order.repository;

import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.shipment.entity.Shipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    // 현금 입금의 중복 처리와 상태 변경을 직렬화한다.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") Long id);

    // 나중에 입금한 수주가 먼저 등록된 대기 수주의 생산 자리를 차지하지 않게 한다.
    @Query("""
            select o from CustomerOrder o
            where o.statusCode.code = 'WAITING'
              and (o.createdAt < :createdAt or (o.createdAt = :createdAt and o.id < :id))
            order by o.createdAt, o.id
            """)
    List<CustomerOrder> findEarlierWaitingOrders(@Param("createdAt") LocalDateTime createdAt, @Param("id") Long id);

    // 거래처·원본 견적·등록자·진행 상태를 포함한 수주 상세 조회
    @Query("""
            select o from CustomerOrder o
            join fetch o.client
            join fetch o.quotation
            join fetch o.user
            join fetch o.statusCode
            where o.id = :id
            """)
    Optional<CustomerOrder> findDetailById(@Param("id") Long id);

    String SEARCH_CONDITION = """
            where o.statusCode.codeGroup.id = 2
              and ((:done = true and o.statusCode.code = 'COMPLETED')
                   or (:done = false and o.statusCode.code not in ('COMPLETED', 'CANCELLED')))
              and (:keyword is null or lower(o.orderNo) like :keyword escape '!'
                   or lower(o.client.company) like :keyword escape '!'
                   or exists (select p.id from OrderProduct p where p.customerOrder = o
                              and lower(p.productName) like :keyword escape '!'))
              and (:confirmationStatus is null or o.confirmationStatus = :confirmationStatus)
              and (:statusCodeId is null or o.statusCode.id = :statusCodeId)
              and (:paymentStatus is null or o.paymentStatus = :paymentStatus)
            """;

    // 검색 조건별 수주 페이지 조회
    @Query(value = """
            select o from CustomerOrder o join fetch o.client join fetch o.statusCode
            left join Shipment s on s.orderId = o.id
            """ + SEARCH_CONDITION + """
            order by case when :completedSort = true and s.deliveredAt is null then 1 else 0 end,
                     case when :completedSort = true then s.deliveredAt else o.createdAt end desc, o.id desc
            """, countQuery = "select count(o) from CustomerOrder o " + SEARCH_CONDITION)
    Page<CustomerOrder> searchOrders(
            @Param("keyword") String keyword,
            @Param("done") boolean done,
            @Param("confirmationStatus") ConfirmationStatus confirmationStatus,
            @Param("statusCodeId") Long statusCodeId,
            @Param("paymentStatus") OrderPaymentStatus paymentStatus,
            @Param("completedSort") boolean completedSort,
            Pageable pageable);

    // 페이지 내 수주 품목 일괄 조회
    @Query("""
            select p from OrderProduct p join fetch p.customerOrder
            where p.customerOrder.id in :ids order by p.id
            """)
    List<OrderProduct> findItems(@Param("ids") List<Long> orderIds);

    // 페이지 내 배송 정보 일괄 조회
    @Query("select s from Shipment s join fetch s.carrier where s.orderId in :ids")
    List<Shipment> findShipments(@Param("ids") List<Long> orderIds);

    // 담당자 미배정 수주 ID 조회
    @Query("""
            select distinct w.orderProduct.customerOrder.id from WorkOrder w
            where w.orderProduct.customerOrder.id in :ids and w.user is null and w.commonCode.code = 'PREPARE'
            """)
    List<Long> findUnassignedOrders(@Param("ids") List<Long> orderIds);

    // 진행 상태별 전체 건수 조회
    @Query("""
            select o.statusCode.code, count(o) from CustomerOrder o
            where o.statusCode.codeGroup.id = 2 group by o.statusCode.code
            """)
    List<Object[]> countByStatus();

    // 사용 중인 수주 진행 상태 조회
    @Query("select c from CommonCode c where c.codeGroup.id = 2 and c.status = :status order by c.sortOrder, c.id")
    List<CommonCode> findStatuses(@Param("status") CodeStatus status);

    // 수주 진행 상태 ID 검증
    @Query("select count(c) from CommonCode c where c.codeGroup.id = 2 and c.id = :id")
    long countOrderStatus(@Param("id") Long id);

    // 이번 달 배송 완료 수주 건수 조회
    @Query("""
            select count(o) from CustomerOrder o join Shipment s on s.orderId = o.id
            where o.statusCode.codeGroup.id = 2 and o.statusCode.code = 'COMPLETED'
              and s.deliveredAt >= :start and s.deliveredAt < :end
            """)
    long countCompleted(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 견적에 연결된 수주 조회
    Optional<CustomerOrder> findByQuotationId(Long quotationId);

    // 견적 ID 목록에 연결된 수주 일괄 조회, 취소 수주 포함
    @EntityGraph(attributePaths = "quotation")
    List<CustomerOrder> findByQuotationIdIn(List<Long> quotationIds);

    // 배송 탭: 상태별 수주. 납기 빠른 순 (포장중 필터·건수용 전체 조회)
    @EntityGraph(attributePaths = {"client", "statusCode"})
    @Query("""
            SELECT o FROM CustomerOrder o
            WHERE o.statusCode.code = :status
            ORDER BY o.estimatedDueDate ASC
            """)
    List<CustomerOrder> findByStatusCodeOrderByEstimatedDueDateAsc(@Param("status") String status);

    // 배송 탭: 상태별 수주. 납기 빠른 순(정렬은 Pageable)
    @EntityGraph(attributePaths = {"client", "statusCode"})
    @Query("""
            SELECT o FROM CustomerOrder o
            WHERE o.statusCode.code = :status
            """)
    Page<CustomerOrder> findByStatusCode(@Param("status") String status, Pageable pageable);

    // 잔금 대기: 배송 완료인데 전액 입금이 아닌 수주
    @EntityGraph(attributePaths = {"client", "statusCode"})
    @Query("""
            SELECT o FROM CustomerOrder o
            WHERE o.statusCode.code = 'COMPLETED'
              AND o.paymentStatus <> :paid
            """)
    Page<CustomerOrder> findBalanceWaitingOrders(
            @Param("paid") OrderPaymentStatus paid,
            Pageable pageable
    );

    long countByStatusCode_Code(String code);

    long countByStatusCode_CodeAndPaymentStatusNot(String code, OrderPaymentStatus paymentStatus);

    // 특정 거래처의 누적 수주량
    Long countByClientId(Long clientId);

    // 특정 거래처의 누적 수주 금액
    @Query("""
                SELECT COALESCE(SUM(o.totalAmount), 0)
                FROM CustomerOrder o
                WHERE o.client.id = :clientId
                  AND o.statusCode.code IN ('COMPLETED')
            """)
    Long sumTotalAmountByClientId(@Param("clientId") Long clientId);

    // 특정 거래처의 현재 진행중인 수주량
    @Query("""
                SELECT COUNT(o)
                FROM CustomerOrder o
                WHERE o.client.id = :clientId
                  AND o.statusCode.code NOT IN ('COMPLETED', 'ON_HOLD', 'CANCELLED')
            """)
    Long countActiveOrdersByClientId(@Param("clientId") Long clientId);

    // 거래처의 최근 수주 조회
    Optional<CustomerOrder> findFirstByClient_IdOrderByCreatedAtDesc(Long clientId);

    // 거래처의 수주 이력 조회
    Page<CustomerOrder> findAllByClient_Id(Long clientId, Pageable pageable);

    // 최근 기간 동안 수주를 진행한 거래처의 수
    @Query(value = """
                SELECT COUNT(DISTINCT o.client_id)
                FROM customer_order o
                WHERE o.created_at > :dateTime
                    AND o.created_at <= NOW()
            """, nativeQuery = true)
    long countRecentOrderClients(
            @Param("dateTime") LocalDateTime dateTime
    );
}
