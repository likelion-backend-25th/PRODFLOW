package net.likelion.bebc25.prodflow.quotation.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;

public interface QuotationScheduleRepository extends Repository<CustomerOrder, Long> {

    // 생산량 반영 대상 수주 조회
    @Query("""
            select o from CustomerOrder o join fetch o.statusCode
            where o.statusCode.code in ('WAITING', 'CONFIRMED', 'PRODUCTION_READY', 'IN_PRODUCTION', 'ON_HOLD')
            order by o.createdAt, o.id
            """)
    List<CustomerOrder> findActiveOrders();

    // 수주 품목 조회 전 ID 목록 확인
    default List<OrderProduct> findOrderProducts(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return findOrderProductsByOrderIds(orderIds);
    }

    // 수주 ID별 품목 조회
    @Query("""
            select p from OrderProduct p join fetch p.customerOrder
            where p.customerOrder.id in :ids
            """)
    List<OrderProduct> findOrderProductsByOrderIds(@Param("ids") List<Long> orderIds);

    // 확정 일정 조회 전 ID 목록 확인
    default List<ProductionPlan> findFixedPlans(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return findFixedPlansByOrderIds(orderIds);
    }

    // 수주 ID별 확정 생산 일정 조회
    @Query("""
            select p from ProductionPlan p
            join fetch p.workOrder w join fetch p.orderProduct op
            join fetch op.customerOrder o
            where o.id in :ids and o.statusCode.code in ('IN_PRODUCTION', 'ON_HOLD')
            and w.commonCode.code in ('PRODUCE', 'SUSPEND', 'PACK', 'PACK_DONE')
            """)
    List<ProductionPlan> findFixedPlansByOrderIds(@Param("ids") List<Long> orderIds);

    // 공휴일 날짜 조회
    @Query("select h.holidayDate from Holiday h")
    List<LocalDate> findHolidays();
}
