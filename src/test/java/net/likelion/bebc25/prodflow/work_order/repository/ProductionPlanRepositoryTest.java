package net.likelion.bebc25.prodflow.work_order.repository;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.repository.ProductionPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ProductionPlanRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 15);
    private static final LocalDate NEXT = LocalDate.of(2026, 12, 16);

    @Autowired
    EntityManager em;

    @Autowired
    ProductionPlanRepository productionPlanRepository;

    Line line;
    Long prepareWorkOrderId;
    long existingProductPlanQuantity;

    @BeforeEach
    void setUp() {
        existingProductPlanQuantity = productionPlanRepository.sumQuantityByOrderProductId(13L);
        line = saveLine("계획-조회");
        User user = em.find(User.class, 4L);
        OrderProduct activeProduct = em.find(OrderProduct.class, 13L);
        OrderProduct cancelledProduct = em.find(OrderProduct.class, 11L);
        prepareWorkOrderId = savePlan(activeProduct, user, "WO-PLAN-PREPARE", 16L, DAY, 100);
        savePlan(activeProduct, user, "WO-PLAN-HALT", 21L, DAY, 30);
        savePlan(activeProduct, user, "WO-PLAN-PACK", 18L, DAY, 40);
        savePlan(cancelledProduct, user, "WO-PLAN-CANCEL", 16L, DAY, 50);
        savePlan(activeProduct, user, "WO-PLAN-PRODUCE", 17L, NEXT, 20);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("라인 날짜 합은 포장과 취소 수주를 빼고 생산능력 초과일을 고른다")
    void sumAndFindDatesExceedingCapacity() {
        assertThat(productionPlanRepository.sumQuantityByLineAndDate(line.getId(), DAY)).isEqualTo(130);
        assertThat(productionPlanRepository.sumQuantityByLineAndDate(line.getId(), NEXT)).isEqualTo(20);
        assertThat(productionPlanRepository.findDatesExceedingCapacity(line.getId(), 100))
                .containsExactly(DAY);
        assertThat(productionPlanRepository.findDatesExceedingCapacity(line.getId(), 130)).isEmpty();
    }

    @Test
    @DisplayName("재배치 대상은 준비만이고 생산 중 개수는 생산만 센다")
    void findRescheduleAndBrokenTargets() {
        assertThat(productionPlanRepository.findRescheduleTargets(line.getId(), DAY))
                .extracting(plan -> plan.getWorkOrder().getWorkNo())
                .containsExactly("WO-PLAN-PREPARE");
        assertThat(productionPlanRepository.countInProgressPlans(line.getId(), DAY)).isEqualTo(1);
        assertThat(productionPlanRepository.countInProgressPlans(line.getId(), NEXT.plusDays(1))).isZero();
    }

    @Test
    @DisplayName("작업지시별 계획과 품목 수량 합은 상태를 가리지 않는다")
    void findByWorkOrderAndSumByOrderProduct() {
        assertThat(productionPlanRepository.findByWorkOrder_Id(prepareWorkOrderId))
                .extracting(ProductionPlan::getPlanDate, ProductionPlan::getQuantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(DAY, 100));
        assertThat(productionPlanRepository.sumQuantityByOrderProductId(13L))
                .isEqualTo(existingProductPlanQuantity + 190);
        assertThat(productionPlanRepository.sumQuantityByOrderProductId(11L)).isEqualTo(50);
    }

    private Line saveLine(String name) {
        Line line = new Line(name, 1000);
        line.setCreatedBy("서도윤");
        line.setUpdatedBy("서도윤");
        em.persist(line);
        em.flush();
        return line;
    }

    private Long savePlan(OrderProduct orderProduct, User user, String workNo, long statusId,
                          LocalDate date, int quantity) {
        WorkOrder workOrder = new WorkOrder(workNo, orderProduct, line, user, quantity, date, date, "계획 조회");
        workOrder.changeStatus(em.find(CommonCode.class, statusId));
        workOrder.setCreatedBy("서도윤");
        workOrder.setUpdatedBy("서도윤");
        em.persist(workOrder);
        ProductionPlan plan = new ProductionPlan(workOrder, date, quantity);
        plan.setCreatedBy("서도윤");
        plan.setUpdatedBy("서도윤");
        em.persist(plan);
        em.flush();
        return workOrder.getId();
    }
}
