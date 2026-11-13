package net.likelion.bebc25.prodflow.work_order.service;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.holiday.repository.HolidayRepository;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.line.repository.LineRepository;
import net.likelion.bebc25.prodflow.work_order.dto.ProductionPlanDto;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto.CreateRequest;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto.DetailResponse;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto.PageResponse;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto.Response;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto.UpdateStatusRequest;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto.UpdatedRequest;
import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.repository.ProductionPlanRepository;
import net.likelion.bebc25.prodflow.work_order.repository.WorkOrderHistoryRepository;
import net.likelion.bebc25.prodflow.work_order.repository.WorkOrderRepository;
import net.likelion.bebc25.prodflow.work_order.service.WorkOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
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

@SpringBootTest

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
    WorkOrderHistoryRepository workOrderHistoryRepository;

    @Autowired
    LineRepository lineRepository;

    @Autowired
    EntityManager em;

    @Autowired
    HolidayRepository holidayRepository;

    @Test
    @DisplayName("수주별 작업지시만 조회하고 생산 일정과 수주 품목 ID를 반환한다")
    void listByOrderId() {
        Page<PageResponse> result = workOrderService.findAll(1L, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).allSatisfy(row -> {
            assertThat(row.orderNo()).isEqualTo("PF-20261110-1");
            assertThat(row.orderProductId()).isEqualTo(1L);
            assertThat(row.plannedStartDate()).isNotNull();
            assertThat(row.plannedEndDate()).isNotNull();
            assertThat(row.completedAt()).isNull();
        });
        Page<PageResponse> completed = workOrderService.findAll(7L, null, null, null, null, null, PageRequest.of(0, 10));
        assertThat(completed.getContent()).hasSize(1);
        assertThat(completed.getContent().getFirst().completedAt()).isEqualTo(LocalDate.of(2026, 10, 27));
    }

    @Test
    @DisplayName("수주 필터와 라인·상태·기간·검색어는 AND로 적용한다")
    void listByOrderIdWithOtherConditions() {
        WorkOrder target = workOrderRepository.findById(1L).orElseThrow();
        Page<PageResponse> result = workOrderService.findAll(1L, target.getLine().getId(), " PREPARE ",
                target.getWorkOrderDate(), target.getWorkOrderDate(), " PF-20261110-1 ", PageRequest.of(0, 5));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(target.getId());
        Page<PageResponse> noMatch = workOrderService.findAll(2L, target.getLine().getId(), "PREPARE",
                target.getWorkOrderDate(), target.getWorkOrderDate(), "PF-20261110-1", PageRequest.of(0, 5));
        assertThat(noMatch.getContent()).isEmpty();
    }

    @Test
    @DisplayName("수주별 조회에서도 완료 예정일·최근 등록 순서와 페이징을 유지한다")
    void listByOrderIdWithPagination() {
        List<PageResponse> rows = new ArrayList<>();
        for (int page = 0; page < 2; page++) {
            Page<PageResponse> result = workOrderService.findAll(1L, null, null, null, null, null, PageRequest.of(page, 1));
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(2);
            rows.addAll(result.getContent());
        }

        assertThat(rows).hasSize(2).isSortedAccordingTo(Comparator.comparing(PageResponse::plannedEndDate)
                .thenComparing(Comparator.comparing(PageResponse::id).reversed()));
        assertThat(rows).extracting(PageResponse::id).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("작업지시가 없는 수주는 빈 페이지를 반환한다")
    void listByOrderIdWithoutWorkOrders() {
        Page<PageResponse> result = workOrderService.findAll(10L, null, null, null, null, null, PageRequest.of(0, 5));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("담당자 조건은 페이지를 자르기 전에 적용한다")
    void listByAssigneeBeforePaging() {
        Page<PageResponse> first = workOrderService.findAll(
                null, null, null, null, null, null, 5L, PageRequest.of(0, 5));
        Page<PageResponse> second = workOrderService.findAll(
                null, null, null, null, null, null, 5L, PageRequest.of(1, 5));

        long assignedToLee = workOrderRepository.findAll().stream()
                .filter(work -> work.getUser() != null && work.getUser().getId().equals(5L))
                .count();
        assertThat(first.getTotalElements()).isEqualTo(assignedToLee);
        assertThat(first.getTotalPages()).isEqualTo((int) ((assignedToLee + 4) / 5));
        assertThat(first.getContent()).hasSize(5);
        assertThat(first.getContent()).allSatisfy(row -> assertThat(row.assignedName()).isEqualTo("이재원"));
        assertThat(second.getContent()).hasSize((int) Math.min(5, assignedToLee - 5));
        assertThat(second.getContent()).allSatisfy(row -> assertThat(row.assignedName()).isEqualTo("이재원"));
    }

    @Test
    @DisplayName("없는 수주 ID는 조회 실패한다")
    void rejectUnknownOrderId() {
        assertThatThrownBy(() -> workOrderService.findAll(Long.MAX_VALUE, null, null, null, null, null, PageRequest.of(0, 5)))
                .isInstanceOf(NoSuchElementException.class).hasMessage("수주를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("한 라인의 기간 안에 들어가면 작업지시 한 장과 날짜별 계획이 저장된다")
    void createOnOneLineAcrossDays() {
        // 이미 계획이 있는 날 다음의 평일 이틀이다. 시드가 늘어도 같은 검증을 한다.
        LocalDate start = firstOpenWeekday();
        LocalDate end = nextOpenWeekday(start);
        CreateRequest request = request(13L, 1L, 4L, 1500, start, end);
        String orderManagerName = em.find(OrderProduct.class, 13L)
                .getCustomerOrder().getUser().getName();
        long historyCount = statusHistoryRepository.count();
        String workNo = workNo(workOrderRepository.count() + 1);

        // 토큰 직원 id는 넘기지만 작성자 이름은 수주 담당자를 쓴다.
        List<Response> created = workOrderService.create(request, 1L);

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().workNo()).isEqualTo(workNo);
        assertThat(created.getFirst().note()).isEqualTo("아이폰 16 맥세이프 클리어 케이스 A라인 " + start + " ~ " + end);
        assertResponsePlans(created.getFirst(), List.of(start, end), List.of(1000, 500));

        WorkOrder saved = workOrderRepository.findById(created.getFirst().id()).orElseThrow();
        assertThat(saved.getOrderProduct().getId()).isEqualTo(13L);
        assertThat(saved.getLine().getId()).isEqualTo(1L);
        assertThat(saved.getUser().getId()).isEqualTo(4L);
        assertThat(saved.getUser().getName()).isEqualTo("김태호");
        assertThat(saved.getQuantity()).isEqualTo(1500);
        assertThat(saved.getPlannedStartDate()).isEqualTo(start);
        assertThat(saved.getPlannedEndDate()).isEqualTo(end);
        assertThat(saved.getCommonCode().getCode()).isEqualTo("PREPARE");
        // 로그인한 처리자와 별개로 해당 수주 담당자의 이름을 기록한다.
        assertThat(saved.getCreatedBy()).isEqualTo(orderManagerName);
        assertThat(saved.getUpdatedBy()).isEqualTo(orderManagerName);
        assertThat(saved.getCompletedAt()).isNull();

        List<ProductionPlan> plans = plansOf(saved.getId());
        assertThat(plans).extracting(ProductionPlan::getPlanDate).containsExactly(start, end);
        assertThat(plans).extracting(ProductionPlan::getQuantity).containsExactly(1000, 500);
        assertThat(plans).allMatch(plan -> plan.getOrderProduct().getId().equals(13L));
        assertThat(plans).allMatch(plan -> orderManagerName.equals(plan.getCreatedBy())
                && orderManagerName.equals(plan.getUpdatedBy()));
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
        CreateRequest request = request(13L, 1L, 4L, 2500, start, end);
        long firstNo = workOrderRepository.count() + 1;

        List<Response> created = workOrderService.create(request, 1L);

        assertThat(created).hasSize(2);
        assertThat(created).extracting(Response::workNo)
                .containsExactly(workNo(firstNo), workNo(firstNo + 1));
        assertResponsePlans(created.getFirst(), List.of(start, end), List.of(1000, 1000));
        assertResponsePlans(created.get(1), List.of(start), List.of(500));

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
        CreateRequest request = request(13L, 1L, 4L, 1500, friday, monday);

        List<Response> created = workOrderService.create(request, 1L);

        assertThat(created).hasSize(1);
        assertResponsePlans(created.getFirst(), List.of(friday, monday), List.of(1000, 500));
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
        LocalDate holiday = LocalDate.of(2026, 12, 25);
        LocalDate before = previousOpenWeekday(holiday);
        LocalDate after = nextOpenWeekday(holiday);
        CreateRequest request = request(13L, 1L, 4L, 1500, before, after);

        List<Response> created = workOrderService.create(request, 1L);

        assertResponsePlans(created.getFirst(), List.of(before, after), List.of(1000, 500));
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
        CreateRequest request = request(13L, 1L, 4L, usableCapacity(2) + 1, start, end);

        assertThatThrownBy(() -> workOrderService.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("선택한 기간의 생산 가능량을 초과했습니다.");
    }

    @Test
    @DisplayName("고장 라인을 첫 라인으로 고르면 배정하지 않는다")
    void rejectBrokenLine() {
        LocalDate day = firstOpenWeekday();
        CreateRequest request = request(13L, 3L, 4L, 100, day, day);

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
                request(13L, 1L, 4L, 100, sameDay, sameDay), 1L).getFirst();
        Response secondOnSameDay = workOrderService.create(
                request(13L, 1L, 4L, 100, sameDay, sameDay), 1L).getFirst();
        Response laterDay = workOrderService.create(
                request(13L, 1L, 4L, 100, later, later), 1L).getFirst();

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
        // 포장 완료 작업도 포함하며 완료 예정일 10월 19일이 11월 11일보다 앞이다.
        assertThat(workNos).contains("WO-2026-008", "WO-2026-001");
        assertThat(workNos.indexOf("WO-2026-008")).isLessThan(workNos.indexOf("WO-2026-001"));
        // 시드의 완료 예정일 11월 6일이 같으면 ID가 큰 007이 006보다 앞이다.
        assertThat(workNos.indexOf("WO-2026-005")).isLessThan(workNos.indexOf("WO-2026-004"));

        int laterRegistration = workNos.indexOf(secondOnSameDay.workNo());
        int earlierRegistration = workNos.indexOf(firstOnSameDay.workNo());
        int afterThatDay = workNos.indexOf(laterDay.workNo());
        assertThat(laterRegistration).isLessThan(earlierRegistration);
        assertThat(earlierRegistration).isLessThan(afterThatDay);

        PageResponse row = list.get(laterRegistration);
        assertThat(row.id()).isEqualTo(secondOnSameDay.id());
        assertThat(row.orderNo()).isEqualTo("PF-20261113-13");
        assertThat(row.clientName()).isEqualTo("한빛케이스(주)");
        assertThat(row.productName()).isEqualTo("아이폰 16 맥세이프 클리어 케이스");
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
        DetailResponse detail = workOrderService.getDetail(1L);
        assertThat(detail.id()).isEqualTo(1L);
        assertThat(detail.workNo()).isEqualTo("WO-2026-001");
        assertThat(detail.orderNo()).isEqualTo("PF-20261110-1");
        assertThat(detail.clientName()).isEqualTo("한빛케이스(주)");
        assertThat(detail.productName()).isEqualTo("아이폰 16 맥세이프 클리어 케이스");
        assertThat(detail.quantity()).isEqualTo(1000);
        assertThat(detail.lineId()).isEqualTo(2L);
        assertThat(detail.lineName()).isEqualTo("B라인");
        assertThat(detail.assignedId()).isEqualTo(5L);
        assertThat(detail.assignedName()).isEqualTo("이재원");
        assertThat(detail.workOrderDate()).isEqualTo(date(2026, 11, 10));
        assertThat(detail.plannedStartDate()).isEqualTo(date(2026, 11, 16));
        assertThat(detail.plannedEndDate()).isEqualTo(date(2026, 11, 16));
        assertThat(detail.completedAt()).isNull();
        assertThat(detail.status()).isEqualTo("PREPARE");
        assertThat(detail.note()).contains("시나리오 01");
        assertThat(detail.estimatedDueDate()).isEqualTo(date(2026, 11, 19));
        assertThat(detail.plans()).extracting(ProductionPlanDto.Response::planDate, ProductionPlanDto.Response::quantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(date(2026, 11, 16), 1000));
        assertThat(detail.plans()).extracting(ProductionPlanDto.Response::workOrderId).containsOnly(1L);
        DetailResponse packed = workOrderService.getDetail(8L);
        assertThat(packed.workNo()).isEqualTo("WO-2026-008");
        assertThat(packed.status()).isEqualTo("PACK_DONE");
        assertThat(packed.completedAt()).isEqualTo(date(2026, 10, 27));
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
        CreateRequest request = request(13L, 1L, 2L, 100, day, day);

        assertThatThrownBy(() -> workOrderService.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용 중인 생산 현장 관리자만 담당자로 배정할 수 있습니다.");
    }

    @Test
    @DisplayName("비활성 생산 현장 관리자는 작성 담당자가 될 수 없다")
    void rejectCreateWhenProductionManagerIsInactive() {
        // 정민석은 생산 현장 관리자이지만 비활성이다.
        LocalDate day = firstOpenWeekday();
        CreateRequest request = request(13L, 1L, 8L, 100, day, day);

        assertThatThrownBy(() -> workOrderService.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용 중인 생산 현장 관리자만 담당자로 배정할 수 있습니다.");
    }

    @Test
    @DisplayName("수정은 사용 중인 생산 현장 관리자로 담당자를 바꾼다")
    void updateAssigneeToActiveProductionManager() {
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(13L, 1L, 4L, 100, day, day), 1L).getFirst().id();

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
        Long workOrderId = workOrderService.create(request(13L, 1L, 4L, 100, day, day), 1L).getFirst().id();

        assertThatThrownBy(() -> workOrderService.update(workOrderId, updateRequest(null, 2L), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용 중인 생산 현장 관리자만 담당자로 배정할 수 있습니다.");
        assertThat(workOrderRepository.findById(workOrderId).orElseThrow().getUser().getId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("비활성 생산 현장 관리자로는 담당자를 바꾸지 않는다")
    void rejectUpdateWhenProductionManagerIsInactive() {
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(13L, 1L, 4L, 100, day, day), 1L).getFirst().id();

        assertThatThrownBy(() -> workOrderService.update(workOrderId, updateRequest(null, 8L), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용 중인 생산 현장 관리자만 담당자로 배정할 수 있습니다.");
        assertThat(workOrderRepository.findById(workOrderId).orElseThrow().getUser().getId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("가동 중인 라인으로는 작업지시 라인을 바꾸지 않는다")
    void rejectUpdateWhenLineIsOperating() {
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(13L, 1L, 4L, 100, day, day), 1L).getFirst().id();
        Line operatingLine = lineRepository.save(new Line("이동-가동", 1000));
        operatingLine.changeStatus(LineStatus.OPERATING);

        assertThatThrownBy(() -> workOrderService.update(workOrderId, updateRequest(operatingLine.getId(), null), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("가동 가능한 라인만 배정할 수 있습니다.");
        assertThat(workOrderRepository.findById(workOrderId).orElseThrow().getLine().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("생산 중이거나 일시정지인 작업은 다른 라인으로 바꾸지 않는다")
    void rejectLineChangeWhenWorkHasStarted() {
        Line source = lineRepository.save(new Line("시작-원라인", 1000));
        Line target = lineRepository.save(new Line("시작-다른라인", 1000));
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(
                request(13L, source.getId(), 4L, 100, day, day), 1L).getFirst().id();
        WorkOrder workOrder = workOrderRepository.findById(workOrderId).orElseThrow();
        // 시작 예정일이 오늘보다 뒤면 생산 중 검증보다 날짜 검증이 먼저 난다.
        ReflectionTestUtils.setField(workOrder, "plannedStartDate", LocalDate.now());

        workOrderService.changeStatus(workOrderId, new UpdateStatusRequest("PRODUCE", null), 1L);

        assertThatThrownBy(() -> workOrderService.update(workOrderId, updateRequest(target.getId(), null), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("생산을 시작한 작업은 라인을 변경할 수 없습니다.");
        assertThat(workOrder.getLine().getId()).isEqualTo(source.getId());
        // 같은 라인을 다시 보내거나 담당자만 바꾸는 것은 된다.
        DetailResponse sameLine = workOrderService.update(workOrderId, updateRequest(source.getId(), null), 1L);
        assertThat(sameLine.lineId()).isEqualTo(source.getId());
        DetailResponse assigned = workOrderService.update(workOrderId, updateRequest(null, 6L), 1L);
        assertThat(assigned.assignedId()).isEqualTo(6L);
        assertThat(assigned.lineId()).isEqualTo(source.getId());

        workOrderService.changeStatus(workOrderId, new UpdateStatusRequest("SUSPEND", null), 1L);
        assertThatThrownBy(() -> workOrderService.update(workOrderId, updateRequest(target.getId(), null), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("생산을 시작한 작업은 라인을 변경할 수 없습니다.");
        assertThat(workOrderRepository.findById(workOrderId).orElseThrow().getLine().getId()).isEqualTo(source.getId());
    }

    @Test
    @DisplayName("가동 가능 라인으로 작업지시 라인을 바꾼다")
    void updateLineToAvailableLine() {
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(13L, 1L, 4L, 100, day, day), 1L).getFirst().id();
        // 시드 라인은 점검·고장으로 바뀌어 있을 수 있다. 이 테스트 안에서 가동 가능 라인을 만든다.
        Line availableLine = lineRepository.save(new Line("이동-가능", 1000));

        DetailResponse updated = workOrderService.update(workOrderId, updateRequest(availableLine.getId(), null), 1L);

        assertThat(updated.lineId()).isEqualTo(availableLine.getId());
        assertThat(updated.assignedId()).isEqualTo(4L);
        assertThat(workOrderRepository.findById(workOrderId).orElseThrow().getLine().getId())
                .isEqualTo(availableLine.getId());
        // 계획 행의 날짜와 수량은 그대로다. 합계는 작업지시의 현재 라인을 본다.
        List<ProductionPlan> plans = plansOf(workOrderId);
        assertThat(plans).extracting(ProductionPlan::getPlanDate).containsExactly(day);
        assertThat(plans).extracting(ProductionPlan::getQuantity).containsExactly(100);
        assertThat(productionPlanRepository.sumQuantityByLineAndDate(1L, day)).isZero();
        assertThat(productionPlanRepository.sumQuantityByLineAndDate(availableLine.getId(), day)).isEqualTo(100);
    }

    @Test
    @DisplayName("바꿀 라인과 담당자가 없으면 수정하지 않는다")
    void rejectUpdateWhenNothingChanges() {
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(13L, 1L, 4L, 100, day, day), 1L).getFirst().id();

        assertThatThrownBy(() -> workOrderService.update(workOrderId, updateRequest(null, null), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("변경된 라인 또는 담당자가 없습니다.");
    }

    @Test
    @DisplayName("작업 상태를 바꾸면 상태 변경 이력을 남긴다")
    void recordStatusHistoryWhenStatusChanges() {
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(13L, 1L, 4L, 100, day, day), 1L).getFirst().id();
        long assignmentHistoryCount = workOrderHistoryRepository.count();

        // 준비에서 생산 중지로 바꾼다. 시작 예정일과 상관없이 허용되는 전이다.
        DetailResponse changed = workOrderService.changeStatus(
                workOrderId, new UpdateStatusRequest("HALT", null), 1L);

        assertThat(changed.status()).isEqualTo("HALT");
        assertThat(statusHistoryRepository.findAll())
                .filteredOn(history -> history.getEntity() == HistoryEntityType.WORK_ORDER
                        && history.getEntityId().equals(workOrderId))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getFromStatus()).isEqualTo("PREPARE");
                    assertThat(history.getToStatus()).isEqualTo("HALT");
                    assertThat(history.getUser().getId()).isEqualTo(1L);
                    assertThat(history.getUserName()).isEqualTo("서도윤");
                });
        // 상태 변경은 라인·담당자 변경 이력에 들어가지 않는다.
        assertThat(workOrderHistoryRepository.count()).isEqualTo(assignmentHistoryCount);
    }

    @Test
    @DisplayName("작업지시의 생산·보류·재개·중지·포장 처리로 바뀐 수주 상태를 이력으로 남긴다")
    void recordOrderHistoryThroughoutWorkProgress() {
        WorkOrder work = workOrderRepository.findById(11L).orElseThrow();
        Line line = lineRepository.save(new Line("수주이력-생산라인", 1000));
        work.changeLine(line);
        work.changeStatus(em.find(CommonCode.class, 16L));
        work.getOrderProduct().getCustomerOrder().changeProgress(em.find(CommonCode.class, 7L));
        ReflectionTestUtils.setField(work, "plannedStartDate", LocalDate.now());
        Long orderId = work.getOrderProduct().getCustomerOrder().getId();

        var existingHistoryIds = statusHistoryRepository.findByEntityAndEntityIdOrderByCreatedAtDescIdDesc(
                HistoryEntityType.ORDER, orderId).stream().map(history -> history.getId()).toList();

        for (String status : List.of("PRODUCE", "SUSPEND", "PRODUCE", "HALT", "PREPARE", "PRODUCE", "PACK", "PACK_DONE")) {
            workOrderService.changeStatus(work.getId(), new UpdateStatusRequest(status, null), 1L);
        }
        em.flush();
        em.clear();

        var histories = statusHistoryRepository.findByEntityAndEntityIdOrderByCreatedAtDescIdDesc(
                HistoryEntityType.ORDER, orderId).stream()
                .filter(history -> !existingHistoryIds.contains(history.getId())).toList();
        assertThat(histories).extracting(history -> history.getFromStatus()).containsExactly(
                "IN_PRODUCTION", "CONFIRMED", "IN_PRODUCTION", "ON_HOLD", "IN_PRODUCTION", "CONFIRMED");
        assertThat(histories).extracting(history -> history.getToStatus()).containsExactly(
                "PACKING", "IN_PRODUCTION", "CONFIRMED", "IN_PRODUCTION", "ON_HOLD", "IN_PRODUCTION");
        assertThat(histories).allSatisfy(history -> {
            assertThat(history.getUser().getId()).isEqualTo(1L);
            assertThat(history.getUserName()).isEqualTo("서도윤");
            assertThat(history.getNote()).contains(work.getWorkNo());
        });
    }

    @Test
    @DisplayName("같은 수주의 다른 작업이 생산을 시작해도 수주가 이미 생산중이면 이력을 중복 저장하지 않는다")
    void skipOrderHistoryWhenAnotherWorkStarts() {
        Line line = lineRepository.save(new Line("수주이력-추가라인", 1000));
        LocalDate day = firstOpenWeekday();
        Long workId = workOrderService.create(request(13L, line.getId(), 4L, 100, day, day), 1L).getFirst().id();
        WorkOrder work = workOrderRepository.findById(workId).orElseThrow();
        ReflectionTestUtils.setField(work, "plannedStartDate", LocalDate.now());
        work.getOrderProduct().getCustomerOrder().changeProgress(em.find(CommonCode.class, 8L));
        Long orderId = work.getOrderProduct().getCustomerOrder().getId();
        long count = statusHistoryRepository.findByEntityAndEntityIdOrderByCreatedAtDescIdDesc(
                HistoryEntityType.ORDER, orderId).size();

        workOrderService.changeStatus(workId, new UpdateStatusRequest("PRODUCE", null), 1L);

        assertThat(statusHistoryRepository.findByEntityAndEntityIdOrderByCreatedAtDescIdDesc(
                HistoryEntityType.ORDER, orderId)).hasSize((int) count);
        assertThat(statusHistoryRepository.findByEntityAndEntityIdOrderByCreatedAtDescIdDesc(
                HistoryEntityType.WORK_ORDER, workId)).singleElement();
    }

    @Test
    @DisplayName("생산을 중지했다가 준비로 돌아와도 줄어든 생산능력보다 큰 계획은 다시 시작할 수 없다")
    void rejectProduceWhenPlanExceedsReducedCapacity() {
        Line line = lineRepository.save(new Line("생산시작-용량", 1000));
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(13L, line.getId(), 4L, 100, day, day), 1L).getFirst().id();
        WorkOrder workOrder = workOrderRepository.findById(workOrderId).orElseThrow();
        // 시작 예정일이 오늘보다 뒤면 생산능력 검증보다 날짜 검증이 먼저 난다.
        ReflectionTestUtils.setField(workOrder, "plannedStartDate", LocalDate.now());

        workOrderService.changeStatus(workOrderId, new UpdateStatusRequest("PRODUCE", null), 1L);
        workOrderService.changeStatus(workOrderId, new UpdateStatusRequest("HALT", null), 1L);
        workOrderService.changeStatus(workOrderId, new UpdateStatusRequest("PREPARE", null), 1L);
        // 중지 상태의 계획은 생산능력 변경에서 비워지지 않는다. 용량만 줄인 뒤를 재현한다.
        line.editProduction(40);

        assertThatThrownBy(() -> workOrderService.changeStatus(
                workOrderId, new UpdateStatusRequest("PRODUCE", null), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("라인의 생산능력을 초과하여 생산을 시작할 수 없습니다.");
        assertThat(workOrder.getCommonCode().getCode()).isEqualTo("PREPARE");
        assertThat(line.getProductionCapacity()).isEqualTo(40);
    }

    @ParameterizedTest
    @CsvSource({
            "PREPARE, BROKEN, 고장 상태인 라인에서는 생산을 시작하거나 재개할 수 없습니다.",
            "PREPARE, INSPECTION, 점검 중인 라인에서는 생산을 시작하거나 재개할 수 없습니다.",
            "SUSPEND, BROKEN, 고장 상태인 라인에서는 생산을 시작하거나 재개할 수 없습니다.",
            "SUSPEND, INSPECTION, 점검 중인 라인에서는 생산을 시작하거나 재개할 수 없습니다."
    })
    @DisplayName("고장·점검 라인에서는 생산 시작과 재개를 거절하고 상태와 이력을 유지한다")
    void rejectProduceOnUnavailableLine(String workStatus, LineStatus lineStatus, String message) {
        Line line = lineRepository.save(new Line("생산시작-상태검증", 1000));
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(13L, line.getId(), 4L, 100, day, day), 1L).getFirst().id();
        WorkOrder workOrder = workOrderRepository.findById(workOrderId).orElseThrow();
        ReflectionTestUtils.setField(workOrder, "plannedStartDate", LocalDate.now());
        if ("SUSPEND".equals(workStatus)) {
            workOrderService.changeStatus(workOrderId, new UpdateStatusRequest("PRODUCE", null), 1L);
            workOrderService.changeStatus(workOrderId, new UpdateStatusRequest("SUSPEND", null), 1L);
        }
        line.changeStatus(lineStatus);
        String orderStatus = workOrder.getOrderProduct().getCustomerOrder().getStatusCode().getCode();
        long historyCount = statusHistoryRepository.count();

        assertThatThrownBy(() -> workOrderService.changeStatus(
                workOrderId, new UpdateStatusRequest("PRODUCE", null), 1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(message);

        assertThat(workOrder.getCommonCode().getCode()).isEqualTo(workStatus);
        assertThat(line.getStatus()).isEqualTo(lineStatus);
        assertThat(workOrder.getOrderProduct().getCustomerOrder().getStatusCode().getCode()).isEqualTo(orderStatus);
        assertThat(statusHistoryRepository.count()).isEqualTo(historyCount);
    }

    @ParameterizedTest
    @CsvSource({"PREPARE, AVAILABLE", "PREPARE, OPERATING", "SUSPEND, OPERATING"})
    @DisplayName("정상 라인에서는 생산 시작과 재개를 허용한다")
    void produceOnUsableLine(String workStatus, LineStatus lineStatus) {
        Line line = lineRepository.save(new Line("생산시작-정상라인", 1000));
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(13L, line.getId(), 4L, 100, day, day), 1L).getFirst().id();
        WorkOrder workOrder = workOrderRepository.findById(workOrderId).orElseThrow();
        ReflectionTestUtils.setField(workOrder, "plannedStartDate", LocalDate.now());
        if ("SUSPEND".equals(workStatus)) {
            workOrderService.changeStatus(workOrderId, new UpdateStatusRequest("PRODUCE", null), 1L);
            workOrderService.changeStatus(workOrderId, new UpdateStatusRequest("SUSPEND", null), 1L);
        }
        line.changeStatus(lineStatus);

        workOrderService.changeStatus(workOrderId, new UpdateStatusRequest("PRODUCE", null), 1L);

        assertThat(workOrder.getCommonCode().getCode()).isEqualTo("PRODUCE");
        assertThat(line.getStatus()).isEqualTo(LineStatus.OPERATING);
        assertThat(workOrder.getOrderProduct().getCustomerOrder().getStatusCode().getCode()).isEqualTo("IN_PRODUCTION");
    }

    @Test
    @DisplayName("같은 라인에 생산 중인 작업이 있으면 생산을 시작하지 않는다")
    void rejectProduceWhenAnotherWorkIsProducing() {
        // 시드 A라인. 6번은 생산 중이고 1번은 준비다.
        WorkOrder producing = workOrderRepository.findById(3L).orElseThrow();
        WorkOrder waiting = workOrderRepository.findById(1L).orElseThrow();
        assertThat(producing.getCommonCode().getCode()).isEqualTo("PRODUCE");
        waiting.changeLine(producing.getLine());
        assertThat(producing.getLine().getId()).isEqualTo(waiting.getLine().getId());
        assertThat(waiting.getCommonCode().getCode()).isEqualTo("PREPARE");
        // 시작 예정일이 오늘보다 뒤면 생산 중 검증보다 날짜 검증이 먼저 난다.
        ReflectionTestUtils.setField(waiting, "plannedStartDate", LocalDate.now());

        assertThatThrownBy(() -> workOrderService.changeStatus(
                waiting.getId(), new UpdateStatusRequest("PRODUCE", null), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이 라인에서 이미 생산 중인 작업이 있습니다.");

        assertThat(waiting.getCommonCode().getCode()).isEqualTo("PREPARE");
        assertThat(producing.getCommonCode().getCode()).isEqualTo("PRODUCE");
    }

    @Test
    @DisplayName("라인과 담당자를 바꾸면 작업지시 변경 이력을 남긴다")
    void recordWorkOrderHistoryWhenLineAndAssigneeChange() {
        LocalDate day = firstOpenWeekday();
        Long workOrderId = workOrderService.create(request(13L, 1L, 4L, 100, day, day), 1L).getFirst().id();
        long statusHistoryCount = statusHistoryRepository.count();
        // 다른 라인을 이 테스트 안에서만 가동 가능으로 둔다.
        Line targetLine = lineRepository.findAll().stream()
                .filter(line -> !line.getId().equals(1L))
                .findFirst()
                .orElseThrow();
        targetLine.changeStatus(LineStatus.AVAILABLE);

        workOrderService.update(workOrderId, updateRequest(targetLine.getId(), 6L), 1L);

        assertThat(workOrderHistoryRepository.findAll())
                .filteredOn(history -> history.getWorkOrder().getId().equals(workOrderId))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getPreviousLine().getId()).isEqualTo(1L);
                    assertThat(history.getNewLine().getId()).isEqualTo(targetLine.getId());
                    assertThat(history.getPreviousUser().getId()).isEqualTo(4L);
                    assertThat(history.getPreviousUser().getName()).isEqualTo("김태호");
                    assertThat(history.getNewUser().getId()).isEqualTo(6L);
                    assertThat(history.getNewUser().getName()).isEqualTo("박시우");
                    assertThat(history.getChangedBy().getName()).isEqualTo("서도윤");
                    assertThat(history.getChangeReason()).isNull();
                });
        // 라인·담당자 변경은 상태 변경 이력에 들어가지 않는다.
        assertThat(statusHistoryRepository.count()).isEqualTo(statusHistoryCount);
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

    @Test
    @DisplayName("점검 라인을 첫 라인으로 고르면 배정하지 않는다")
    void rejectInspectionLine() {
        LocalDate day = LocalDate.of(2026, 12, 15);

        assertThatThrownBy(() -> workOrderService.create(request(13L, 5L, 4L, 100, day, day), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("점검·고장 라인에는 배정할 수 없습니다.");
    }

    @Test
    @DisplayName("목록은 라인, 상태, 수주번호로 거른다")
    void findByLineStatusAndOrderNo() {
        Line line = new Line("검색-라인", 1000);
        line.setCreatedBy("서도윤");
        line.setUpdatedBy("서도윤");
        lineRepository.save(line);
        LocalDate day = LocalDate.of(2026, 12, 15);
        Long workOrderId = workOrderService.create(
                request(13L, line.getId(), 4L, 100, day, day), 1L).getFirst().id();
        String workNo = workOrderRepository.findById(workOrderId).orElseThrow().getWorkNo();

        Page<PageResponse> matched = workOrderService.findAll(
                line.getId(), "PREPARE", null, null, "PF-20261113-13", PageRequest.of(0, 5));
        Page<PageResponse> otherStatus = workOrderService.findAll(
                line.getId(), "PRODUCE", null, null, "PF-20261113-13", PageRequest.of(0, 5));

        assertThat(matched.getContent()).extracting(PageResponse::workNo).containsExactly(workNo);
        assertThat(otherStatus.getContent()).isEmpty();
    }

    @Test
    @DisplayName("준비 상태에서 포장으로 건너뛰거나 같은 상태로 바꾸지 않는다")
    void rejectDisallowedStatusTransition() {
        LocalDate day = LocalDate.of(2026, 12, 15);
        Long workOrderId = workOrderService.create(request(13L, 4L, 4L, 100, day, day), 1L).getFirst().id();

        assertThatThrownBy(() -> workOrderService.changeStatus(
                workOrderId, new UpdateStatusRequest("PACK", null), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재 작업 상태에서는 요청한 상태로 변경할 수 없습니다.");
        assertThatThrownBy(() -> workOrderService.changeStatus(
                workOrderId, new UpdateStatusRequest("PREPARE", null), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 같은 상태입니다.");
        assertThat(workOrderRepository.findById(workOrderId).orElseThrow().getCommonCode().getCode())
                .isEqualTo("PREPARE");
    }

    @Test
    @DisplayName("생산 시작 예정일 전에는 생산을 시작하지 않는다")
    void rejectProduceBeforePlannedStart() {
        LocalDate day = LocalDate.of(2027, 12, 15);
        Long workOrderId = workOrderService.create(request(13L, 4L, 4L, 100, day, day), 1L).getFirst().id();

        assertThatThrownBy(() -> workOrderService.changeStatus(
                workOrderId, new UpdateStatusRequest("PRODUCE", null), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("생산 시작 예정일 전에는 시작할 수 없습니다.");
    }

    @Test
    @DisplayName("배정되지 않은 생산 현장 관리자는 작업 상태를 바꾸지 않는다")
    void rejectStatusChangeWhenUserIsNotAssignee() {
        LocalDate day = LocalDate.of(2026, 12, 15);
        Long workOrderId = workOrderService.create(request(13L, 4L, 4L, 100, day, day), 1L).getFirst().id();

        // 직원 5는 생산 현장 관리자이지만 이 작업의 담당자가 아니다.
        assertThatThrownBy(() -> workOrderService.changeStatus(
                workOrderId, new UpdateStatusRequest("HALT", null), 5L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("배정된 생산 현장 관리자와 최고 관리자만 작업 상태를 바꿀 수 있습니다.");
    }

    @Test
    @DisplayName("취소된 수주의 생산 중지 작업은 준비로 돌아가지 않는다")
    void rejectResumeWhenOrderIsCancelled() {
        LocalDate day = LocalDate.of(2026, 12, 15);
        Long workOrderId = workOrderService.create(request(13L, 4L, 4L, 100, day, day), 1L).getFirst().id();
        workOrderService.changeStatus(workOrderId, new UpdateStatusRequest("HALT", "수주 취소"), 1L);
        WorkOrder workOrder = workOrderRepository.findById(workOrderId).orElseThrow();
        workOrder.getOrderProduct().getCustomerOrder().changeProgress(em.find(CommonCode.class, 15L));
        em.flush();

        assertThatThrownBy(() -> workOrderService.changeStatus(
                workOrderId, new UpdateStatusRequest("PREPARE", null), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("취소된 수주의 작업은 다시 준비할 수 없습니다.");
        assertThat(workOrderRepository.findById(workOrderId).orElseThrow().getCommonCode().getCode())
                .isEqualTo("HALT");
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
        return "WO-" + Year.now().getValue() + "-" + String.format("%03d", sequence);
    }

    // 등록 응답의 하루 계획이 날짜, 수량, 작업지시 번호와 맞는지 본다.
    private void assertResponsePlans(Response response, List<LocalDate> dates, List<Integer> quantities) {
        assertThat(response.plans()).extracting(ProductionPlanDto.Response::planDate).containsExactlyElementsOf(dates);
        assertThat(response.plans())
                .extracting(ProductionPlanDto.Response::quantity)
                .containsExactlyElementsOf(quantities);
        assertThat(response.plans()).extracting(ProductionPlanDto.Response::workOrderId).containsOnly(response.id());
    }

    private LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }
}
