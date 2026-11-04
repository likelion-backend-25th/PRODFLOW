package net.likelion.bebc25.prodflow.dashboard;

import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.service.ProductionStatisticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ProductionStatisticsServiceTest {

    @Autowired
    private ProductionStatisticsService productionStatisticsService;

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
    @DisplayName("주차별 생산량 통계 조회 시 잘못된 월 입력")
    void getWeeklyProductionWithInvalidMonth() {

        // 1. 잘못된 월 입력 시 예외 발생 검증
        assertThatThrownBy(() ->
                productionStatisticsService.getWeeklyProduction(2026, 0)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("1월부터 12월까지 입력 가능합니다.");
    }
}