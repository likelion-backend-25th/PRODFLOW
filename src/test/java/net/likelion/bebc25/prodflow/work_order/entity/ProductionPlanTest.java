package net.likelion.bebc25.prodflow.work_order.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ProductionPlanTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 15);

    @Autowired
    EntityManager em;

    WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        Line line = new Line("계획-라인", 1000);
        line.setCreatedBy("서도윤");
        line.setUpdatedBy("서도윤");
        em.persist(line);
        workOrder = new WorkOrder(
                "WO-PLAN-001",
                em.find(OrderProduct.class, 15L),
                line,
                em.find(User.class, 4L),
                100,
                DAY,
                DAY,
                "계획 테스트");
        workOrder.changeStatus(em.find(CommonCode.class, 16L));
        workOrder.setCreatedBy("서도윤");
        workOrder.setUpdatedBy("서도윤");
        em.persist(workOrder);
        em.flush();
    }

    @Test
    @DisplayName("생산 계획은 작업지시의 수주 품목을 함께 저장한다")
    void saveWithOrderProductOfWorkOrder() {
        ProductionPlan plan = plan(DAY, 100);
        em.persist(plan);
        em.flush();
        em.clear();

        ProductionPlan saved = em.find(ProductionPlan.class, plan.getId());
        assertThat(saved.getWorkOrder().getId()).isEqualTo(workOrder.getId());
        assertThat(saved.getOrderProduct().getId()).isEqualTo(15L);
        assertThat(saved.getPlanDate()).isEqualTo(DAY);
        assertThat(saved.getQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("같은 작업지시의 같은 날짜는 저장하지 않는다")
    void rejectDuplicateDate() {
        em.persist(plan(DAY, 40));
        em.flush();

        assertThatThrownBy(() -> {
            em.persist(plan(DAY, 60));
            em.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("생산 계획 수량은 1 이상이다")
    void rejectNonPositiveQuantity() {
        ProductionPlan plan = plan(DAY, 100);

        assertThatThrownBy(() -> plan.changeQuantity(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("생산계획 수량은 1 이상이어야 합니다.");
        plan.changeQuantity(20);
        assertThat(plan.getQuantity()).isEqualTo(20);
    }

    private ProductionPlan plan(LocalDate date, int quantity) {
        ProductionPlan plan = new ProductionPlan(workOrder, date, quantity);
        plan.setCreatedBy("서도윤");
        plan.setUpdatedBy("서도윤");
        return plan;
    }
}
