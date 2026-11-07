package net.likelion.bebc25.prodflow.work_order.repository;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.repsitory.ProductionPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// CI 에러 임시 해결 (@Sql 해당 부분 제거할 때 @Sql import 도 같이 삭제)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Sql("/data.sql")
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

    @BeforeEach
    void setUp() {
        line = saveLine("계획-조회");
        User user = em.find(User.class, 4L);
        OrderProduct activeProduct = em.find(OrderProduct.class, 15L);
        OrderProduct cancelledProduct = em.find(OrderProduct.class, 13L);
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
    @DisplayName("재배치 대상은 준비만, 고장 대상은 준비와 생산이고 생산 중 개수는 생산만 센다")
    void findRescheduleAndBrokenTargets() {
        assertThat(productionPlanRepository.findRescheduleTargets(line.getId(), DAY))
                .extracting(plan -> plan.getWorkOrder().getWorkNo())
                .containsExactly("WO-PLAN-PREPARE");
        assertThat(productionPlanRepository.findBrokenTargets(line.getId(), DAY))
                .extracting(plan -> plan.getWorkOrder().getWorkNo())
                .containsExactly("WO-PLAN-PREPARE", "WO-PLAN-PRODUCE");
        assertThat(productionPlanRepository.countInProgressPlans(line.getId(), DAY)).isEqualTo(1);
        assertThat(productionPlanRepository.countInProgressPlans(line.getId(), NEXT.plusDays(1))).isZero();
    }

    @Test
    @DisplayName("작업지시별 계획과 품목 수량 합은 상태를 가리지 않는다")
    void findByWorkOrderAndSumByOrderProduct() {
        assertThat(productionPlanRepository.findByWorkOrder_Id(prepareWorkOrderId))
                .extracting(ProductionPlan::getPlanDate, ProductionPlan::getQuantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(DAY, 100));
        assertThat(productionPlanRepository.sumQuantityByOrderProductId(15L)).isEqualTo(190);
        assertThat(productionPlanRepository.sumQuantityByOrderProductId(13L)).isEqualTo(50);
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
