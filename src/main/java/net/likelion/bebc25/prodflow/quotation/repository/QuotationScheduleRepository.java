package net.likelion.bebc25.prodflow.quotation.repository;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.workorder.entity.ProductionPlan;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface QuotationScheduleRepository extends Repository<CustomerOrder, Long> {

    @Query("""
            select o from CustomerOrder o join fetch o.statusCode
            where o.statusCode.code in ('WAITING', 'CONFIRMED', 'PRODUCTION_READY', 'IN_PRODUCTION', 'ON_HOLD')
            order by o.createdAt, o.id
            """)
    List<CustomerOrder> findActiveOrders();

    default List<OrderProduct> findOrderProducts(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return findOrderProductsByOrderIds(orderIds);
    }

    @Query("""
            select p from OrderProduct p join fetch p.customerOrder
            where p.customerOrder.id in :ids
            """)
    List<OrderProduct> findOrderProductsByOrderIds(@Param("ids") List<Long> orderIds);

    default List<ProductionPlan> findFixedPlans(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return findFixedPlansByOrderIds(orderIds);
    }

    @Query("""
            select p from ProductionPlan p
            join fetch p.workOrder w join fetch p.orderProduct op
            join fetch op.customerOrder o
            where o.id in :ids and o.statusCode.code in ('IN_PRODUCTION', 'ON_HOLD')
            and w.commonCode.code in ('PRODUCE', 'SUSPEND', 'PACK', 'PACK_DONE')
            """)
    List<ProductionPlan> findFixedPlansByOrderIds(@Param("ids") List<Long> orderIds);

    @Query("select h.holidayDate from holiday h")
    List<LocalDate> findHolidays();
}
