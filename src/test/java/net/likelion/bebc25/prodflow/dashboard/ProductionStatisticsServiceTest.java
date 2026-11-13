package net.likelion.bebc25.prodflow.dashboard;

import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.repository.OrderProductRepository;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.service.ProductionStatisticsService;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.line.repository.LineRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.repository.ProductionPlanRepository;
import net.likelion.bebc25.prodflow.work_order.repository.WorkOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ProductionStatisticsServiceTest {

    @Autowired
    private ProductionStatisticsService productionStatisticsService;

    @Autowired
    private OrderProductRepository orderProductRepository;

    @Autowired
    private LineRepository lineRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommonCodeRepository commonCodeRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private ProductionPlanRepository productionPlanRepository;

    @Test
    @DisplayName("선택한 기간의 일간 생산량 통계 조회")
    void getDailyProduction() {

        // 1. 일간 생산량 통계 조회
        LocalDate startDate = LocalDate.of(2026, 10, 1);
        LocalDate endDate = LocalDate.of(2026, 10, 7);

        List<DashboardDto.ProductionResponse> response =
                productionStatisticsService.getDailyProduction(startDate, endDate);

        // 2. 조회 결과 검증
        assertThat(response).isNotNull();
        assertThat(response).hasSize(7);

        // 3. 날짜별 데이터 검증
        for (int i = 0; i < response.size(); i++) {
            DashboardDto.ProductionResponse daily = response.get(i);

            assertThat(daily.date()).isEqualTo(startDate.plusDays(i));
            assertThat(daily.plannedQuantity()).isGreaterThanOrEqualTo(0);
            assertThat(daily.completedQuantity()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("선택한 월의 생산량 통계 조회")
    void getMonthlyProduction() {

        // 1. 월간 생산량 통계 조회
        DashboardDto.MonthlyProductionResponse response =
                productionStatisticsService.getMonthlyProduction(2026, 10);

        // 2. 조회 결과 검증
        assertThat(response).isNotNull();
        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.month()).isEqualTo(10);
        assertThat(response.plannedQuantity()).isGreaterThanOrEqualTo(0);
        assertThat(response.completedQuantity()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("선택한 월의 주차별 생산량 통계 조회")
    void getWeeklyProduction() {

        // 1. 주차별 생산량 통계 조회
        List<DashboardDto.WeeklyProductionResponse> response =
                productionStatisticsService.getWeeklyProduction(2026, 10);

        // 2. 조회 결과 검증
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
        assertThat(response).hasSizeBetween(4, 6);

        // 3. 주차별 데이터 검증
        for (DashboardDto.WeeklyProductionResponse weekly : response) {
            assertThat(weekly.week()).isPositive();
            assertThat(weekly.startDate()).isBeforeOrEqualTo(weekly.endDate());
            assertThat(weekly.plannedQuantity()).isGreaterThanOrEqualTo(0);
            assertThat(weekly.completedQuantity()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("일간 생산량 통계 조회 시 시작일이 종료일보다 늦으면 예외 발생")
    void getDailyProductionWithInvalidDateRange() {

        // 1. 시작일이 종료일보다 늦은 경우 예외 발생 검증
        LocalDate startDate = LocalDate.of(2026, 10, 7);
        LocalDate endDate = LocalDate.of(2026, 10, 1);

        assertThatThrownBy(() ->
                productionStatisticsService.getDailyProduction(startDate, endDate)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("시작일은 종료일보다 늦을 수 없습니다.");
    }

    @Test
    @DisplayName("월간 생산량 통계 조회 시 잘못된 월 입력")
    void getMonthlyProductionWithInvalidMonth() {

        // 1. 잘못된 월 입력 시 예외 발생 검증
        assertThatThrownBy(() ->
                productionStatisticsService.getMonthlyProduction(2026, 13)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("1월부터 12월까지 입력 가능합니다.");
    }

    @Test
    @DisplayName("2026년 2월은 일요일 시작 4주차까지 반환한다")
    void february2026HasFourSundayWeeks() {
        List<DashboardDto.WeeklyProductionResponse> weeks =
                productionStatisticsService.getWeeklyProduction(2026, 2);

        assertThat(weeks).extracting(DashboardDto.WeeklyProductionResponse::week)
                .containsExactly(1, 2, 3, 4);
        assertThat(weeks).extracting(DashboardDto.WeeklyProductionResponse::startDate)
                .containsExactly(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 8),
                        LocalDate.of(2026, 2, 15),
                        LocalDate.of(2026, 2, 22)
                );
        assertThat(weeks).extracting(DashboardDto.WeeklyProductionResponse::endDate)
                .containsExactly(
                        LocalDate.of(2026, 2, 7),
                        LocalDate.of(2026, 2, 14),
                        LocalDate.of(2026, 2, 21),
                        LocalDate.of(2026, 2, 28)
                );
        assertThat(weeks).allSatisfy(week -> {
            assertThat(week.plannedQuantity()).isGreaterThanOrEqualTo(0);
            assertThat(week.completedQuantity()).isGreaterThanOrEqualTo(0);
        });
    }

    @Test
    @DisplayName("2026년 11월과 12월, 2027년 2월은 일요일 시작 5주차까지 반환한다")
    void fiveWeekMonthsStopAtFifthSundayWeek() {
        assertSundayWeeks(2026, 11, List.of(
                new LocalDate[]{LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7)},
                new LocalDate[]{LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 14)},
                new LocalDate[]{LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 21)},
                new LocalDate[]{LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 28)},
                new LocalDate[]{LocalDate.of(2026, 11, 29), LocalDate.of(2026, 11, 30)}
        ));
        assertSundayWeeks(2026, 12, List.of(
                new LocalDate[]{LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5)},
                new LocalDate[]{LocalDate.of(2026, 12, 6), LocalDate.of(2026, 12, 12)},
                new LocalDate[]{LocalDate.of(2026, 12, 13), LocalDate.of(2026, 12, 19)},
                new LocalDate[]{LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 26)},
                new LocalDate[]{LocalDate.of(2026, 12, 27), LocalDate.of(2026, 12, 31)}
        ));
        assertSundayWeeks(2027, 2, List.of(
                new LocalDate[]{LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 6)},
                new LocalDate[]{LocalDate.of(2027, 2, 7), LocalDate.of(2027, 2, 13)},
                new LocalDate[]{LocalDate.of(2027, 2, 14), LocalDate.of(2027, 2, 20)},
                new LocalDate[]{LocalDate.of(2027, 2, 21), LocalDate.of(2027, 2, 27)},
                new LocalDate[]{LocalDate.of(2027, 2, 28), LocalDate.of(2027, 2, 28)}
        ));
    }

    @Test
    @DisplayName("2027년 1월은 일요일 시작 6주차까지 반환한다")
    void january2027HasSixSundayWeeks() {
        assertSundayWeeks(2027, 1, List.of(
                new LocalDate[]{LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2)},
                new LocalDate[]{LocalDate.of(2027, 1, 3), LocalDate.of(2027, 1, 9)},
                new LocalDate[]{LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 16)},
                new LocalDate[]{LocalDate.of(2027, 1, 17), LocalDate.of(2027, 1, 23)},
                new LocalDate[]{LocalDate.of(2027, 1, 24), LocalDate.of(2027, 1, 30)},
                new LocalDate[]{LocalDate.of(2027, 1, 31), LocalDate.of(2027, 1, 31)}
        ));
    }

    @Test
    @DisplayName("월을 넘나드는 계획량은 해당 월의 일요일 시작 주차에만 합산된다")
    void plannedQuantityFollowsSundayWeekInsideMonth() {
        List<DashboardDto.WeeklyProductionResponse> novemberBefore =
                productionStatisticsService.getWeeklyProduction(2026, 11);
        List<DashboardDto.WeeklyProductionResponse> decemberBefore =
                productionStatisticsService.getWeeklyProduction(2026, 12);
        List<DashboardDto.WeeklyProductionResponse> januaryBefore =
                productionStatisticsService.getWeeklyProduction(2027, 1);
        OrderProduct product = orderProductRepository.findById(1L).orElseThrow();
        Line line = lineRepository.findById(1L).orElseThrow();
        User user = userRepository.findById(4L).orElseThrow();
        CommonCode prepare = commonCodeRepository.findById(16L).orElseThrow();

        savePlan("WI-WEEK-SAT", product, line, user, prepare, LocalDate.of(2026, 11, 7), 37);
        savePlan("WI-WEEK-MON", product, line, user, prepare, LocalDate.of(2026, 11, 30), 41);
        savePlan("WI-WEEK-JAN", product, line, user, prepare, LocalDate.of(2027, 1, 1), 53);

        List<DashboardDto.WeeklyProductionResponse> november =
                productionStatisticsService.getWeeklyProduction(2026, 11);
        List<DashboardDto.WeeklyProductionResponse> december =
                productionStatisticsService.getWeeklyProduction(2026, 12);
        List<DashboardDto.WeeklyProductionResponse> january =
                productionStatisticsService.getWeeklyProduction(2027, 1);

        assertThat(november).extracting(DashboardDto.WeeklyProductionResponse::week)
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(planned(november, 1)).isEqualTo(planned(novemberBefore, 1) + 37);
        assertThat(planned(november, 2)).isEqualTo(planned(novemberBefore, 2));
        assertThat(planned(november, 5)).isEqualTo(planned(novemberBefore, 5) + 41);
        assertThat(december).hasSize(5);
        assertThat(december).extracting(DashboardDto.WeeklyProductionResponse::plannedQuantity)
                .containsExactlyElementsOf(decemberBefore.stream()
                        .map(DashboardDto.WeeklyProductionResponse::plannedQuantity).toList());
        assertThat(january).extracting(DashboardDto.WeeklyProductionResponse::week)
                .containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(planned(january, 1)).isEqualTo(planned(januaryBefore, 1) + 53);
        assertThat(january).filteredOn(week -> week.week() > 1)
                .allSatisfy(week -> assertThat(week.plannedQuantity())
                        .isEqualTo(planned(januaryBefore, week.week())));
    }

    @Test
    @DisplayName("주차별 생산량 통계 조회 시 잘못된 월 입력")
    void getWeeklyProductionWithInvalidMonth() {

        // 1. 잘못된 월 입력 시 예외 발생 검증
        assertThatThrownBy(() ->
                productionStatisticsService.getWeeklyProduction(2026, 0)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("1월부터 12월까지 입력 가능합니다.");
    }

    @Test
    @DisplayName("시작일과 종료일이 같으면 해당 날짜만 조회한다")
    void dailyProductionOnBoundaryDate() {
        LocalDate date = LocalDate.of(2026, 11, 13);

        List<DashboardDto.ProductionResponse> response =
                productionStatisticsService.getDailyProduction(date, date);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().date()).isEqualTo(date);
        assertThat(response.getFirst().plannedQuantity()).isEqualTo(300);
        assertThat(response.getFirst().completedQuantity()).isZero();
    }

    @Test
    @DisplayName("생산 데이터가 없는 기간은 0이다")
    void emptyProductionPeriod() {
        DashboardDto.MonthlyProductionResponse response =
                productionStatisticsService.getMonthlyProduction(2030, 1);

        assertThat(response.plannedQuantity()).isZero();
        assertThat(response.completedQuantity()).isZero();
    }

    @Test
    @DisplayName("취소 수주의 생산 계획은 집계하지 않는다")
    void cancelledOrderPlanIsExcluded() {
        DashboardDto.MonthlyProductionResponse before =
                productionStatisticsService.getMonthlyProduction(2026, 10);
        OrderProduct cancelledProduct = orderProductRepository.findById(11L).orElseThrow();
        Line line = lineRepository.findById(1L).orElseThrow();
        User user = userRepository.findById(4L).orElseThrow();
        CommonCode prepare = commonCodeRepository.findById(16L).orElseThrow();

        WorkOrder workOrder = new WorkOrder(
                "WI-CANCEL-TEST",
                cancelledProduct,
                line,
                user,
                100,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 1),
                "취소 수주"
        );
        workOrder.changeStatus(prepare);
        workOrderRepository.save(workOrder);
        productionPlanRepository.save(
                new ProductionPlan(workOrder, LocalDate.of(2026, 10, 1), 100)
        );

        DashboardDto.MonthlyProductionResponse response =
                productionStatisticsService.getMonthlyProduction(2026, 10);

        assertThat(response.plannedQuantity()).isEqualTo(before.plannedQuantity());
        assertThat(response.completedQuantity()).isEqualTo(before.completedQuantity());
    }

    private void assertSundayWeeks(int year, int month, List<LocalDate[]> ranges) {
        List<DashboardDto.WeeklyProductionResponse> weeks =
                productionStatisticsService.getWeeklyProduction(year, month);

        assertThat(weeks).extracting(DashboardDto.WeeklyProductionResponse::week)
                .containsExactlyElementsOf(
                        IntStream.rangeClosed(1, ranges.size()).boxed().toList()
                );
        assertThat(weeks).extracting(DashboardDto.WeeklyProductionResponse::startDate)
                .containsExactly(ranges.stream().map(range -> range[0]).toArray(LocalDate[]::new));
        assertThat(weeks).extracting(DashboardDto.WeeklyProductionResponse::endDate)
                .containsExactly(ranges.stream().map(range -> range[1]).toArray(LocalDate[]::new));
    }

    private void savePlan(
            String workNo,
            OrderProduct product,
            Line line,
            User user,
            CommonCode status,
            LocalDate planDate,
            int quantity
    ) {
        WorkOrder workOrder = new WorkOrder(
                workNo,
                product,
                line,
                user,
                quantity,
                planDate,
                planDate,
                "일요일 시작 주차"
        );
        workOrder.changeStatus(status);
        workOrderRepository.save(workOrder);
        productionPlanRepository.save(new ProductionPlan(workOrder, planDate, quantity));
    }

    private long planned(List<DashboardDto.WeeklyProductionResponse> weeks, int week) {
        return weeks.stream()
                .filter(item -> item.week() == week)
                .mapToLong(DashboardDto.WeeklyProductionResponse::plannedQuantity)
                .findFirst()
                .orElseThrow();
    }
}
