package net.likelion.bebc25.prodflow.dashboard;

import net.likelion.bebc25.prodflow.dashboard.controller.DashboardController;
import net.likelion.bebc25.prodflow.dashboard.controller.ProductProductionStatisticsController;
import net.likelion.bebc25.prodflow.dashboard.controller.ProductionStatisticsController;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class DashboardControllerTest {

    @Autowired
    private DashboardController dashboardController;

    @Autowired
    private ProductionStatisticsController productionStatisticsController;

    @Autowired
    private ProductProductionStatisticsController productProductionStatisticsController;

    @Test
    @DisplayName("대시보드 조회는 시작일이 종료일보다 늦으면 거절한다")
    void dashboardRejectsReversedRange() {
        DashboardDto.Request request = new DashboardDto.Request(
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1)
        );

        assertThatThrownBy(() -> dashboardController.getDashboard(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 시작일은 종료일보다 늦을 수 없습니다.");
    }

    @Test
    @DisplayName("월간 생산 조회는 잘못된 월을 거절한다")
    void monthlyProductionRejectsInvalidMonth() {
        assertThatThrownBy(() ->
                productionStatisticsController.getMonthlyProduction(2026, 13)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("1월부터 12월까지 입력 가능합니다.");
    }

    @Test
    @DisplayName("품목 생산 비중 조회는 month가 없으면 거절한다")
    void productShareRequiresMonth() {
        assertThatThrownBy(() ->
                productProductionStatisticsController.getProductProductionShare(" ")
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("month는 필수입니다.");
    }

    @Test
    @DisplayName("일간 생산 조회는 기간의 날짜 수를 반환한다")
    void dailyProductionReturnsRequestedDays() {
        var response = productionStatisticsController.getDailyProduction(
                LocalDate.of(2026, 11, 13),
                LocalDate.of(2026, 11, 13)
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().plannedQuantity()).isEqualTo(300);
    }
}
