package net.likelion.bebc25.prodflow.quotation.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.entity.holiday;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.workorder.entity.ProductionPlan;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class QuotationScheduleRepository {
    private final EntityManager entityManager;

    public List<CustomerOrder> findActiveOrders() {
        return entityManager.createQuery("""
                select o from CustomerOrder o join fetch o.statusCode
                where o.statusCode.code in ('WAITING', 'CONFIRMED', 'PRODUCTION_READY', 'IN_PRODUCTION', 'ON_HOLD')
                order by o.createdAt, o.id
                """, CustomerOrder.class).getResultList();
    }

    public List<OrderProduct> findOrderProducts(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                select p from OrderProduct p join fetch p.customerOrder
                where p.customerOrder.id in :ids
                """, OrderProduct.class).setParameter("ids", orderIds).getResultList();
    }

    public List<ProductionPlan> findFixedPlans(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                select p from ProductionPlan p
                join fetch p.workOrder w join fetch p.orderProduct op
                join fetch op.customerOrder o
                where o.id in :ids and o.statusCode.code in ('IN_PRODUCTION', 'ON_HOLD')
                and w.commonCode.code in ('PRODUCE', 'SUSPEND', 'PACK', 'PACK_DONE')
                """, ProductionPlan.class).setParameter("ids", orderIds).getResultList();
    }

    public List<LocalDate> findHolidays() {
        return entityManager.createQuery("select h from holiday h", holiday.class)
                .getResultStream().map(holiday::getHolidayDate).toList();
    }
}
