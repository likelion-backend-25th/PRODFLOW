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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// CI 에러 임시 해결 (@Sql 해당 부분 제거할 때 @Sql import 도 같이 삭제)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Sql("/data.sql")
@Transactional
class WorkOrderTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 15);
    private static final LocalDate END = LocalDate.of(2026, 12, 16);

    @Autowired
    EntityManager em;

    Line line;
    Line otherLine;
    User assignee;
    User otherUser;
    OrderProduct orderProduct;
    CommonCode prepare;
    CommonCode pack;

    @BeforeEach
    void setUp() {
        line = saveLine("작업-원래");
        otherLine = saveLine("작업-이동");
        assignee = em.find(User.class, 4L);
        otherUser = em.find(User.class, 5L);
        orderProduct = em.find(OrderProduct.class, 15L);
        prepare = em.find(CommonCode.class, 16L);
        pack = em.find(CommonCode.class, 18L);
    }

    @Test
    @DisplayName("작업지시는 지시일과 관계를 저장하고 완료일은 비워 둔다")
    void saveWithoutCompletedAt() {
        WorkOrder workOrder = workOrder("WO-ENTITY-001", prepare);
        em.persist(workOrder);
        em.flush();
        em.clear();

        WorkOrder saved = em.find(WorkOrder.class, workOrder.getId());
        assertThat(saved.getWorkNo()).isEqualTo("WO-ENTITY-001");
        assertThat(saved.getQuantity()).isEqualTo(100);
        assertThat(saved.getWorkOrderDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getPlannedStartDate()).isEqualTo(START);
        assertThat(saved.getPlannedEndDate()).isEqualTo(END);
        assertThat(saved.getCompletedAt()).isNull();
        assertThat(saved.getOrderProduct().getId()).isEqualTo(15L);
        assertThat(saved.getLine().getId()).isEqualTo(line.getId());
        assertThat(saved.getUser().getId()).isEqualTo(4L);
        assertThat(saved.getCommonCode().getCode()).isEqualTo("PREPARE");
    }

    @Test
    @DisplayName("포장으로 바꾸면 완료일이 생기고 다른 상태는 완료일을 지우지 않는다")
    void completeOnlyWhenPacked() {
        WorkOrder workOrder = workOrder("WO-ENTITY-002", prepare);
        em.persist(workOrder);
        em.flush();

        workOrder.changeStatus(pack);
        em.flush();
        em.clear();

        WorkOrder packed = em.find(WorkOrder.class, workOrder.getId());
        assertThat(packed.getCommonCode().getCode()).isEqualTo("PACK");
        assertThat(packed.getCompletedAt()).isEqualTo(LocalDate.now());

        packed.changeStatus(em.find(CommonCode.class, 16L));
        em.flush();
        em.clear();

        WorkOrder prepared = em.find(WorkOrder.class, workOrder.getId());
        assertThat(prepared.getCommonCode().getCode()).isEqualTo("PREPARE");
        assertThat(prepared.getCompletedAt()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("라인과 담당자 변경이 저장된다")
    void changeLineAndUser() {
        WorkOrder workOrder = workOrder("WO-ENTITY-003", prepare);
        em.persist(workOrder);
        em.flush();

        workOrder.changeLine(otherLine);
        workOrder.changeUser(otherUser);
        em.flush();
        em.clear();

        WorkOrder saved = em.find(WorkOrder.class, workOrder.getId());
        assertThat(saved.getLine().getLineName()).isEqualTo("작업-이동");
        assertThat(saved.getUser().getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("수량은 1 이상이고 예정 시작일은 완료일보다 늦을 수 없다")
    void rejectInvalidQuantityAndPeriod() {
        WorkOrder workOrder = workOrder("WO-ENTITY-004", prepare);

        assertThatThrownBy(() -> workOrder.changeQuantity(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("작업지시 수량은 1 이상이어야 합니다.");
        assertThatThrownBy(() -> workOrder.changePlannedDates(END, START))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("생산 예정 기간이 올바르지 않습니다.");
        assertThatThrownBy(() -> workOrder.changePlannedDates(null, END))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("생산 예정 기간이 올바르지 않습니다.");

        workOrder.changeQuantity(1);
        workOrder.changePlannedDates(START, START);
        assertThat(workOrder.getQuantity()).isEqualTo(1);
        assertThat(workOrder.getPlannedEndDate()).isEqualTo(START);
    }

    @Test
    @DisplayName("같은 지시번호는 저장하지 않는다")
    void rejectDuplicateWorkNo() {
        em.persist(workOrder("WO-ENTITY-DUP", prepare));
        em.flush();

        assertThatThrownBy(() -> {
            em.persist(workOrder("WO-ENTITY-DUP", prepare));
            em.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    private Line saveLine(String name) {
        Line saved = new Line(name, 1000);
        saved.setCreatedBy("서도윤");
        saved.setUpdatedBy("서도윤");
        em.persist(saved);
        em.flush();
        return saved;
    }

    private WorkOrder workOrder(String workNo, CommonCode status) {
        WorkOrder workOrder = new WorkOrder(
                workNo, orderProduct, line, assignee, 100, START, END, "엔티티 테스트");
        workOrder.changeStatus(status);
        workOrder.setCreatedBy("서도윤");
        workOrder.setUpdatedBy("서도윤");
        return workOrder;
    }
}
