package net.likelion.bebc25.prodflow.line.service;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.line.dto.LineDto.CreateRequest;
import net.likelion.bebc25.prodflow.line.dto.LineDto.UpdateStatusRequest;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.line.repsitory.LineRepository;
import net.likelion.bebc25.prodflow.line.serivce.LineService;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto;
import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.repsitory.ProductionPlanRepository;
import net.likelion.bebc25.prodflow.work_order.repsitory.WorkOrderRepository;
import net.likelion.bebc25.prodflow.work_order.serivce.WorkOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

// 애플리케이션 설정은 유지한다. 이 테스트만 테이블을 지우지 않고 data.sql도 다시 실행하지 않는다.
//@SpringBootTest(properties = {
//        "spring.jpa.hibernate.ddl-auto=validate",
//        "spring.sql.init.mode=never"
//})

// CI 에러 임시 해결 (@Sql 해당 부분 제거할 때 @Sql import 도 같이 삭제)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Sql("/data.sql")
@Transactional
class LineBreakRescheduleTest {

    // 2026-11-06은 금요일이다. 생산 시작은 예정일이 오늘보다 뒤면 거절되고, 토·일은 등록되지 않는다.
    private static final LocalDate PRODUCE_DAY = LocalDate.of(2026, 11, 6);
    private static final LocalDate FULL = LocalDate.of(2026, 12, 15);
    private static final LocalDate NEXT = LocalDate.of(2026, 12, 16);
    private static final Long ORDER_PRODUCT_ID = 15L;
    private static final Long ASSIGNEE_ID = 4L;
    private static final Long ACTOR_ID = 1L;

    @Autowired
    EntityManager em;

    @Autowired
    LineService lineService;

    @Autowired
    LineRepository lineRepository;

    @Autowired
    WorkOrderService workOrderService;

    @Autowired
    WorkOrderRepository workOrderRepository;

    @Autowired
    ProductionPlanRepository productionPlanRepository;

    @Nested
    @DisplayName("받을 라인이 있는 고장")
    class WhenAnotherLineHasRoom {

        Long sourceId;
        Long receiverId;
        Long workOrderId;
        Long behindId;

        @BeforeEach
        void setUp() {
            // 1. 라인 등록. 시드 라인은 후보에서 빼 이 두 라인만 남긴다.
            sourceId = lineService.create(new CreateRequest("사전-원래", 1000), ACTOR_ID).id();
            receiverId = lineService.create(new CreateRequest("사전-수신", 1000), ACTOR_ID).id();
            closeOtherLines(sourceId, receiverId);

            // 2. 수신 라인의 15일을 채워 원래 라인 작업이 16일로 밀리게 한다.
            createWork(receiverId, 1000, FULL, FULL);

            // 3. 원래 라인에 작업지시를 두 장 등록한 뒤 고장으로 옮긴다.
            workOrderId = createWork(sourceId, 100, FULL, FULL);
            behindId = createWork(sourceId, 100, FULL, FULL);
            lineService.changeStatus(sourceId, new UpdateStatusRequest(LineStatus.BROKEN), ACTOR_ID);

            // 캐시를 비워 이후 조회가 DB를 보게 한다.
            em.flush();
            em.clear();
        }

        @Test
        @DisplayName("원래 완료일이 꽉 차 있으면 다음 영업일로 옮기고 시작일을 그 날로 둔다")
        void shiftToNextOpenDay() {
            WorkOrder moved = workOrderRepository.findById(workOrderId).orElseThrow();

            assertThat(moved.getLine().getId()).isEqualTo(receiverId);
            assertThat(moved.getQuantity()).isEqualTo(100);
            assertThat(moved.getUser().getId()).isEqualTo(ASSIGNEE_ID);
            assertThat(moved.getPlannedStartDate()).isEqualTo(NEXT);
            assertThat(moved.getPlannedEndDate()).isEqualTo(NEXT);
            assertThat(productionPlanRepository.findByWorkOrder_Id(workOrderId))
                    .extracting(ProductionPlan::getPlanDate, ProductionPlan::getQuantity)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(NEXT, 100));
            assertThat(productionPlanRepository.sumQuantityByLineAndDate(sourceId, FULL)).isZero();
            assertThat(productionPlanRepository.sumQuantityByLineAndDate(receiverId, NEXT)).isEqualTo(200);
        }

        @Test
        @DisplayName("가동 가능으로 복구해도 작업지시는 옮겨 가지 않는다")
        void stayWhenSourceBecomesAvailable() {
            lineService.changeStatus(sourceId, new UpdateStatusRequest(LineStatus.AVAILABLE), ACTOR_ID);
            em.flush();
            em.clear();

            WorkOrder stayed = workOrderRepository.findById(workOrderId).orElseThrow();
            assertThat(lineRepository.findById(sourceId).orElseThrow().getStatus()).isEqualTo(LineStatus.AVAILABLE);
            assertThat(stayed.getLine().getId()).isEqualTo(receiverId);
            assertThat(stayed.getPlannedStartDate()).isEqualTo(NEXT);
        }

        @Test
        @DisplayName("다른 라인으로 수동 이동하면 밀린 날짜를 유지한다")
        void keepDelayedDateOnAnotherLine() {
            lineService.changeStatus(sourceId, new UpdateStatusRequest(LineStatus.AVAILABLE), ACTOR_ID);
            Long otherId = lineService.create(new CreateRequest("사전-다른", 1000), ACTOR_ID).id();

            workOrderService.update(workOrderId, new WorkOrderDto.UpdatedRequest(otherId, null), ACTOR_ID);
            em.flush();
            em.clear();

            WorkOrder kept = workOrderRepository.findById(workOrderId).orElseThrow();
            assertThat(kept.getLine().getId()).isEqualTo(otherId);
            assertThat(kept.getPlannedStartDate()).isEqualTo(NEXT);
            assertThat(kept.getPlannedEndDate()).isEqualTo(NEXT);
        }

        @Test
        @DisplayName("앞날이 비어 있는 복구 라인으로 옮기면 원래 시작일로 당긴다")
        void pullForwardWhenFrontIsEmpty() {
            lineService.changeStatus(sourceId, new UpdateStatusRequest(LineStatus.AVAILABLE), ACTOR_ID);
            workOrderService.update(workOrderId, new WorkOrderDto.UpdatedRequest(sourceId, null), ACTOR_ID);
            em.flush();
            em.clear();

            WorkOrder returned = workOrderRepository.findById(workOrderId).orElseThrow();
            assertThat(returned.getLine().getId()).isEqualTo(sourceId);
            assertThat(returned.getQuantity()).isEqualTo(100);
            assertThat(returned.getPlannedStartDate()).isEqualTo(FULL);
            assertThat(returned.getPlannedEndDate()).isEqualTo(FULL);
            assertThat(productionPlanRepository.findByWorkOrder_Id(workOrderId))
                    .extracting(ProductionPlan::getPlanDate, ProductionPlan::getQuantity)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(FULL, 100));
        }

        @Test
        @DisplayName("앞날에 작업이 있으면 밀린 날짜를 유지한다")
        void keepDelayedDateWhenFrontHasWork() {
            lineService.changeStatus(sourceId, new UpdateStatusRequest(LineStatus.AVAILABLE), ACTOR_ID);
            // 15일을 먼저 채운 뒤 남은 작업을 넣는다.
            workOrderService.update(workOrderId, new WorkOrderDto.UpdatedRequest(sourceId, null), ACTOR_ID);
            workOrderService.update(behindId, new WorkOrderDto.UpdatedRequest(sourceId, null), ACTOR_ID);
            em.flush();
            em.clear();

            WorkOrder behind = workOrderRepository.findById(behindId).orElseThrow();
            assertThat(behind.getLine().getId()).isEqualTo(sourceId);
            assertThat(behind.getPlannedStartDate()).isEqualTo(NEXT);
            assertThat(behind.getPlannedEndDate()).isEqualTo(NEXT);
        }
    }

    @Nested
    @DisplayName("받을 라인이 없는 고장")
    class WhenNoLineCanTakeWork {

        Long sourceId;
        Long workOrderId;

        @BeforeEach
        void setUp() {
            sourceId = lineService.create(new CreateRequest("사전-잔류", 1000), ACTOR_ID).id();
            closeOtherLines(sourceId);
            workOrderId = createWork(sourceId, 100, NEXT, NEXT);
            em.flush();
            em.clear();
        }

        @Test
        @DisplayName("고장 처리는 되고 작업지시와 일정은 그 라인에 남는다")
        void keepWorkOrderOnBrokenLine() {
            var updated = lineService.changeStatus(sourceId, new UpdateStatusRequest(LineStatus.BROKEN), ACTOR_ID);
            em.flush();
            em.clear();

            WorkOrder stayed = workOrderRepository.findById(workOrderId).orElseThrow();
            assertThat(updated.status()).isEqualTo(LineStatus.BROKEN);
            assertThat(lineRepository.findById(sourceId).orElseThrow().getStatus()).isEqualTo(LineStatus.BROKEN);
            assertThat(stayed.getLine().getId()).isEqualTo(sourceId);
            assertThat(stayed.getQuantity()).isEqualTo(100);
            assertThat(stayed.getPlannedStartDate()).isEqualTo(NEXT);
            assertThat(stayed.getPlannedEndDate()).isEqualTo(NEXT);
            assertThat(productionPlanRepository.sumQuantityByLineAndDate(sourceId, NEXT)).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("생산 중인 고장")
    class WhenWorkIsProducing {

        Long sourceId;
        Long receiverId;
        Long workOrderId;

        @BeforeEach
        void setUp() {
            sourceId = lineService.create(new CreateRequest("사전-생산", 1000), ACTOR_ID).id();
            receiverId = lineService.create(new CreateRequest("사전-생산수신", 1000), ACTOR_ID).id();
            closeOtherLines(sourceId, receiverId);
            workOrderId = createWork(sourceId, 100, PRODUCE_DAY, PRODUCE_DAY);
            workOrderService.changeStatus(
                    workOrderId, new WorkOrderDto.UpdateStatusRequest("PRODUCE", null), ACTOR_ID);
            em.flush();
            em.clear();
        }

        @Test
        @DisplayName("생산 작업도 다른 라인으로 옮기고 받는 라인은 가동 중이 된다")
        void moveProducingWork() {
            lineService.changeStatus(sourceId, new UpdateStatusRequest(LineStatus.BROKEN), ACTOR_ID);
            em.flush();
            em.clear();

            WorkOrder moved = workOrderRepository.findById(workOrderId).orElseThrow();
            assertThat(moved.getLine().getId()).isEqualTo(receiverId);
            assertThat(moved.getCommonCode().getCode()).isEqualTo("PRODUCE");
            assertThat(moved.getQuantity()).isEqualTo(100);
            assertThat(lineRepository.findById(sourceId).orElseThrow().getStatus()).isEqualTo(LineStatus.BROKEN);
            assertThat(lineRepository.findById(receiverId).orElseThrow().getStatus()).isEqualTo(LineStatus.OPERATING);
            assertThat(productionPlanRepository.sumQuantityByLineAndDate(sourceId, PRODUCE_DAY)).isZero();
            assertThat(productionPlanRepository.sumQuantityByLineAndDate(receiverId, PRODUCE_DAY)).isEqualTo(100);
        }
    }

    // 열어 둔 라인을 빼고 나머지는 고장으로 둔다. 상태 변경 서비스는 타지 않는다.
    private void closeOtherLines(Long... openLineIds) {
        for (Line line : lineRepository.findAll()) {
            if (Arrays.stream(openLineIds).noneMatch(openLineId -> openLineId.equals(line.getId()))) {
                line.changeStatus(LineStatus.BROKEN);
            }
        }
    }

    // 시드 품목 15, 담당자 4로 준비 작업지시를 등록한다.
    private Long createWork(Long lineId, int quantity, LocalDate start, LocalDate end) {
        return workOrderService.create(
                new WorkOrderDto.CreateRequest(ORDER_PRODUCT_ID, lineId, ASSIGNEE_ID, quantity, start, end),
                ACTOR_ID).getFirst().id();
    }
}
