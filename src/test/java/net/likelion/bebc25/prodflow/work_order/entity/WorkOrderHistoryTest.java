package net.likelion.bebc25.prodflow.work_order.entity;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.user.entity.User;
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
class WorkOrderHistoryTest {

    @Autowired
    EntityManager em;

    WorkOrder workOrder;
    Line previousLine;
    Line newLine;
    User previousUser;
    User newUser;
    User changedBy;

    @BeforeEach
    void setUp() {
        previousLine = saveLine("이력-이전");
        newLine = saveLine("이력-이후");
        previousUser = em.find(User.class, 4L);
        newUser = em.find(User.class, 5L);
        changedBy = em.find(User.class, 1L);
        workOrder = new WorkOrder(
                "WO-HISTORY-001",
                em.find(OrderProduct.class, 15L),
                previousLine,
                previousUser,
                100,
                LocalDate.of(2026, 12, 15),
                LocalDate.of(2026, 12, 15),
                "이력 테스트");
        workOrder.changeStatus(em.find(CommonCode.class, 16L));
        workOrder.setCreatedBy("서도윤");
        workOrder.setUpdatedBy("서도윤");
        em.persist(workOrder);
        em.flush();
    }

    @Test
    @DisplayName("작업지시 변경 이력은 이전과 이후 라인, 담당자, 사유를 저장한다")
    void saveLineAndAssigneeChange() {
        WorkOrderHistory history = new WorkOrderHistory(
                workOrder, previousLine, newLine, previousUser, newUser, changedBy, "라인 변경");
        em.persist(history);
        em.flush();
        em.clear();

        WorkOrderHistory saved = em.find(WorkOrderHistory.class, history.getId());
        assertThat(saved.getWorkOrder().getWorkNo()).isEqualTo("WO-HISTORY-001");
        assertThat(saved.getPreviousLine().getLineName()).isEqualTo("이력-이전");
        assertThat(saved.getNewLine().getLineName()).isEqualTo("이력-이후");
        assertThat(saved.getPreviousUser().getId()).isEqualTo(4L);
        assertThat(saved.getNewUser().getId()).isEqualTo(5L);
        assertThat(saved.getChangedBy().getName()).isEqualTo("서도윤");
        assertThat(saved.getChangeReason()).isEqualTo("라인 변경");
        assertThat(saved.getChangedAt()).isNotNull();
    }

    private Line saveLine(String name) {
        Line line = new Line(name, 1000);
        line.setCreatedBy("서도윤");
        line.setUpdatedBy("서도윤");
        em.persist(line);
        em.flush();
        return line;
    }
}
