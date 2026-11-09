package net.likelion.bebc25.prodflow.work_order.repository;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class WorkOrderRepositoryTest {

    private static final LocalDate SOON = LocalDate.of(2026, 12, 15);
    private static final LocalDate LATER = LocalDate.of(2026, 12, 16);

    @Autowired
    EntityManager em;

    @Autowired
    WorkOrderRepository workOrderRepository;

    Line line;
    long prepareQuantityBefore;
    Long prepareId;
    Long produceId;

    @BeforeEach
    void setUp() {
        prepareQuantityBefore = workOrderRepository.sumPrepareQuantityByUserId(4L);
        line = saveLine("저장소-라인");
        prepareId = saveWork("WO-REPO-PREPARE", 16L, 100, SOON, SOON).getId();
        produceId = saveWork("WO-REPO-PRODUCE", 17L, 50, LATER, LATER).getId();
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("목록 검색은 라인, 상태, 지시일, 수주번호를 함께 거른다")
    void searchByLineStatusDateAndOrderNo() {
        LocalDate today = LocalDate.now();

        assertThat(workOrderRepository.search(
                        line.getId(), "PREPARE", today, today, "PF-20261103-13", PageRequest.of(0, 5))
                .getContent())
                .extracting(WorkOrder::getWorkNo)
                .containsExactly("WO-REPO-PREPARE");
        assertThat(workOrderRepository.search(
                        line.getId(), "PRODUCE", null, null, "없는수주", PageRequest.of(0, 5))
                .getContent())
                .isEmpty();

        assertThat(workOrderRepository.searchPage(
                        line.getId(), "PREPARE", today, today, "PF-20261103-13", PageRequest.of(0, 5))
                .getContent())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.workNo()).isEqualTo("WO-REPO-PREPARE");
                    assertThat(row.orderNo()).isEqualTo("PF-20261103-13");
                    assertThat(row.lineName()).isEqualTo("저장소-라인");
                    assertThat(row.assignedName()).isEqualTo("김태호");
                    assertThat(row.status()).isEqualTo("PREPARE");
                    assertThat(row.quantity()).isEqualTo(100);
                    assertThat(row.workOrderDate()).isEqualTo(today);
                });
    }

    @Test
    @DisplayName("같은 라인의 상태 존재와 상태 목록 조회는 자기 자신을 제외할 수 있다")
    void existsAndFindByLineStatus() {
        assertThat(workOrderRepository.existsByLine_IdAndCommonCode_CodeAndIdNot(
                line.getId(), "PRODUCE", prepareId)).isTrue();
        assertThat(workOrderRepository.existsByLine_IdAndCommonCode_CodeAndIdNot(
                line.getId(), "PRODUCE", produceId)).isFalse();
        assertThat(workOrderRepository.findByLine_IdAndCommonCode_CodeIn(line.getId(), List.of("PRODUCE")))
                .extracting(WorkOrder::getWorkNo)
                .containsExactly("WO-REPO-PRODUCE");
    }

    @Test
    @DisplayName("지시번호, 담당자 생산, 준비 수량 합을 조회한다")
    void findWorkNoAssigneeAndPrepareQuantity() {
        assertThat(workOrderRepository.existsByWorkNo("WO-REPO-PREPARE")).isTrue();
        assertThat(workOrderRepository.existsByWorkNo("WO-REPO-NONE")).isFalse();
        assertThat(workOrderRepository.existsByUser_IdAndCommonCode_Code(4L, "PRODUCE")).isTrue();
        assertThat(workOrderRepository.existsByUser_IdAndCommonCode_Code(1L, "PRODUCE")).isFalse();
        assertThat(workOrderRepository.sumPrepareQuantityByUserId(4L))
                .isEqualTo(prepareQuantityBefore + 100);
    }

    @Test
    @DisplayName("같은 수주의 다른 작업 상태를 자기 자신을 빼고 확인한다")
    void existsByCustomerOrderStatus() {
        Long customerOrderId = em.find(OrderProduct.class, 15L).getCustomerOrder().getId();

        assertThat(workOrderRepository.existsByOrderProduct_CustomerOrder_IdAndCommonCode_CodeAndIdNot(
                customerOrderId, "PRODUCE", prepareId)).isTrue();
        assertThat(workOrderRepository.existsByOrderProduct_CustomerOrder_IdAndCommonCode_CodeNotInAndIdNot(
                customerOrderId, List.of("PACK", "PACK_DONE"), produceId)).isTrue();
        assertThat(workOrderRepository.existsByOrderProduct_CustomerOrder_IdAndCommonCode_CodeNotInAndIdNot(
                customerOrderId, List.of("PREPARE", "PRODUCE", "PACK", "PACK_DONE"), prepareId)).isFalse();
    }

    private Line saveLine(String name) {
        Line line = new Line(name, 1000);
        line.setCreatedBy("서도윤");
        line.setUpdatedBy("서도윤");
        em.persist(line);
        em.flush();
        return line;
    }

    private WorkOrder saveWork(String workNo, long statusId, int quantity, LocalDate start, LocalDate end) {
        WorkOrder workOrder = new WorkOrder(
                workNo,
                em.find(OrderProduct.class, 15L),
                line,
                em.find(User.class, 4L),
                quantity,
                start,
                end,
                "저장소 테스트");
        workOrder.changeStatus(em.find(CommonCode.class, statusId));
        workOrder.setCreatedBy("서도윤");
        workOrder.setUpdatedBy("서도윤");
        em.persist(workOrder);
        em.flush();
        return workOrder;
    }
}
