package net.likelion.bebc25.prodflow.line.service;

import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.line.dto.LineDto.CreateRequest;
import net.likelion.bebc25.prodflow.line.dto.LineDto.Response;
import net.likelion.bebc25.prodflow.line.dto.LineDto.UpdateRequest;
import net.likelion.bebc25.prodflow.line.dto.LineDto.UpdateStatusRequest;
import net.likelion.bebc25.prodflow.line.entity.LineCapacityLog;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.line.repository.LineCapacityLogRepository;
import net.likelion.bebc25.prodflow.line.repository.LineRepository;
import net.likelion.bebc25.prodflow.line.service.LineService;
import net.likelion.bebc25.prodflow.line_notification.repository.LineNotificationRepository;
import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.repository.ProductionPlanRepository;
import net.likelion.bebc25.prodflow.work_order.repository.WorkOrderRepository;
import net.likelion.bebc25.prodflow.work_order.service.WorkOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional // 테스트가 끝나면 롤백해서 시드 라인 5건을 유지한다
class LineServiceTest {

    @Autowired
    LineService lineService;

    @Autowired
    LineRepository lineRepository;

    @Autowired
    LineCapacityLogRepository lineCapacityLogRepository;

    @Autowired
    StatusHistoryRepository statusHistoryRepository;

    @Autowired
    LineNotificationRepository lineNotificationRepository;

    @Autowired
    WorkOrderService workOrderService;

    @Autowired
    WorkOrderRepository workOrderRepository;

    @Autowired
    ProductionPlanRepository productionPlanRepository;

    @Test
    @DisplayName("라인 등록 시 이름, 생산량, 초기 상태 AVAILABLE이 저장된다")
    void createLine() {
        // data.sql에 없는 이름. 화면 기본 생산량은 500이다.
        CreateRequest request = new CreateRequest("F라인", 500);

        // 시드 직원 id 1의 이름은 서도윤이다. 토큰 없이 서비스에 id를 직접 넘긴다.
        Response created = lineService.create(request, 1L);

        // IDENTITY라서 INSERT 후 id가 채워져야 한다.
        assertThat(created.id()).isNotNull();
        assertThat(created.lineName()).isEqualTo("F라인");
        assertThat(created.productionCapacity()).isEqualTo(500);
        // 엔티티 기본값이 AVAILABLE이다. 요청에는 상태가 없다.
        assertThat(created.status()).isEqualTo(LineStatus.AVAILABLE);
        // 도입 일시는 createdAt으로 자동 기록된다.
        assertThat(lineRepository.findById(created.id())).isPresent();
        assertThat(lineRepository.findById(created.id()).orElseThrow().getCreatedAt()).isNotNull();
        // 토큰 id 1의 User.name이 작성자로 저장된다.
        assertThat(lineRepository.findById(created.id()).orElseThrow().getCreatedBy()).isEqualTo("서도윤");

        // 시드 5건 + 방금 등록 1건
        assertThat(lineRepository.count()).isEqualTo(6);
    }

    @Test
    @DisplayName("이름만 바꾸면 생산량과 이력은 그대로다")
    void updateNameOnly() {
        // 시드 A라인의 생산량 1000은 유지한다. 이력은 생산량이 바뀔 때만 생긴다.
        UpdateRequest request = new UpdateRequest("A라인-개정", 1000, null);
        long logCount = lineCapacityLogRepository.count();

        // 직원 1은 서도윤. 컨트롤러가 토큰에서 꺼낼 id를 테스트에서는 직접 넘긴다.
        Response updated = lineService.update(1L, request, 1L);

        assertThat(updated.lineName()).isEqualTo("A라인-개정");
        assertThat(updated.productionCapacity()).isEqualTo(1000);
        // 상태 변경 API가 아니므로 시드 상태 OPERATING이 유지된다.
        assertThat(updated.status()).isEqualTo(LineStatus.OPERATING);
        assertThat(lineCapacityLogRepository.count()).isEqualTo(logCount);
        // 이름만 바꾸는 분기는 setUpdatedBy를 호출하지 않아 시드 값 system이 남는다.
        assertThat(lineRepository.findById(1L).orElseThrow().getUpdatedBy()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("기존 계획이 새 생산능력보다 크면 생산량을 줄이지 않는다")
    void rejectCapacityDecreaseWhenPlansExceed() {
        UpdateRequest request = new UpdateRequest("A라인", 100, "설비 점검으로 감축");
        long logCount = lineCapacityLogRepository.count();

        assertThatThrownBy(() -> lineService.update(1L, request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("기존 생산계획이 새 생산능력을 초과합니다.");
        assertThat(lineRepository.findById(1L).orElseThrow().getProductionCapacity()).isEqualTo(1000);
        assertThat(lineCapacityLogRepository.count()).isEqualTo(logCount);
    }

    @Test
    @DisplayName("이름과 생산량을 같이 바꾸면 이력과 라인 수정자 이름이 같이 저장된다")
    void updateNameAndCapacity() {
        UpdateRequest request = new UpdateRequest("A라인-확장", 1200, "설비 증설");

        Response updated = lineService.update(1L, request, 1L);

        assertThat(updated.lineName()).isEqualTo("A라인-확장");
        assertThat(updated.productionCapacity()).isEqualTo(1200);
        assertThat(lineRepository.findById(1L).orElseThrow().getUpdatedBy()).isEqualTo("서도윤");

        LineCapacityLog saved = lineCapacityLogRepository.findAll().stream()
                .filter(log -> log.getLine().getId().equals(1L) && log.getAfterCapacity() == 1200)
                .findFirst()
                .orElseThrow();
        assertThat(saved.getBeforeCapacity()).isEqualTo(1000);
        assertThat(saved.getReason()).isEqualTo("설비 증설");
        assertThat(saved.getChangedBy().getName()).isEqualTo("서도윤");
    }

    @Test
    @DisplayName("없는 라인을 수정하면 400으로 매핑될 예외가 난다")
    void updateMissingLine() {
        UpdateRequest request = new UpdateRequest("없는라인", 1000, null);

        assertThatThrownBy(() -> lineService.update(999L, request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("라인을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("없는 직원 id로 수정하면 400으로 매핑될 예외가 난다")
    void updateMissingUser() {
        UpdateRequest request = new UpdateRequest("A라인", 1000, null);

        assertThatThrownBy(() -> lineService.update(1L, request, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수정한 직원을 찾을 수 없습니다.");
    }


    @Test
    @DisplayName("라인 상태를 바꾸면 enum 이름이 status_history에 남는다")
    void changeLineStatus() {
        // 시드 A라인은 오늘 이후 준비가 있어, 다른 가동 라인이 꽉 차면 점검으로 바꾸지 못한다.
        // 계획이 없는 라인을 가동 중으로 둔 뒤 점검으로 바꾼다.
        Response created = lineService.create(new CreateRequest("이력-점검", 1000), 1L);
        lineService.changeStatus(created.id(), new UpdateStatusRequest(LineStatus.OPERATING), 1L);
        Response updated = lineService.changeStatus(created.id(), new UpdateStatusRequest(LineStatus.INSPECTION), 1L);

        assertThat(updated.status()).isEqualTo(LineStatus.INSPECTION);
        assertThat(lineRepository.findById(created.id()).orElseThrow().getUpdatedBy()).isEqualTo("SYSTEM");

        // 방금 저장한 상태 변경 이력을 ID 기준으로 조회한다.
        StatusHistory saved = statusHistoryRepository.findAll().stream()
                .filter(history -> history.getEntity() == HistoryEntityType.LINE
                        && history.getEntityId().equals(created.id())
                        && "INSPECTION".equals(history.getToStatus()))
                .max((a, b) -> a.getId().compareTo(b.getId()))
                .orElseThrow();

        // name() 결과가 이력의 변경 전, 변경 후 문자열이다.
        assertThat(saved.getFromStatus()).isEqualTo("OPERATING");
        assertThat(saved.getToStatus()).isEqualTo("INSPECTION");
        assertThat(saved.getUserName()).isEqualTo("서도윤");
        assertThat(saved.getUser().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 라인 상태로 바꾸면 이력을 남기지 않는다")
    void changeLineStatusToSameValue() {
        long historyCount = statusHistoryRepository.count();
        long noticeCount = lineNotificationRepository.count();

        assertThatThrownBy(() -> lineService.changeStatus(1L, new UpdateStatusRequest(LineStatus.OPERATING), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 같은 상태입니다.");
        assertThat(statusHistoryRepository.count()).isEqualTo(historyCount);
        assertThat(lineNotificationRepository.count()).isEqualTo(noticeCount);
    }

    @Test
    @DisplayName("줄어든 생산능력이 기간 안에 부족하면 다른 라인으로 옮기지 않고 거절한다")
    void rejectCapacityDecreaseWhenThePeriodCannotHoldTheQuantity() {
        // 시드 라인은 지난 계획이 1000이라 용량을 줄이면 거절된다. 빈 라인을 하나 만든다.
        Response createdLine = lineService.create(new CreateRequest("재배치-감축", 1000), 1L);
        // 12월 15일은 화요일이고 공휴일이 아니다. 오늘보다 뒤라 재배치 대상이다.
        LocalDate day = LocalDate.of(2026, 12, 15);
        long logCount = lineCapacityLogRepository.count();
        Long workOrderId = workOrderService.create(
                new net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto.CreateRequest(
                        13L, createdLine.id(), 4L, 100, day, day), 1L).getFirst().id();

        // 하루짜리 100은 생산능력 40에 들어가지 않는다. 라인 변경은 하지 않는다.
        assertThatThrownBy(() -> lineService.update(
                createdLine.id(), new UpdateRequest("재배치-감축", 40, "설비 감축"), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("기존 생산계획이 새 생산능력을 초과합니다.");

        WorkOrder stayed = workOrderRepository.findById(workOrderId).orElseThrow();
        assertThat(stayed.getLine().getId()).isEqualTo(createdLine.id());
        assertThat(stayed.getQuantity()).isEqualTo(100);
        assertThat(lineRepository.findById(createdLine.id()).orElseThrow().getProductionCapacity()).isEqualTo(1000);
        assertThat(lineCapacityLogRepository.count()).isEqualTo(logCount);
    }

    @Test
    @DisplayName("기간 안에 들어가면 같은 작업지시의 뒤 날짜로 다시 채우고 시작일에 새 장을 만들지 않는다")
    void repackLaterDayOnSameWorkOrderWhenCapacityDecreases() {
        Response createdLine = lineService.create(new CreateRequest("재배치-기간", 1000), 1L);
        LocalDate start = LocalDate.of(2026, 12, 15);
        LocalDate end = LocalDate.of(2026, 12, 16);
        long workOrderCount = workOrderRepository.count();
        Long workOrderId = workOrderService.create(
                new net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto.CreateRequest(
                        13L, createdLine.id(), 4L, 80, start, end), 1L).getFirst().id();

        // 80은 하루 40으로는 부족하고 이틀이면 된다. 15일에 새 장을 겹치지 않는다.
        lineService.update(createdLine.id(), new UpdateRequest("재배치-기간", 40, "설비 감축"), 1L);

        WorkOrder workOrder = workOrderRepository.findById(workOrderId).orElseThrow();
        assertThat(workOrder.getLine().getId()).isEqualTo(createdLine.id());
        assertThat(workOrder.getQuantity()).isEqualTo(80);
        assertThat(workOrder.getPlannedStartDate()).isEqualTo(start);
        assertThat(workOrder.getPlannedEndDate()).isEqualTo(end);
        assertThat(productionPlanRepository.findByWorkOrder_Id(workOrderId))
                .extracting(ProductionPlan::getPlanDate, ProductionPlan::getQuantity)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(start, 40),
                        org.assertj.core.groups.Tuple.tuple(end, 40));
        assertThat(workOrderRepository.count()).isEqualTo(workOrderCount + 1);
    }

    @Test
    @DisplayName("고장으로 바꾸면 미래 준비 작업을 다른 라인으로 통째로 옮긴다")
    void moveFuturePreparePlanWhenLineBreaks() {
        // 시드 A라인에는 지난 생산 계획이 있다. 오늘 이후만 보므로 그 계획은 이 테스트를 막지 않는다.
        // 전용 라인을 만들어 그 날의 준비 100이 통째로 빠지는지만 본다.
        Response createdLine = lineService.create(new CreateRequest("재배치-고장", 1000), 1L);
        LocalDate day = LocalDate.of(2026, 12, 16);
        long workOrderCount = workOrderRepository.count();
        Long workOrderId = workOrderService.create(
                new net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto.CreateRequest(
                        13L, createdLine.id(), 4L, 100, day, day), 1L).getFirst().id();

        lineService.changeStatus(createdLine.id(), new UpdateStatusRequest(LineStatus.BROKEN), 1L);

        WorkOrder moved = workOrderRepository.findById(workOrderId).orElseThrow();
        // 전량이 한 라인으로 가면 새 장을 만들지 않고 같은 행의 라인만 바뀐다.
        assertThat(moved.getLine().getId()).isNotEqualTo(createdLine.id());
        assertThat(moved.getQuantity()).isEqualTo(100);
        // 통째 이동은 담당자를 다시 고르지 않는다.
        assertThat(moved.getUser().getId()).isEqualTo(4L);
        // 고장 라인 그날에는 준비 수량이 남으면 안 된다.
        assertThat(productionPlanRepository.sumQuantityByLineAndDate(createdLine.id(), day)).isZero();
        // create로 한 장만 늘고, 재배치가 장을 더 만들지 않는다.
        assertThat(workOrderRepository.count()).isEqualTo(workOrderCount + 1);
    }

    @Test
    @DisplayName("이름이 비어 있거나 생산량이 1보다 작으면 라인을 등록하지 않는다")
    void rejectCreateWhenNameOrCapacityIsInvalid() {
        assertThatThrownBy(() -> lineService.create(new CreateRequest("  ", 500), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("라인 이름은 공백 허용 안함");
        assertThatThrownBy(() -> lineService.create(new CreateRequest("검증-생산량", 0), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("하루 생산량은 1 이상");
        assertThat(lineRepository.existsByLineName("검증-생산량")).isFalse();
    }

    @Test
    @DisplayName("같은 이름의 라인은 등록하지 않는다")
    void rejectDuplicateLineName() {
        assertThatThrownBy(() -> lineService.create(new CreateRequest("A라인", 500), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 존재하는 이름");
        assertThat(lineRepository.count()).isEqualTo(5);
    }

    @Test
    @DisplayName("없는 직원 id로 등록하면 라인을 만들지 않는다")
    void rejectCreateWhenUserIsMissing() {
        assertThatThrownBy(() -> lineService.create(new CreateRequest("검증-직원", 500), 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("등록한 직원을 찾을 수 없습니다.");
        assertThat(lineRepository.existsByLineName("검증-직원")).isFalse();
    }

    @Test
    @DisplayName("최고 관리자가 아니면 라인 이름을 바꾸지 않는다")
    void rejectNameChangeWhenUserIsNotSuperAdmin() {
        // 직원 2는 수주 관리자다. 생산량은 바꿀 수 있어도 이름은 최고 관리자만 바꾼다.
        assertThatThrownBy(() -> lineService.update(1L, new UpdateRequest("A라인-금지", 1000, null), 2L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("라인 이름은 최고 관리자만 수정할 수 있습니다.");
        assertThat(lineRepository.findById(1L).orElseThrow().getLineName()).isEqualTo("A라인");
    }

    @Test
    @DisplayName("없는 라인의 상세 조회는 거절한다")
    void rejectMissingLineDetail() {
        assertThatThrownBy(() -> lineService.findByLineId(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("라인을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("생산량이 늘면 기존 계획의 날짜와 수량을 유지한다")
    void keepPlanWhenCapacityIncreases() {
        Response createdLine = lineService.create(new CreateRequest("증설-유지", 1000), 1L);
        LocalDate day = LocalDate.of(2026, 12, 15);
        Long workOrderId = workOrderService.create(
                new net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto.CreateRequest(
                        13L, createdLine.id(), 4L, 100, day, day), 1L).getFirst().id();

        lineService.update(createdLine.id(), new UpdateRequest("증설-유지", 1200, "설비 증설"), 1L);

        WorkOrder workOrder = workOrderRepository.findById(workOrderId).orElseThrow();
        assertThat(workOrder.getLine().getId()).isEqualTo(createdLine.id());
        assertThat(workOrder.getPlannedStartDate()).isEqualTo(day);
        assertThat(workOrder.getPlannedEndDate()).isEqualTo(day);
        assertThat(productionPlanRepository.findByWorkOrder_Id(workOrderId))
                .extracting(ProductionPlan::getPlanDate, ProductionPlan::getQuantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(day, 100));
        assertThat(lineRepository.findById(createdLine.id()).orElseThrow().getProductionCapacity()).isEqualTo(1200);
    }

    @Test
    @DisplayName("오늘 이후 생산 계획이 있으면 점검으로 바꾸지 않는다")
    void rejectInspectionWhenProducing() {
        // 등록은 주말을 건너뛰므로 생산 계획을 오늘로 직접 넣는다. 점검은 오늘 이후 생산만 막는다.
        Response createdLine = lineService.create(new CreateRequest("점검-생산", 1000), 1L);
        WorkOrder sample = workOrderRepository.findById(3L).orElseThrow();
        LocalDate today = LocalDate.now();
        WorkOrder producing = new WorkOrder(
                "WO-TEST-PRODUCE",
                sample.getOrderProduct(),
                lineRepository.findById(createdLine.id()).orElseThrow(),
                sample.getUser(),
                100,
                today,
                today,
                "점검 차단");
        producing.changeStatus(sample.getCommonCode());
        producing.setCreatedBy("서도윤");
        producing.setUpdatedBy("서도윤");
        WorkOrder saved = workOrderRepository.save(producing);
        ProductionPlan plan = new ProductionPlan(saved, today, 100);
        plan.setCreatedBy("서도윤");
        plan.setUpdatedBy("서도윤");
        productionPlanRepository.save(plan);

        assertThatThrownBy(() -> lineService.changeStatus(
                createdLine.id(), new UpdateStatusRequest(LineStatus.INSPECTION), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("생산 중인 작업이 있어 라인을 점검으로 바꿀 수 없습니다.");
        assertThat(lineRepository.findById(createdLine.id()).orElseThrow().getStatus()).isEqualTo(LineStatus.AVAILABLE);
    }

    // 품목의 작업지시 수량 합. 계획 합과 같이 재배치 전후에 수량이 사라지지 않았는지 본다.
    private int quantityOf(Long orderProductId) {
        int quantity = 0;
        for (WorkOrder workOrder : workOrderRepository.findAll()) {
            if (workOrder.getOrderProduct().getId().equals(orderProductId)) {
                quantity += workOrder.getQuantity();
            }
        }
        return quantity;
    }
}