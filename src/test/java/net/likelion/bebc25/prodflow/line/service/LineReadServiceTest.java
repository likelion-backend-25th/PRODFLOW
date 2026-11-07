package net.likelion.bebc25.prodflow.line.service;

import net.likelion.bebc25.prodflow.line.dto.LineDto.CreateRequest;
import net.likelion.bebc25.prodflow.line.dto.LineDto.DetailResponse;
import net.likelion.bebc25.prodflow.line.dto.LineDto.DetailWorkOrder;
import net.likelion.bebc25.prodflow.line.dto.LineDto.LineList;
import net.likelion.bebc25.prodflow.line.dto.LineDto.ListResponse;
import net.likelion.bebc25.prodflow.line.dto.LineDto.UpdateRequest;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.line.serivce.LineService;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.repsitory.WorkOrderRepository;
import net.likelion.bebc25.prodflow.work_order.serivce.WorkOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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
class LineReadServiceTest {

    private static final Long ORDER_PRODUCT_ID = 15L;
    private static final Long ASSIGNEE_ID = 4L;
    private static final Long ACTOR_ID = 1L;
    // 2026-11-06은 금요일이다. 생산 시작은 예정일이 오늘보다 뒤면 거절되고, 토·일은 등록되지 않는다.
    private static final LocalDate PRODUCE_DAY = LocalDate.of(2026, 11, 6);
    private static final LocalDate SOON = LocalDate.of(2026, 12, 15);
    private static final LocalDate LATER = LocalDate.of(2026, 12, 16);
    private static final LocalDate HALTED_DAY = LocalDate.of(2026, 12, 17);

    @Autowired
    LineService lineService;

    @Autowired
    WorkOrderService workOrderService;

    @Autowired
    WorkOrderRepository workOrderRepository;

    int totalCount;
    int runningCount;
    int availableCount;
    int inspectionOrBrokenCount;
    Long producingLineId;
    Long idleLineId;
    String produceWorkNo;
    String soonWorkNo;
    String laterWorkNo;

    @BeforeEach
    void setUp() {
        ListResponse before = lineService.findAll();
        totalCount = before.totalCount();
        runningCount = before.runningCount();
        availableCount = before.availableCount();
        inspectionOrBrokenCount = before.inspectionOrBrokenCount();

        // 1. 생산 라인과 대기 라인을 등록한다.
        producingLineId = lineService.create(new CreateRequest("조회-생산", 1000), ACTOR_ID).id();
        idleLineId = lineService.create(new CreateRequest("조회-대기", 1000), ACTOR_ID).id();

        // 2. 생산, 준비 두 건, 목록에서 빠질 생산 중지를 등록한다.
        Long produceId = createWork(producingLineId, PRODUCE_DAY, PRODUCE_DAY);
        Long soonId = createWork(producingLineId, SOON, SOON);
        Long laterId = createWork(producingLineId, LATER, LATER);
        Long haltedId = createWork(producingLineId, HALTED_DAY, HALTED_DAY);
        workOrderService.changeStatus(produceId, new WorkOrderDto.UpdateStatusRequest("PRODUCE", null), ACTOR_ID);
        workOrderService.changeStatus(haltedId, new WorkOrderDto.UpdateStatusRequest("HALT", null), ACTOR_ID);

        produceWorkNo = workOrderRepository.findById(produceId).orElseThrow().getWorkNo();
        soonWorkNo = workOrderRepository.findById(soonId).orElseThrow().getWorkNo();
        laterWorkNo = workOrderRepository.findById(laterId).orElseThrow().getWorkNo();

        // 3. 상세에서 볼 생산량 이력을 남긴다.
        lineService.update(producingLineId, new UpdateRequest("조회-생산", 1200, "조회 테스트 증설"), ACTOR_ID);
    }

    @Test
    @DisplayName("목록은 생산 중인 작업 한 건을 붙이고 생산 중이 아닌 라인은 비워 둔다")
    void listShowsCurrentProduceAndKeepsIdleLines() {
        ListResponse response = lineService.findAll();
        WorkOrder produce = workOrderRepository.findById(
                workOrderRepository.findAll().stream()
                        .filter(workOrder -> produceWorkNo.equals(workOrder.getWorkNo()))
                        .findFirst()
                        .orElseThrow()
                        .getId()).orElseThrow();

        assertThat(response.totalCount()).isEqualTo(totalCount + 2);
        assertThat(response.runningCount()).isEqualTo(runningCount + 1);
        assertThat(response.availableCount()).isEqualTo(availableCount + 1);
        assertThat(response.inspectionOrBrokenCount()).isEqualTo(inspectionOrBrokenCount);

        LineList producing = lineOf(response, "조회-생산");
        assertThat(producing.status()).isEqualTo(LineStatus.OPERATING);
        assertThat(producing.productionCapacity()).isEqualTo(1200);
        assertThat(producing.workNo()).isEqualTo(produceWorkNo);
        assertThat(producing.productName()).isEqualTo(produce.getOrderProduct().getProductName());
        assertThat(producing.orderNo()).isEqualTo(produce.getOrderProduct().getCustomerOrder().getOrderNo());
        assertThat(producing.managerName()).isEqualTo(produce.getUser().getName());

        LineList idle = lineOf(response, "조회-대기");
        assertThat(idle.lineId()).isEqualTo(idleLineId);
        assertThat(idle.status()).isEqualTo(LineStatus.AVAILABLE);
        assertThat(idle.workNo()).isNull();
        assertThat(idle.productName()).isNull();
        assertThat(idle.orderNo()).isNull();
        assertThat(idle.managerName()).isNull();
    }

    @Test
    @DisplayName("상세의 현재 작업은 생산 중 한 건이고 작업 목록은 현재와 예정만 둔다")
    void detailKeepsCurrentAndScheduledWorks() {
        DetailResponse detail = lineService.findByLineId(producingLineId);
        WorkOrder produce = workOrderRepository.findAll().stream()
                .filter(workOrder -> produceWorkNo.equals(workOrder.getWorkNo()))
                .findFirst()
                .orElseThrow();

        assertThat(detail.lineName()).isEqualTo("조회-생산");
        assertThat(detail.status()).isEqualTo(LineStatus.OPERATING);
        assertThat(detail.productionCapacity()).isEqualTo(1200);
        assertThat(detail.workNo()).isEqualTo(produceWorkNo);
        assertThat(detail.productName()).isEqualTo(produce.getOrderProduct().getProductName());
        assertThat(detail.orderNo()).isEqualTo(produce.getOrderProduct().getCustomerOrder().getOrderNo());
        assertThat(detail.managerName()).isEqualTo(produce.getUser().getName());
        assertThat(detail.workOrderCount()).isEqualTo(3);
        assertThat(detail.waitingCount()).isEqualTo(2);
        assertThat(detail.workOrders()).extracting(DetailWorkOrder::workNo)
                .containsExactly(produceWorkNo, soonWorkNo, laterWorkNo);
        assertThat(detail.workOrders()).extracting(DetailWorkOrder::status)
                .containsExactly("PRODUCE", "PREPARE", "PREPARE");
        assertThat(detail.capacityLogs()).singleElement().satisfies(log -> {
            assertThat(log.beforeCapacity()).isEqualTo(1000);
            assertThat(log.afterCapacity()).isEqualTo(1200);
            assertThat(log.reason()).isEqualTo("조회 테스트 증설");
            assertThat(log.managerName()).isEqualTo("서도윤");
        });
    }

    private Long createWork(Long lineId, LocalDate start, LocalDate end) {
        return workOrderService.create(
                new WorkOrderDto.CreateRequest(ORDER_PRODUCT_ID, lineId, ASSIGNEE_ID, 100, start, end),
                ACTOR_ID).getFirst().id();
    }

    private LineList lineOf(ListResponse response, String lineName) {
        return response.lines().stream()
                .filter(line -> lineName.equals(line.lineName()))
                .findFirst()
                .orElseThrow();
    }
}
