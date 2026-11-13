package net.likelion.bebc25.prodflow.dashboard;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.repository.OrderStatisticsRepository;
import net.likelion.bebc25.prodflow.dashboard.service.OrderStatisticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class OrderStatisticsServiceTest {

    @Autowired
    private OrderStatisticsService orderStatisticsService;

    @Autowired
    private EntityManager em;

    @Autowired
    private OrderStatisticsRepository orderStatisticsRepository;

    @Test
    @DisplayName("전체 수주 건수와 10월 수주 건수를 조회한다")
    void getOrderStatistics() {

        DashboardDto.OrderStatisticsResponse response =
                orderStatisticsService.getOrderStatistics(
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 31)
                );

        assertThat(response.totalOrderCount()).isEqualTo(orderStatisticsRepository.countAllOrders());
        assertThat(response.periodOrderCount()).isEqualTo(periodCount(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
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

        assertThat(response.totalOrderCount()).isEqualTo(orderStatisticsRepository.countAllOrders());

        assertThat(response.periodOrderCount()).isEqualTo(periodCount(
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20)));
    }

    @Test
    @DisplayName("조회 기간에 수주가 없으면 0건을 반환한다")
    void noOrdersInPeriod() {

        DashboardDto.OrderStatisticsResponse response =
                orderStatisticsService.getOrderStatistics(
                        LocalDate.of(2030, 1, 1),
                        LocalDate.of(2030, 1, 31)
                );

        assertThat(response.totalOrderCount()).isEqualTo(orderStatisticsRepository.countAllOrders());

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
                        "CONFIRMED",
                        "IN_PRODUCTION",
                        "PACKING",
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
        Long producing = em.createQuery(
                        "select count(o) from CustomerOrder o where o.statusCode.code = 'IN_PRODUCTION'", Long.class)
                .getSingleResult();
        assertThat(producingStatus.orderCount()).isEqualTo(producing);
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

        // 생산대기 수주가 실제로 있는 시드에서 해당 상태의 수주만 비운다.
        CommonCode waiting = em.find(CommonCode.class, 6L);
        em.createQuery("update CustomerOrder o set o.statusCode = :waiting where o.statusCode.code = 'CONFIRMED'")
                .setParameter("waiting", waiting).executeUpdate();
        em.clear();

        // when
        List<DashboardDto.OrderStatusStatisticsResponse> result =
                orderStatisticsService.getOrderStatusStatistics();

        // then
        assertThat(result)
                .extracting(DashboardDto.OrderStatusStatisticsResponse::statusCode)
                .doesNotContain("CONFIRMED", "PACK_DONE");
    }

    @Test
    @DisplayName("주문 상태별 통계는 ORDER_STATUS 코드만 반환한다")
    void getOrderStatusStatistics_statusArray() {
        // when
        List<DashboardDto.OrderStatusStatisticsResponse> result =
                orderStatisticsService.getOrderStatusStatistics();

        // then
        assertThat(result)
                .extracting(DashboardDto.OrderStatusStatisticsResponse::statusCode)
                .containsExactlyInAnyOrder(
                        "CANCELLED",
                        "COMPLETED",
                        "CONFIRMED",
                        "IN_PRODUCTION",
                        "ON_HOLD",
                        "PACKING",
                        "READY_TO_SHIP",
                        "SHIPPING",
                        "WAITING"
                );
    }

    private long periodCount(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        return orderStatisticsRepository.countOrdersByPeriod(start, end);
    }
}
