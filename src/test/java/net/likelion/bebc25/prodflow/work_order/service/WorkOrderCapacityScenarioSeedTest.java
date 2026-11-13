package net.likelion.bebc25.prodflow.work_order.service;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrderHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class WorkOrderCapacityScenarioSeedTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("1,300개 생산대기 수주는 B·D라인 작업지시 두 장으로 나뉜다")
    void seedSplitsThirteenHundredAndKeepsFourPrepareWorks() {
        CustomerOrder first = order("PF-20261110-1");
        assertThat(quantityOf(first)).isEqualTo(1300);
        Map<String, WorkOrder> works = works("WO-2026-001", "WO-2026-002");
        assertThat(works).hasSize(2);
        assertThat(works.get("WO-2026-001").getQuantity()).isEqualTo(1000);
        assertThat(works.get("WO-2026-002").getQuantity()).isEqualTo(300);
        assertThat(works.get("WO-2026-001").getLine().getLineName()).isEqualTo("B라인");
        assertThat(works.get("WO-2026-002").getLine().getLineName()).isEqualTo("D라인");
        assertThat(works.get("WO-2026-002").getUser()).isNull();
        assertThat(works.values()).allSatisfy(work -> {
            assertThat(work.getOrderProduct().getCustomerOrder().getId()).isEqualTo(first.getId());
            assertThat(work.getCommonCode().getCode()).isEqualTo("PREPARE");
            assertThat(work.getPlannedStartDate()).isEqualTo(TODAY.plusDays(3));
        });
    }

    @Test
    @DisplayName("당일 계획은 A 200개·B 100개이고 당일착수 여유를 남긴다")
    void seedKeepsSameDayPlanAfterInspectionBreakdownAndDirectReturn() {
        List<ProductionPlan> plans = em.createQuery(
                "select p from ProductionPlan p join fetch p.workOrder w join fetch w.line where p.planDate=:date", ProductionPlan.class)
                .setParameter("date", TODAY).getResultList();
        assertThat(plans).hasSize(2);
        assertThat(sum(plans, "A라인")).isEqualTo(200);
        assertThat(sum(plans, "B라인")).isEqualTo(100);
        assertThat(sum(plans, "C라인")).isZero();
        assertThat(sum(plans, "D라인")).isZero();
        assertThat(line("A라인").getStatus()).isEqualTo(LineStatus.OPERATING);
        assertThat(line("B라인").getStatus()).isEqualTo(LineStatus.AVAILABLE);
        assertThat(line("C라인").getStatus()).isEqualTo(LineStatus.BROKEN);
        assertThat(line("D라인").getStatus()).isEqualTo(LineStatus.AVAILABLE);
        assertThat(line("E라인").getStatus()).isEqualTo(LineStatus.INSPECTION);
    }

    @Test
    @DisplayName("다음 달 작업은 준비 상태이며 생산·완료 이력이 없다")
    void seedRecordsMoveHistoryForTheReturnedWork() {
        WorkOrder future = works("WO-2026-012").get("WO-2026-012");
        assertThat(future.getOrderProduct().getCustomerOrder().getId()).isEqualTo(20L);
        assertThat(future.getPlannedStartDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(future.getCommonCode().getCode()).isEqualTo("PREPARE");
        assertThat(future.getCompletedAt()).isNull();
        Long completedEvents = em.createQuery("select count(h) from StatusHistory h where h.entityId=:id and h.entity=net.likelion.bebc25.prodflow.common.enums.HistoryEntityType.WORK_ORDER and h.toStatus in ('PRODUCE','PACK','PACK_DONE')", Long.class)
                .setParameter("id", future.getId()).getSingleResult();
        assertThat(completedEvents).isZero();
    }

    private CustomerOrder order(String orderNo) {
        return em.createQuery("select customerOrder from CustomerOrder customerOrder where customerOrder.orderNo = :orderNo",
                        CustomerOrder.class)
                .setParameter("orderNo", orderNo)
                .getSingleResult();
    }

    private int quantityOf(CustomerOrder customerOrder) {
        return em.createQuery("select orderProduct from OrderProduct orderProduct where orderProduct.customerOrder = :customerOrder",
                        OrderProduct.class)
                .setParameter("customerOrder", customerOrder)
                .getResultList()
                .stream()
                .mapToInt(OrderProduct::getQuantity)
                .sum();
    }

    private Map<String, WorkOrder> works(String... workNos) {
        List<WorkOrder> found = em.createQuery(
                        "select workOrder from WorkOrder workOrder join fetch workOrder.line join fetch workOrder.commonCode "
                                + "join fetch workOrder.orderProduct where workOrder.workNo in :workNos",
                        WorkOrder.class)
                .setParameter("workNos", List.of(workNos))
                .getResultList();
        return found.stream().collect(Collectors.toMap(WorkOrder::getWorkNo, Function.identity()));
    }

    private int sum(List<ProductionPlan> plans, String lineName) {
        return plans.stream()
                .filter(plan -> lineName.equals(plan.getWorkOrder().getLine().getLineName()))
                .mapToInt(ProductionPlan::getQuantity)
                .sum();
    }

    private Line line(String lineName) {
        return em.createQuery("select line from Line line where line.lineName = :lineName", Line.class)
                .setParameter("lineName", lineName)
                .getSingleResult();
    }

    private List<String> reasons(String workNo) {
        return em.createQuery(
                        "select history from WorkOrderHistory history where history.workNo = :workNo order by history.id",
                        WorkOrderHistory.class)
                .setParameter("workNo", workNo)
                .getResultList()
                .stream()
                .map(WorkOrderHistory::getChangeReason)
                .toList();
    }
}
