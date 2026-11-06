package net.likelion.bebc25.prodflow.customer_order.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.shipment.entity.Shipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
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
}
