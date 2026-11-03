package net.likelion.bebc25.prodflow.work_order;

import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.holiday.repository.HolidayRepository;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.line.repsitory.LineRepository;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto.CreateRequest;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto.DetailResponse;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto.PageResponse;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto.Response;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto.UpdatedRequest;
import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.repsitory.ProductionPlanRepository;
import net.likelion.bebc25.prodflow.work_order.repsitory.WorkOrderRepository;
import net.likelion.bebc25.prodflow.work_order.serivce.WorkOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 애플리케이션 설정은 유지한다. 이 테스트만 테이블을 지우지 않고 data.sql도 다시 실행하지 않는다.
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=never"
})
@Transactional // 테스트가 끝나면 롤백해서 시드 작업지시를 유지한다
class WorkOrderServiceTest {

    @Autowired
    WorkOrderService workOrderService;

    @Autowired
    WorkOrderRepository workOrderRepository;

    @Autowired
    ProductionPlanRepository productionPlanRepository;

    @Autowired
    StatusHistoryRepository statusHistoryRepository;

    @Autowired
    LineRepository lineRepository;

    @Autowired
    HolidayRepository holidayRepository;

    @Test
    @DisplayName("한 라인의 기간 안에 들어가면 작업지시 한 장과 날짜별 계획이 저장된다")
    void createOnOneLineAcrossDays() {
        // 이미 계획이 있는 날 다음의 평일 이틀이다. 시드가 늘어도 같은 검증을 한다.
        LocalDate start = firstOpenWeekday();
        LocalDate end = nextOpenWeekday(start);
        CreateRequest request = request(15L, 1L, 4L, 1500, start, end);
        long historyCount = statusHistoryRepository.count();
        String workNo = workNo(workOrderRepository.count() + 1);

        // 토큰 직원 id는 넘기지만 작성자 이름은 수주 담당자를 쓴다.
        List<Response> created = workOrderService.create(request, 1L);

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().workNo()).isEqualTo(workNo);
        assertThat(created.getFirst().note()).isEqualTo("디자인 A 투명 케이스 A라인 " + start + " ~ " + end);

        WorkOrder saved = workOrderRepository.findById(created.getFirst().id()).orElseThrow();
        assertThat(saved.getOrderProduct().getId()).isEqualTo(15L);
        assertThat(saved.getLine().getId()).isEqualTo(1L);
        assertThat(saved.getUser().getId()).isEqualTo(4L);
        assertThat(saved.getUser().getName()).isEqualTo("김태호");
        assertThat(saved.getQuantity()).isEqualTo(1500);
        assertThat(saved.getPlannedStartDate()).isEqualTo(start);
        assertThat(saved.getPlannedEndDate()).isEqualTo(end);
        assertThat(saved.getCommonCode().getCode()).isEqualTo("PREPARE");
        // 서비스는 수주 담당자 이서준을 넣는다. 로그인 사용자가 없으면 Auditing이 SYSTEM으로 덮어쓴다.
        assertThat(saved.getCreatedBy()).isEqualTo("SYSTEM");
        assertThat(saved.getUpdatedBy()).isEqualTo("SYSTEM");
        assertThat(saved.getCompletedAt()).isNull();

        List<ProductionPlan> plans = plansOf(saved.getId());
        assertThat(plans).extracting(ProductionPlan::getPlanDate).containsExactly(start, end);
        assertThat(plans).extracting(ProductionPlan::getQuantity).containsExactly(1000, 500);
        assertThat(plans).allMatch(plan -> plan.getOrderProduct().getId().equals(15L));
        // 등록은 준비 상태만 채우고 상태 변경 이력은 남기지 않는다.
        assertThat(statusHistoryRepository.count()).isEqualTo(historyCount);
        assertThat(statusHistoryRepository.findAll())
                .noneMatch(history -> history.getEntity() == HistoryEntityType.WORK_ORDER
                        && history.getEntityId().equals(saved.getId()));
    }

    @Test
    @DisplayName("한 라인을 기간 끝까지 채운 뒤 다음 가동 라인으로 넘기고 지시번호를 1 올린다")
    void spillToNextLineAfterFillingTheFirst() {
        // 평일 이틀은 한 라인에 2,000개다. 2,500개면 다음 가동 라인에 500개가 넘어간다.
        LocalDate start = firstOpenWeekday();
        LocalDate end = nextOpenWeekday(start);
        CreateRequest request = request(15L, 1L, 4L, 2500, start, end);
        long firstNo = workOrderRepository.count() + 1;

        List<Response> created = workOrderService.create(request, 1L);

        assertThat(created).hasSize(2);
        assertThat(created).extracting(Response::workNo)
                .containsExactly(workNo(firstNo), workNo(firstNo + 1));

        WorkOrder first = workOrderRepository.findById(created.getFirst().id()).orElseThrow();
        WorkOrder second = workOrderRepository.findById(created.get(1).id()).orElseThrow();
        assertThat(first.getLine().getLineName()).isEqualTo("A라인");
        assertThat(first.getQuantity()).isEqualTo(2000);
        assertThat(plansOf(first.getId())).extracting(ProductionPlan::getQuantity).containsExactly(1000, 1000);

        assertThat(second.getLine().getId()).isNotEqualTo(first.getLine().getId());
        assertThat(second.getQuantity()).isEqualTo(500);
        assertThat(plansOf(second.getId())).extracting(ProductionPlan::getPlanDate).containsExactly(start);
        assertThat(plansOf(second.getId())).extracting(ProductionPlan::getQuantity).containsExactly(500);
        assertThat(first.getQuantity() + second.getQuantity()).isEqualTo(2500);
    }

    @Test
    @DisplayName("토요일과 일요일은 생산 계획에서 빠진다")
    void skipWeekend() {
        // 계획이 없는 금요일부터 다음 월요일까지다. 그 사이 토, 일은 빠진다.
        LocalDate friday = nextWeekday(DayOfWeek.FRIDAY);
        LocalDate monday = friday.plusDays(3);
        CreateRequest request = request(15L, 1L, 4L, 1500, friday, monday);

        List<Response> created = workOrderService.create(request, 1L);

        assertThat(created).hasSize(1);
        List<ProductionPlan> plans = plansOf(created.getFirst().id());
        assertThat(plans).extracting(ProductionPlan::getPlanDate).containsExactly(friday, monday);
        assertThat(plans).extracting(ProductionPlan::getQuantity).containsExactly(1000, 500);
        assertThat(plans).allMatch(plan -> plan.getPlanDate().getDayOfWeek() != DayOfWeek.SATURDAY
                && plan.getPlanDate().getDayOfWeek() != DayOfWeek.SUNDAY);
    }

    @Test
    @DisplayName("성탄절은 생산 계획에서 빠진다")
    void skipHoliday() {
        // 저장된 공휴일과 그 앞뒤 평일이다. 연도가 바뀌어도 그 공휴일은 계획에서 빠진다.
        LocalDate holiday = nextHoliday();
        LocalDate before = previousOpenWeekday(holiday);
        LocalDate after = nextOpenWeekday(holiday);
        CreateRequest request = request(15L, 1L, 4L, 1500, before, after);

        List<Response> created = workOrderService.create(request, 1L);

        List<ProductionPlan> plans = plansOf(created.getFirst().id());
        assertThat(plans).extracting(ProductionPlan::getPlanDate).containsExactly(before, after);
        assertThat(plans).extracting(ProductionPlan::getPlanDate).doesNotContain(holiday);
        assertThat(plans).extracting(ProductionPlan::getQuantity).containsExactly(1000, 500);
    }

    @Test
    @DisplayName("기간 안 생산 가능량을 넘기면 예외가 난다")
    void rejectWhenQuantityExceedsCapacity() {
        // 가동 중인 라인의 이틀 생산량보다 1개 많다. 라인이 늘어도 같은 방식으로 거절된다.
        LocalDate start = firstOpenWeekday();
        LocalDate end = nextOpenWeekday(start);
        CreateRequest request = request(15L, 1L, 4L, usableCapacity(2) + 1, start, end);

        assertThatThrownBy(() -> workOrderService.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("선택한 기간의 생산 가능량을 초과했습니다.");
    }

    @Test
    @DisplayName("고장 라인을 첫 라인으로 고르면 배정하지 않는다")
    void rejectBrokenLine() {
        LocalDate day = firstOpenWeekday();
        CreateRequest request = request(15L, 4L, 4L, 100, day, day);

        assertThatThrownBy(() -> workOrderService.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("점검·고장 라인에는 배정할 수 없습니다.");
    }

    @Test
    @DisplayName("목록은 상태와 상관없이 완료 예정일이 빠른 순이고 같은 날이면 최근 등록이 앞이다")
    void listByPlannedEndDateThenLatestRegistration() {
        // 같은 완료 예정일 두 장과, 그다음 평일의 한 장이다.
        LocalDate sameDay = firstOpenWeekday();
        LocalDate later = nextOpenWeekday(sameDay);
        Response firstOnSameDay = workOrderService.create(
                request(15L, 1L, 4L, 100, sameDay, sameDay), 1L).getFirst();
        Response secondOnSameDay = workOrderService.create(
                request(15L, 1L, 4L, 100, sameDay, sameDay), 1L).getFirst();
        Response laterDay = workOrderService.create(
                request(15L, 1L, 4L, 100, later, later), 1L).getFirst();

        // 화면과 같이 5건씩이다. 정렬은 비워 레포지토리 순서를 쓴다.
        Page<PageResponse> firstPage = workOrderService.findAll(null, null, null, null, null, PageRequest.of(0, 5));

        assertThat(firstPage.getNumber()).isZero();
        assertThat(firstPage.getSize()).isEqualTo(5);
        assertThat(firstPage.getContent()).hasSize(5);
        assertThat(firstPage.getTotalElements()).isEqualTo(workOrderRepository.count());

        List<PageResponse> list = new ArrayList<>();
        for (int page = 0; page < firstPage.getTotalPages(); page++) {
            list.addAll(workOrderService.findAll(null, null, null, null, null, PageRequest.of(page, 5)).getContent());
        }

        assertThat(list).hasSize((int) firstPage.getTotalElements());
        assertThat(list).isSortedAccordingTo(Comparator.comparing(PageResponse::plannedEndDate)
                .thenComparing(Comparator.comparing(PageResponse::id).reversed()));

        List<String> workNos = list.stream().map(PageResponse::workNo).toList();
        // 포장 완료도 빠지지 않는다. 완료 예정일 9월 21일이 10월 28일보다 앞이다.
        assertThat(workNos).contains("WI-2026-012", "WI-2026-001");
        assertThat(workNos.indexOf("WI-2026-012")).isLessThan(workNos.indexOf("WI-2026-001"));
        // 시드의 10월 26일은 id가 큰 007이 006보다 앞이다.
        assertThat(workNos.indexOf("WI-2026-007")).isLessThan(workNos.indexOf("WI-2026-006"));

        int laterRegistration = workNos.indexOf(secondOnSameDay.workNo());
        int earlierRegistration = workNos.indexOf(firstOnSameDay.workNo());
        int afterThatDay = workNos.indexOf(laterDay.workNo());
        assertThat(laterRegistration).isLessThan(earlierRegistration);
        assertThat(earlierRegistration).isLessThan(afterThatDay);

        PageResponse row = list.get(laterRegistration);
        assertThat(row.id()).isEqualTo(secondOnSameDay.id());
        assertThat(row.orderNo()).isEqualTo("PF-2026-013");
        assertThat(row.clientName()).isEqualTo("세원모바일(주)");
        assertThat(row.productName()).isEqualTo("디자인 A 투명 케이스");
        assertThat(row.quantity()).isEqualTo(100);
        assertThat(row.lineName()).isEqualTo("A라인");
        assertThat(row.assignedName()).isEqualTo("김태호");
        assertThat(row.workOrderDate()).isEqualTo(LocalDate.now());
        assertThat(row.plannedEndDate()).isEqualTo(sameDay);
        assertThat(row.status()).isEqualTo("PREPARE");
    }

    @Test
    @DisplayName("작업지시 한 장의 수주, 라인, 담당자, 기간, 납기를 조회한다")
    void getDetailOfSeedWorkOrder() {
        // 시드 1번은 준비 상태다. 완료일은 없다.
        DetailResponse detail = workOrderService.getDetail(1L);

        assertThat(detail.id()).isEqualTo(1L);
        assertThat(detail.workNo()).isEqualTo("WI-2026-001");
        assertThat(detail.orderNo()).isEqualTo("PF-2026-001");
        assertThat(detail.clientName()).isEqualTo("한빛케이스(주)");
        assertThat(detail.productName()).isEqualTo("디자인 A 투명 케이스");
        assertThat(detail.quantity()).isEqualTo(2000);
        assertThat(detail.lineId()).isEqualTo(1L);
        assertThat(detail.lineName()).isEqualTo("A라인");
        assertThat(detail.assignedId()).isEqualTo(4L);
        assertThat(detail.assignedName()).isEqualTo("김태호");
        assertThat(detail.workOrderDate()).isEqualTo(date(2026, 10, 26));
        assertThat(detail.plannedStartDate()).isEqualTo(date(2026, 10, 27));
        assertThat(detail.plannedEndDate()).isEqualTo(date(2026, 10, 28));
        assertThat(detail.completedAt()).isNull();
        assertThat(detail.status()).isEqualTo("PREPARE");
        assertThat(detail.note()).isEqualTo("디자인 A 투명 케이스 2,000개. A라인 10-27 1,000개, 10-28 1,000개");
        assertThat(detail.estimatedDueDate()).isEqualTo(date(2026, 10, 30));

        // 시드 12번은 포장 완료라 완료일이 있다.
        DetailResponse packed = workOrderService.getDetail(12L);
        assertThat(packed.workNo()).isEqualTo("WI-2026-012");
        assertThat(packed.status()).isEqualTo("PACK_DONE");
        assertThat(packed.completedAt()).isEqualTo(date(2026, 9, 21));
    }

    @Test
    @DisplayName("없는 작업지시면 예외가 난다")
    void rejectMissingWorkOrder() {
        assertThatThrownBy(() -> workOrderService.getDetail(999L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("해당 지시서를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("생산 현장 관리자가 아닌 직원은 작성 담당자가 될 수 없다")
    void rejectCreateWhenAssigneeIsNotProductionManager() {
        // 이서준은 사용 중인 수주 관리자다.
        LocalDate day = firstOpenWeekday();
        CreateRequest request = request(15L, 1L, 2L, 100, day, day);

        assertThatThrownBy(() -> workOrderService.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용 중인 생산 현장 관리자만 담당자로 배정할 수 있습니다.");
    }

    @Test
    @DisplayName("비활성 생산 현장 관리자는 작성 담당자가 될 수 없다")
    void rejectCreateWhenProductionManagerIsInactive() {
        // 정민석은 생산 현장 관리자이지만 비활성이다.
        LocalDate day = firstOpenWeekday();
        CreateRequest request = request(15L, 1L, 8L, 100, day, day);

        assertThatThrownBy(() -> workOrderService.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용 중인 생산 현장 관리자만 담당자로 배정할 수 있습니다.");
    }

    @Test
    @DisplayName("수정은 사용 중인 생산 현장 관리자로 담당자를 바꾼다")
    void updateAssigneeToActiveProductionManager() {
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(15L, 1L, 4L, 100, day, day), 1L).getFirst().id();

        // 박시우는 사용 중인 생산 현장 관리자다. 수정한 직원은 서도윤이다.
        DetailResponse updated = workOrderService.update(workOrderId, updateRequest(null, 6L), 1L);

        assertThat(updated.assignedId()).isEqualTo(6L);
        assertThat(updated.assignedName()).isEqualTo("박시우");
        assertThat(updated.lineId()).isEqualTo(1L);
        WorkOrder saved = workOrderRepository.findById(workOrderId).orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(6L);
        assertThat(saved.getUpdatedBy()).isEqualTo("서도윤");
    }

    @Test
    @DisplayName("생산 현장 관리자가 아닌 직원으로는 담당자를 바꾸지 않는다")
    void rejectUpdateWhenAssigneeIsNotProductionManager() {
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(15L, 1L, 4L, 100, day, day), 1L).getFirst().id();

        assertThatThrownBy(() -> workOrderService.update(workOrderId, updateRequest(null, 2L), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용 중인 생산 현장 관리자만 담당자로 배정할 수 있습니다.");
        assertThat(workOrderRepository.findById(workOrderId).orElseThrow().getUser().getId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("비활성 생산 현장 관리자로는 담당자를 바꾸지 않는다")
    void rejectUpdateWhenProductionManagerIsInactive() {
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(15L, 1L, 4L, 100, day, day), 1L).getFirst().id();

        assertThatThrownBy(() -> workOrderService.update(workOrderId, updateRequest(null, 8L), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용 중인 생산 현장 관리자만 담당자로 배정할 수 있습니다.");
        assertThat(workOrderRepository.findById(workOrderId).orElseThrow().getUser().getId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("가동 중인 라인으로는 작업지시 라인을 바꾸지 않는다")
    void rejectUpdateWhenLineIsOperating() {
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(15L, 1L, 4L, 100, day, day), 1L).getFirst().id();
        Long operatingLineId = lineRepository.findAll().stream()
                .filter(line -> line.getStatus() == LineStatus.OPERATING)
                .map(line -> line.getId())
                .filter(lineId -> !lineId.equals(1L))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() -> workOrderService.update(workOrderId, updateRequest(operatingLineId, null), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("가동 가능한 라인만 배정할 수 있습니다.");
        assertThat(workOrderRepository.findById(workOrderId).orElseThrow().getLine().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("가동 가능 라인으로 작업지시 라인을 바꾼다")
    void updateLineToAvailableLine() {
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(15L, 1L, 4L, 100, day, day), 1L).getFirst().id();
        Long availableLineId = lineRepository.findAll().stream()
                .filter(line -> line.getStatus() == LineStatus.AVAILABLE)
                .map(line -> line.getId())
                .findFirst()
                .orElseThrow();

        DetailResponse updated = workOrderService.update(workOrderId, updateRequest(availableLineId, null), 1L);

        assertThat(updated.lineId()).isEqualTo(availableLineId);
        assertThat(updated.assignedId()).isEqualTo(4L);
        assertThat(workOrderRepository.findById(workOrderId).orElseThrow().getLine().getId()).isEqualTo(availableLineId);
    }

    @Test
    @DisplayName("바꿀 라인과 담당자가 없으면 수정하지 않는다")
    void rejectUpdateWhenNothingChanges() {
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(15L, 1L, 4L, 100, day, day), 1L).getFirst().id();

        assertThatThrownBy(() -> workOrderService.update(workOrderId, updateRequest(null, null), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("변경된 라인 또는 담당자가 없습니다.");
    }

    @Test
    @DisplayName("없는 수주 품목이면 예외가 난다")
    void rejectMissingOrderProduct() {
        LocalDate day = firstOpenWeekday();
        CreateRequest request = request(999L, 1L, 4L, 100, day, day);

        assertThatThrownBy(() -> workOrderService.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수주 품목을 찾을 수 없습니다.");
    }

    private CreateRequest request(Long orderProductId, Long lineId, Long assignedId, int quantity,
                                  LocalDate start, LocalDate end) {
        return new WorkOrderDto.CreateRequest(orderProductId, lineId, assignedId, quantity, start, end);
    }

    private UpdatedRequest updateRequest(Long lineId, Long assignedId) {
        return new UpdatedRequest(lineId, assignedId);
    }

    private LocalDate firstOpenWeekday() {
        return nextOpenWeekday(latestPlanDate());
    }

    private LocalDate nextOpenWeekday(LocalDate after) {
        LocalDate day = after.plusDays(1);
        while (isClosed(day)) {
            day = day.plusDays(1);
        }
        return day;
    }

    private LocalDate previousOpenWeekday(LocalDate before) {
        LocalDate day = before.minusDays(1);
        while (isClosed(day)) {
            day = day.minusDays(1);
        }
        return day;
    }

    private LocalDate nextWeekday(DayOfWeek dayOfWeek) {
        LocalDate day = firstOpenWeekday();
        while (day.getDayOfWeek() != dayOfWeek) {
            day = day.plusDays(1);
        }
        while (isClosed(day)) {
            day = day.plusDays(7);
        }
        return day;
    }

    private LocalDate nextHoliday() {
        LocalDate after = latestPlanDate();
        return holidayRepository.findAll().stream()
                .map(holiday -> holiday.getHolidayDate())
                .filter(day -> day.isAfter(after))
                .min(Comparator.naturalOrder())
                .orElseThrow();
    }

    private boolean isClosed(LocalDate day) {
        return day.getDayOfWeek() == DayOfWeek.SATURDAY
                || day.getDayOfWeek() == DayOfWeek.SUNDAY
                || holidays().contains(day);
    }

    private Set<LocalDate> holidays() {
        Set<LocalDate> holidays = new HashSet<>();
        holidayRepository.findAll().forEach(holiday -> holidays.add(holiday.getHolidayDate()));
        return holidays;
    }

    private LocalDate latestPlanDate() {
        return productionPlanRepository.findAll().stream()
                .map(ProductionPlan::getPlanDate)
                .max(Comparator.naturalOrder())
                .orElse(LocalDate.of(2026, 1, 1));
    }

    private int usableCapacity(int days) {
        return lineRepository.findAll().stream()
                .filter(line -> line.getStatus() == LineStatus.AVAILABLE || line.getStatus() == LineStatus.OPERATING)
                .mapToInt(line -> line.getProductionCapacity())
                .sum() * days;
    }

    private List<ProductionPlan> plansOf(Long workOrderId) {
        return productionPlanRepository.findAll().stream()
                .filter(plan -> plan.getWorkOrder().getId().equals(workOrderId))
                .sorted(Comparator.comparing(ProductionPlan::getPlanDate))
                .toList();
    }

    private String workNo(long sequence) {
        return "WI-" + Year.now().getValue() + "-" + String.format("%03d", sequence);
    }

    private LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }
}
