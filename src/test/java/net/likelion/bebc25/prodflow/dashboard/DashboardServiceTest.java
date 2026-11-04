package net.likelion.bebc25.prodflow.dashboard;

import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class DashboardServiceTest {

    @Autowired
    private DashboardService dashboardService;

    private DashboardDto.Request createRequest() {
        return new DashboardDto.Request(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );
    }

    @Test
    @DisplayName("STAT-01 대시보드 통합 조회 결과를 반환한다")
    void getDashboard() {
        // when
        DashboardDto.DashboardResponse response =
                dashboardService.getDashboard(createRequest());

        // then
        assertThat(response).isNotNull();
        assertThat(response.summary()).isNotNull();
        assertThat(response.orderStatistics()).isNotNull();
        assertThat(response.orderStatusStatistics()).isNotNull();
        assertThat(response.productionStatistics()).isNotNull();
        assertThat(response.monthlyProductionStatistics()).isNotNull();
        assertThat(response.weeklyProductionStatistics()).isNotNull();
        assertThat(response.deliveryPerformance()).isNotNull();
        assertThat(response.productStatistics()).isNotNull();
        assertThat(response.productProductionShare()).isNotNull();
        assertThat(response.deliveryRiskStatistics()).isNotNull();
    }

    @Test
    @DisplayName("대시보드 요약의 수주 건수는 수주 통계와 일치한다")
    void summaryOrderCountMatchesOrderStatistics() {
        // when
        DashboardDto.DashboardResponse response =
                dashboardService.getDashboard(createRequest());

        // then
        assertThat(response.summary().totalOrderCount())
                .isEqualTo(response.orderStatistics().totalOrderCount());
    }

    @Test
    @DisplayName("대시보드 요약의 생산 계획량과 완료량은 날짜별 통계 합계와 일치한다")
    void summaryProductionQuantityMatchesDailyStatistics() {
        // when
        DashboardDto.DashboardResponse response =
                dashboardService.getDashboard(createRequest());

        long plannedQuantity = response.productionStatistics().stream()
                .mapToLong(DashboardDto.ProductionResponse::plannedQuantity)
                .sum();

        long completedQuantity = response.productionStatistics().stream()
                .mapToLong(DashboardDto.ProductionResponse::completedQuantity)
                .sum();

        // then
        assertThat(response.summary().plannedQuantity())
                .isEqualTo(plannedQuantity);

        assertThat(response.summary().completedQuantity())
                .isEqualTo(completedQuantity);
    }

    @Test
    @DisplayName("생산 진행률은 계획량 대비 완료량으로 계산된다")
    void productionProgressRateIsCalculatedCorrectly() {
        // when
        DashboardDto.DashboardResponse response =
                dashboardService.getDashboard(createRequest());

        long plannedQuantity = response.summary().plannedQuantity();
        long completedQuantity = response.summary().completedQuantity();

        double expectedRate = plannedQuantity == 0
                ? 0.0
                : Math.round((double) completedQuantity / plannedQuantity * 100 * 100.0) / 100.0;

        // then
        assertThat(response.summary().productionProgressRate())
                .isEqualTo(expectedRate);
    }

    @Test
    @DisplayName("대시보드 월간 생산 통계는 조회 종료일 기준 월을 반환한다")
    void monthlyProductionUsesEndDateMonth() {
        // given
        DashboardDto.Request request = new DashboardDto.Request(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 15)
        );

        // when
        DashboardDto.DashboardResponse response =
                dashboardService.getDashboard(request);

        // then
        assertThat(response.monthlyProductionStatistics())
                .anySatisfy(item -> {
                    assertThat(item.year()).isEqualTo(2026);
                    assertThat(item.month()).isEqualTo(11);
                });
    }

    @Test
    @DisplayName("조회 시작일이 종료일보다 늦으면 예외가 발생한다")
    void getDashboardWithInvalidDateRange() {
        // given
        DashboardDto.Request request = new DashboardDto.Request(
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1)
        );

        // when & then
        assertThatThrownBy(() -> dashboardService.getDashboard(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 시작을은 종료일보다 늦을 수 없습니다.");
    }
}