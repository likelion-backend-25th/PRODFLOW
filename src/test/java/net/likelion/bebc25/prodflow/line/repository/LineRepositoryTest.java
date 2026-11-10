package net.likelion.bebc25.prodflow.line.repository;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.line.dto.LineDto.LineList;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.line.repository.LineRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class LineRepositoryTest {

    @Autowired
    EntityManager em;

    @Autowired
    LineRepository lineRepository;

    @Test
    @DisplayName("등록된 라인 이름만 중복으로 본다")
    void existsByLineName() {
        assertThat(lineRepository.existsByLineName("A라인")).isTrue();
        assertThat(lineRepository.existsByLineName("없는라인")).isFalse();
    }

    @Test
    @DisplayName("라인 잠금 조회는 id 오름차순이다")
    void findAllForUpdateOrdersById() {
        List<Line> lines = lineRepository.findAllForUpdate();

        assertThat(lines).extracting(Line::getId).isSorted();
        assertThat(lines).extracting(Line::getLineName).contains("A라인", "E라인");
    }

    @Test
    @DisplayName("목록 조회는 생산 중인 작업을 붙이고 생산이 없는 라인은 남긴다")
    void findLineListKeepsIdleLineAndAttachesProduce() {
        Line idle = saveLine("조회-대기");
        Line producing = saveLine("조회-생산");
        WorkOrder workOrder = new WorkOrder(
                "WO-LINE-LIST",
                em.find(OrderProduct.class, 15L),
                producing,
                em.find(User.class, 4L),
                100,
                LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 11, 6),
                "목록 조회");
        workOrder.changeStatus(em.find(CommonCode.class, 17L));
        workOrder.setCreatedBy("서도윤");
        workOrder.setUpdatedBy("서도윤");
        em.persist(workOrder);
        em.flush();
        em.clear();

        List<LineList> rows = lineRepository.findLineList();

        LineList idleRow = row(rows, "조회-대기");
        assertThat(idleRow.status()).isEqualTo(LineStatus.AVAILABLE);
        assertThat(idleRow.workNo()).isNull();
        assertThat(idleRow.productName()).isNull();
        assertThat(idleRow.orderNo()).isNull();
        assertThat(idleRow.managerName()).isNull();

        LineList producingRow = row(rows, "조회-생산");
        assertThat(producingRow.lineId()).isEqualTo(producing.getId());
        assertThat(producingRow.workNo()).isEqualTo("WO-LINE-LIST");
        assertThat(producingRow.orderNo()).isEqualTo("PF-20261103-13");
        assertThat(producingRow.managerName()).isEqualTo("김태호");
        assertThat(idle.getId()).isNotNull();
    }

    private Line saveLine(String name) {
        Line line = new Line(name, 1000);
        line.setCreatedBy("서도윤");
        line.setUpdatedBy("서도윤");
        em.persist(line);
        em.flush();
        return line;
    }

    private LineList row(List<LineList> rows, String lineName) {
        return rows.stream()
                .filter(row -> lineName.equals(row.lineName()))
                .findFirst()
                .orElseThrow();
    }
}
