package net.likelion.bebc25.prodflow.workorder;

import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.workorder.dto.WorkOrderDto;
import net.likelion.bebc25.prodflow.workorder.dto.WorkOrderDto.CreateRequest;
import net.likelion.bebc25.prodflow.workorder.dto.WorkOrderDto.ListResponse;
import net.likelion.bebc25.prodflow.workorder.dto.WorkOrderDto.Response;
import net.likelion.bebc25.prodflow.workorder.entity.ProductionPlan;
import net.likelion.bebc25.prodflow.workorder.entity.WorkOrder;
import net.likelion.bebc25.prodflow.workorder.repsitory.ProductionPlanRepository;
import net.likelion.bebc25.prodflow.workorder.repsitory.WorkOrderRepository;
import net.likelion.bebc25.prodflow.workorder.serivce.WorkOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.util.Comparator;
import java.util.List;

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

    @Test
    @DisplayName("한 라인의 기간 안에 들어가면 작업지시 한 장과 날짜별 계획이 저장된다")
    void createOnOneLineAcrossDays() {
        // 수주 품목 15번은 디자인 A 투명 케이스다. 수주 담당자는 이서준이다.
        // 12월 1일 화요일, 2일 수요일. 시드 생산 계획이 없는 날이다.
        CreateRequest request = request(15L, 1L, 4L, 1500, date(2026, 12, 1), date(2026, 12, 2));
        long historyCount = statusHistoryRepository.count();
        String workNo = workNo(workOrderRepository.count() + 1);

        // 토큰 직원 id는 넘기지만 작성자 이름은 수주 담당자를 쓴다.
        List<Response> created = workOrderService.create(request, 1L);

        assertThat(created).hasSize(1);
        assertThat(created.getFirst().workNo()).isEqualTo(workNo);
        assertThat(created.getFirst().note()).isEqualTo("디자인 A 투명 케이스 A라인 2026-12-01 ~ 2026-12-02");

        WorkOrder saved = workOrderRepository.findById(created.getFirst().id()).orElseThrow();
        assertThat(saved.getOrderProduct().getId()).isEqualTo(15L);
        assertThat(saved.getLine().getId()).isEqualTo(1L);
        assertThat(saved.getUser().getId()).isEqualTo(4L);
        assertThat(saved.getUser().getName()).isEqualTo("김태호");
        assertThat(saved.getQuantity()).isEqualTo(1500);
        assertThat(saved.getPlannedStartDate()).isEqualTo(date(2026, 12, 1));
        assertThat(saved.getPlannedEndDate()).isEqualTo(date(2026, 12, 2));
        assertThat(saved.getCommonCode().getCode()).isEqualTo("PREPARE");
        // 서비스는 수주 담당자 이서준을 넣는다. 로그인 사용자가 없으면 Auditing이 SYSTEM으로 덮어쓴다.
        assertThat(saved.getCreatedBy()).isEqualTo("SYSTEM");
        assertThat(saved.getUpdatedBy()).isEqualTo("SYSTEM");
        assertThat(saved.getCompletedAt()).isNull();

        List<ProductionPlan> plans = plansOf(saved.getId());
        assertThat(plans).extracting(ProductionPlan::getPlanDate)
                .containsExactly(date(2026, 12, 1), date(2026, 12, 2));
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
        // A라인 이틀은 2,000개다. 2,500개면 다음 라인에 500개가 넘어간다.
        CreateRequest request = request(15L, 1L, 4L, 2500, date(2026, 12, 1), date(2026, 12, 2));
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

        assertThat(second.getLine().getId()).isNotIn(1L, 4L, 5L);
        assertThat(second.getQuantity()).isEqualTo(500);
        assertThat(plansOf(second.getId())).extracting(ProductionPlan::getPlanDate)
                .containsExactly(date(2026, 12, 1));
        assertThat(plansOf(second.getId())).extracting(ProductionPlan::getQuantity).containsExactly(500);
        assertThat(first.getQuantity() + second.getQuantity()).isEqualTo(2500);
    }

    @Test
    @DisplayName("토요일과 일요일은 생산 계획에서 빠진다")
    void skipWeekend() {
        // 12월 4일 금요일, 7일 월요일. 5일과 6일은 주말이다.
        CreateRequest request = request(15L, 1L, 4L, 1500, date(2026, 12, 4), date(2026, 12, 7));

        List<Response> created = workOrderService.create(request, 1L);

        assertThat(created).hasSize(1);
        List<ProductionPlan> plans = plansOf(created.getFirst().id());
        assertThat(plans).extracting(ProductionPlan::getPlanDate)
                .containsExactly(date(2026, 12, 4), date(2026, 12, 7));
        assertThat(plans).extracting(ProductionPlan::getQuantity).containsExactly(1000, 500);
        assertThat(plans).allMatch(plan -> plan.getPlanDate().getDayOfWeek() != DayOfWeek.SATURDAY
                && plan.getPlanDate().getDayOfWeek() != DayOfWeek.SUNDAY);
    }

    @Test
    @DisplayName("성탄절은 생산 계획에서 빠진다")
    void skipHoliday() {
        // 12월 24일 목요일, 25일 성탄절, 28일 월요일.
        CreateRequest request = request(15L, 1L, 4L, 1500, date(2026, 12, 24), date(2026, 12, 28));

        List<Response> created = workOrderService.create(request, 1L);

        List<ProductionPlan> plans = plansOf(created.getFirst().id());
        assertThat(plans).extracting(ProductionPlan::getPlanDate)
                .containsExactly(date(2026, 12, 24), date(2026, 12, 28));
        assertThat(plans).extracting(ProductionPlan::getQuantity).containsExactly(1000, 500);
    }

    @Test
    @DisplayName("기간 안 생산 가능량을 넘기면 예외가 난다")
    void rejectWhenQuantityExceedsCapacity() {
        // 가동 라인 3개, 이틀, 하루 1,000개. 최대 6,000개다.
        CreateRequest request = request(15L, 1L, 4L, 6001, date(2026, 12, 1), date(2026, 12, 2));

        assertThatThrownBy(() -> workOrderService.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("선택한 기간의 생산 가능량을 초과했습니다.");
    }

    @Test
    @DisplayName("고장 라인을 첫 라인으로 고르면 배정하지 않는다")
    void rejectBrokenLine() {
        CreateRequest request = request(15L, 4L, 4L, 100, date(2026, 12, 1), date(2026, 12, 1));

        assertThatThrownBy(() -> workOrderService.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("점검·고장 라인에는 배정할 수 없습니다.");
    }

    @Test
    @DisplayName("목록은 상태와 상관없이 완료 예정일이 빠른 순이고 같은 날이면 최근 등록이 앞이다")
    void listByPlannedEndDateThenLatestRegistration() {
        // 12월 1일은 시드 계획이 없는 화요일이다. 같은 완료 예정일로 두 장을 만든다.
        Response firstOnSameDay = workOrderService.create(
                request(15L, 1L, 4L, 100, date(2026, 12, 1), date(2026, 12, 1)), 1L).getFirst();
        Response secondOnSameDay = workOrderService.create(
                request(15L, 1L, 4L, 100, date(2026, 12, 1), date(2026, 12, 1)), 1L).getFirst();
        // 12월 3일은 더 늦은 완료 예정일이다.
        Response laterDay = workOrderService.create(
                request(15L, 1L, 4L, 100, date(2026, 12, 3), date(2026, 12, 3)), 1L).getFirst();

        List<ListResponse> list = workOrderService.getList();

        assertThat(list).hasSize((int) workOrderRepository.count());
        assertThat(list).isSortedAccordingTo(Comparator.comparing(ListResponse::plannedEndDate)
                .thenComparing(Comparator.comparing(ListResponse::id).reversed()));

        List<String> workNos = list.stream().map(ListResponse::workNo).toList();
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

        ListResponse row = list.get(laterRegistration);
        assertThat(row.id()).isEqualTo(secondOnSameDay.id());
        assertThat(row.orderNo()).isEqualTo("PF-2026-013");
        assertThat(row.clientName()).isEqualTo("세원모바일(주)");
        assertThat(row.productName()).isEqualTo("디자인 A 투명 케이스");
        assertThat(row.quantity()).isEqualTo(100);
        assertThat(row.lineName()).isEqualTo("A라인");
        assertThat(row.assignedName()).isEqualTo("김태호");
        assertThat(row.workOrderDate()).isEqualTo(LocalDate.now());
        assertThat(row.plannedEndDate()).isEqualTo(date(2026, 12, 1));
        assertThat(row.status()).isEqualTo("PREPARE");
    }

    @Test
    @DisplayName("없는 수주 품목이면 예외가 난다")
    void rejectMissingOrderProduct() {
        CreateRequest request = request(999L, 1L, 4L, 100, date(2026, 12, 1), date(2026, 12, 1));

        assertThatThrownBy(() -> workOrderService.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수주 품목을 찾을 수 없습니다.");
    }

    private CreateRequest request(Long orderProductId, Long lineId, Long assignedId, int quantity,
                                  LocalDate start, LocalDate end) {
        return new WorkOrderDto.CreateRequest(orderProductId, lineId, assignedId, quantity, start, end);
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
