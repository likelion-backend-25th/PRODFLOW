package net.likelion.bebc25.prodflow.work_order.repository;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrderHistory;
import net.likelion.bebc25.prodflow.work_order.repsitory.WorkOrderHistoryRepository;
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
class WorkOrderHistoryRepositoryTest {

    @Autowired
    EntityManager em;

    @Autowired
    WorkOrderHistoryRepository workOrderHistoryRepository;

    Long workOrderId;
    Long previousLineId;
    Long firstHistoryId;
    Long latestHistoryId;

    @BeforeEach
    void setUp() {
        Line previous = saveLine("이력-이전");
        Line middle = saveLine("이력-중간");
        Line latest = saveLine("이력-최근");
        User assignee = em.find(User.class, 4L);
        User actor = em.find(User.class, 1L);
        WorkOrder workOrder = new WorkOrder(
                "WO-HISTORY-REPO",
                em.find(OrderProduct.class, 15L),
                latest,
                assignee,
                100,
                LocalDate.of(2026, 12, 15),
                LocalDate.of(2026, 12, 15),
                "이력 조회");
        workOrder.changeStatus(em.find(CommonCode.class, 16L));
        workOrder.setCreatedBy("서도윤");
        workOrder.setUpdatedBy("서도윤");
        em.persist(workOrder);
        WorkOrderHistory firstHistory = new WorkOrderHistory(
                workOrder, previous, middle, assignee, assignee, actor, "첫 이동");
        em.persist(firstHistory);
        WorkOrderHistory latestHistory = new WorkOrderHistory(
                workOrder, previous, latest, assignee, assignee, actor, "둘째 이동");
        em.persist(latestHistory);
        em.persist(new WorkOrderHistory(
                workOrder, middle, latest, assignee, assignee, actor, "다른 라인"));
        em.flush();
        workOrderId = workOrder.getId();
        previousLineId = previous.getId();
        firstHistoryId = firstHistory.getId();
        latestHistoryId = latestHistory.getId();
        em.clear();
    }

    @Test
    @DisplayName("작업이 그 라인에서 빠진 이력만 최근 이동부터 조회한다")
    void findByWorkOrderAndPreviousLineOrderByIdDesc() {
        assertThat(workOrderHistoryRepository
                .findByWorkOrder_IdAndPreviousLine_IdOrderByIdDesc(workOrderId, previousLineId))
                .extracting(WorkOrderHistory::getId, WorkOrderHistory::getChangeReason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(latestHistoryId, "둘째 이동"),
                        org.assertj.core.groups.Tuple.tuple(firstHistoryId, "첫 이동"));
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
