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
    @DisplayName("전체 기간별 수주 건수를 조회")
    void getOrderStatistics() {
        // 1. 전체 수주 및 10월 수주 건수 조회
        DashboardDto.OrderStatisticsResponse response = dashboardService.getOrderStatistics(
                LocalDate.of(2026, 10, 1), // 시작일
                LocalDate.of(2026, 10, 31) // 종료일
        );

        // 전체 수주 건수 검증
        assertThat(response.totalOrderCount()).isEqualTo(13);
        // 10월 수주 건수 검ㅈ으
        assertThat(response.periodOrderCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 예외 발생")
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
    @DisplayName("종료일이 null 이면 예외 발생")
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

}
