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
public class DashboardServiceTest {

    @Autowired
    private DashboardService dashboardService;

    @Test
    @DisplayName("전체 수주 건수와 10월 수주 건수를 조회한다")
    void getOrderStatistics() {

        DashboardDto.OrderStatisticsResponse response =
                dashboardService.getOrderStatistics(
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 31)
                );

        // 전체 수주 13건
        assertThat(response.totalOrderCount()).isEqualTo(13);

        // 10월 등록 수주 10건
        assertThat(response.periodOrderCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 예외가 발생한다")
    void invalidDateRange() {

        assertThatThrownBy(() ->
                dashboardService.getOrderStatistics(
                        LocalDate.of(2026, 10, 31),
                        LocalDate.of(2026, 10, 1)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 시작일은 종료일보다 늦을 수 없습니다.");
    }

    @Test
    @DisplayName("시작일이 null이면 예외가 발생한다")
    void nullStartDate() {

        assertThatThrownBy(() ->
                dashboardService.getOrderStatistics(
                        null,
                        LocalDate.of(2026, 10, 31)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 시작일과 종료일은 필수입니다.");
    }

    @Test
    @DisplayName("종료일이 null이면 예외가 발생한다")
    void nullEndDate() {

        assertThatThrownBy(() ->
                dashboardService.getOrderStatistics(
                        LocalDate.of(2026, 10, 1),
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 시작일과 종료일은 필수입니다.");
    }

    @Test
    @DisplayName("시작일과 종료일이 같아도 정상 조회한다")
    void sameDateRange() {

        DashboardDto.OrderStatisticsResponse response =
                dashboardService.getOrderStatistics(
                        LocalDate.of(2026, 10, 20),
                        LocalDate.of(2026, 10, 20)
                );

        assertThat(response.totalOrderCount()).isEqualTo(13);
        assertThat(response.periodOrderCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("조회 기간에 수주가 없으면 0건을 반환한다")
    void noOrdersInPeriod() {

        DashboardDto.OrderStatisticsResponse response =
                dashboardService.getOrderStatistics(
                        LocalDate.of(2030, 1, 1),
                        LocalDate.of(2030, 1, 31)
                );

        assertThat(response.totalOrderCount()).isEqualTo(13);
        assertThat(response.periodOrderCount()).isZero();
    }
}
