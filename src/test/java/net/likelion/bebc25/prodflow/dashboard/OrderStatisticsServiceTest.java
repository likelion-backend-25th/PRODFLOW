package net.likelion.bebc25.prodflow.dashboard;

import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.service.OrderStatisticsService;
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
class OrderStatisticsServiceTest {

    @Autowired
    private OrderStatisticsService orderStatisticsService;

    @Test
    @DisplayName("전체 수주 건수와 10월 수주 건수를 조회한다")
    void getOrderStatistics() {

        DashboardDto.OrderStatisticsResponse response =
                orderStatisticsService.getOrderStatistics(
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 31)
                );

        // 취소 수주를 제외한 전체 수주 12건
        assertThat(response.totalOrderCount()).isEqualTo(12);

        // 10월 등록 수주 10건
        assertThat(response.periodOrderCount()).isEqualTo(9);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 예외가 발생한다")
    void invalidDateRange() {

        assertThatThrownBy(() ->
                orderStatisticsService.getOrderStatistics(
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
                orderStatisticsService.getOrderStatistics(
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
                orderStatisticsService.getOrderStatistics(
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
                orderStatisticsService.getOrderStatistics(
                        LocalDate.of(2026, 10, 20),
                        LocalDate.of(2026, 10, 20)
                );

        // 취소 수주를 제외한 전체 수주 12건
        assertThat(response.totalOrderCount()).isEqualTo(12);

        assertThat(response.periodOrderCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("조회 기간에 수주가 없으면 0건을 반환한다")
    void noOrdersInPeriod() {

        DashboardDto.OrderStatisticsResponse response =
                orderStatisticsService.getOrderStatistics(
                        LocalDate.of(2030, 1, 1),
                        LocalDate.of(2030, 1, 31)
                );

        // 취소 수주를 제외한 전체 수주 12건
        assertThat(response.totalOrderCount()).isEqualTo(12);

        assertThat(response.periodOrderCount()).isZero();
    }

    @Test
    @DisplayName("수주 상태별 현황을 조회한다")
    void getOrderStatusStatistics() {

        // when
        List<DashboardDto.OrderStatusStatisticsResponse> result =
                orderStatisticsService.getOrderStatusStatistics();

        // then
        assertThat(result).isNotEmpty();

        assertThat(result)
                .extracting(DashboardDto.OrderStatusStatisticsResponse::statusCode)
                .containsExactlyInAnyOrder(
                        "WAITING",
                        "PRODUCTION_READY",
                        "IN_PRODUCTION",
                        "PACKING",
                        "PACK_DONE",
                        "READY_TO_SHIP",
                        "SHIPPING",
                        "COMPLETED",
                        "ON_HOLD",
                        "CANCELLED"
                );

        assertThat(result)
                .allSatisfy(status -> {
                    assertThat(status.statusCode()).isNotBlank();
                    assertThat(status.statusName()).isNotBlank();
                    assertThat(status.orderCount()).isGreaterThanOrEqualTo(0);
                });
    }


    @Test
    @DisplayName("생산 중인 수주 현황을 조회한다")
    void getInProductionOrderCount() {

        // when
        List<DashboardDto.OrderStatusStatisticsResponse> result =
                orderStatisticsService.getOrderStatusStatistics();

        // then
        DashboardDto.OrderStatusStatisticsResponse producingStatus =
                result.stream()
                        .filter(status -> "IN_PRODUCTION".equals(status.statusCode()))
                        .findFirst()
                        .orElseThrow();

        assertThat(producingStatus.statusCode()).isEqualTo("IN_PRODUCTION");
        assertThat(producingStatus.statusName()).isEqualTo("생산중");
        assertThat(producingStatus.orderCount()).isEqualTo(1L);
    }


    @Test
    @DisplayName("수주 상태별 통계는 상태 코드별로 한 건씩 반환한다")
    void orderStatusStatisticsAreGroupedByStatus() {

        // when
        List<DashboardDto.OrderStatusStatisticsResponse> result =
                orderStatisticsService.getOrderStatusStatistics();

        // then
        List<String> statusCodes = result.stream()
                .map(DashboardDto.OrderStatusStatisticsResponse::statusCode)
                .toList();

        assertThat(statusCodes)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("주문 상태별 통계를 조회한다")
    void getOrderStatusStatistics_statusRead() {

        // when
        List<DashboardDto.OrderStatusStatisticsResponse> result =
                orderStatisticsService.getOrderStatusStatistics();

        // then
        assertThat(result).isNotEmpty();

        assertThat(result)
                .extracting(DashboardDto.OrderStatusStatisticsResponse::statusCode)
                .contains("WAITING", "IN_PRODUCTION", "CANCELLED");
    }

    @Test
    @DisplayName("주문이 없는 상태는 통계 결과에 포함되지 않는다")
    void getOrderStatusStatistics_noneOrderStatus() {

        // when
        List<DashboardDto.OrderStatusStatisticsResponse> result =
                orderStatisticsService.getOrderStatusStatistics();

        // then
        assertThat(result)
                .extracting(DashboardDto.OrderStatusStatisticsResponse::statusCode)
                .doesNotContain("CONFIRMED");
    }

    @Test
    @DisplayName("주문 상태별 통계는 상태 코드 오름차순으로 반환된다")
    void getOrderStatusStatistics_statusArray() {
        // when
        List<DashboardDto.OrderStatusStatisticsResponse> result =
                orderStatisticsService.getOrderStatusStatistics();

        // then
        assertThat(result)
                .extracting(DashboardDto.OrderStatusStatisticsResponse::statusCode)
                .containsExactly(
                        "CANCELLED",
                        "COMPLETED",
                        "IN_PRODUCTION",
                        "ON_HOLD",
                        "PACK_DONE",
                        "PACKING",
                        "PRODUCTION_READY",
                        "READY_TO_SHIP",
                        "SHIPPING",
                        "WAITING"
                );
    }
}