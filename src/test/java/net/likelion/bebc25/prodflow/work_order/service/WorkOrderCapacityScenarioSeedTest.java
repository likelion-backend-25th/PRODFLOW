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
    @DisplayName("1,300개 수주는 작업지시 두 장으로 나뉘고 이어진 200개 수주가 남은 라인에 배정된다")
    void seedSplitsThirteenHundredAndKeepsFourPrepareWorks() {
        CustomerOrder first = order("PF-20261113-217");
        CustomerOrder second = order("PF-20261113-218");
        CustomerOrder third = order("PF-20261113-219");

        assertThat(quantityOf(first)).isEqualTo(1300);
        assertThat(quantityOf(second)).isEqualTo(200);
        assertThat(quantityOf(third)).isEqualTo(200);

        Map<String, WorkOrder> works = works(
                "WO-2026-203", "WO-2026-204", "WO-2026-205", "WO-2026-206");

        assertThat(works.get("WO-2026-203").getQuantity()).isEqualTo(1000);
        assertThat(works.get("WO-2026-203").getLine().getLineName()).isEqualTo("A라인");
        assertThat(works.get("WO-2026-204").getQuantity()).isEqualTo(300);
        assertThat(works.get("WO-2026-204").getLine().getLineName()).isEqualTo("B라인");
        assertThat(works.get("WO-2026-203").getOrderProduct().getId())
                .isEqualTo(works.get("WO-2026-204").getOrderProduct().getId());
        assertThat(works.get("WO-2026-205").getQuantity()).isEqualTo(200);
        assertThat(works.get("WO-2026-205").getLine().getLineName()).isEqualTo("B라인");
        assertThat(works.get("WO-2026-206").getQuantity()).isEqualTo(200);
        assertThat(works.get("WO-2026-206").getLine().getLineName()).isEqualTo("D라인");
        assertThat(works.values()).allSatisfy(work -> {
            assertThat(work.getCommonCode().getCode()).isEqualTo("PREPARE");
            assertThat(work.getPlannedStartDate()).isEqualTo(TODAY);
            assertThat(work.getPlannedEndDate()).isEqualTo(TODAY);
        });
    }

    @Test
    @DisplayName("당일 계획은 A 1,000, B 500, D 200이고 C는 고장, E는 점검이다")
    void seedKeepsSameDayPlanAfterInspectionBreakdownAndDirectReturn() {
        List<ProductionPlan> plans = em.createQuery(
                        "select plan from ProductionPlan plan join fetch plan.workOrder workOrder join fetch workOrder.line "
                                + "where plan.planDate = :planDate",
                        ProductionPlan.class)
                .setParameter("planDate", TODAY)
                .getResultList();

        assertThat(plans).hasSize(4);
        assertThat(sum(plans, "A라인")).isEqualTo(1000);
        assertThat(sum(plans, "B라인")).isEqualTo(500);
        assertThat(sum(plans, "C라인")).isZero();
        assertThat(sum(plans, "D라인")).isEqualTo(200);
        assertThat(plans).extracting(plan -> plan.getQuantity()).containsExactlyInAnyOrder(1000, 300, 200, 200);

        assertThat(line("C라인").getStatus()).isEqualTo(LineStatus.BROKEN);
        assertThat(line("D라인").getStatus()).isEqualTo(LineStatus.AVAILABLE);
        assertThat(line("E라인").getStatus()).isEqualTo(LineStatus.INSPECTION);
    }

    @Test
    @DisplayName("D라인 작업만 점검과 고장을 거쳐 복구된 D라인으로 직접 돌아온다")
    void seedRecordsMoveHistoryForTheReturnedWork() {
        List<String> returned = reasons("WO-2026-206");
        List<String> movedFromBrokenLine = reasons("WO-2026-205");

        assertThat(returned).containsExactly(
                "라인 점검으로 재배치|2026-11-13|2026-11-13",
                "라인 고장으로 재배치|2026-11-13|2026-11-13",
                "복구된 라인으로 직접 배치");
        assertThat(movedFromBrokenLine).containsExactly(
                "라인 고장으로 재배치|2026-11-13|2026-11-13");
        assertThat(reasons("WO-2026-203")).isEmpty();
        assertThat(reasons("WO-2026-204")).isEmpty();
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
