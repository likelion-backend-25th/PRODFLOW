package net.likelion.bebc25.prodflow.dashboard;

import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.service.DeliveryPerformanceService;
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
class DeliveryPerformanceServiceTest {

    @Autowired
    private DeliveryPerformanceService deliveryPerformanceService;

    @Test
    @DisplayName("조회 시작일이 null이면 예외가 발생한다")
    void getDeliveryPerformanceWithNullFrom() {
        // given
        LocalDate from = null;
        LocalDate to = LocalDate.of(2026, 10, 31);

        // when & then
        assertThatThrownBy(() ->
                deliveryPerformanceService.getDeliveryPerformance(from, to)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회일과 종료일을 입력해야합니다.");
    }

    @Test
    @DisplayName("조회 종료일이 null이면 예외가 발생한다")
    void getDeliveryPerformanceWithNullTo() {
        // given
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = null;

        // when & then
        assertThatThrownBy(() ->
                deliveryPerformanceService.getDeliveryPerformance(from, to)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회일과 종료일을 입력해야합니다.");
    }

    @Test
    @DisplayName("조회 시작일이 종료일보다 늦으면 예외가 발생한다")
    void getDeliveryPerformanceWithInvalidDateRange() {
        // given
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 10, 1);

        // when & then
        assertThatThrownBy(() ->
                deliveryPerformanceService.getDeliveryPerformance(from, to)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회일은 종료일보다 늦을 수 없습니다.");
    }

    @Test
    @DisplayName("조회 기간이 같아도 정상적으로 조회할 수 있다")
    void getDeliveryPerformanceWithSameDateRange() {
        // given
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 1);

        // when
        DashboardDto.DeliveryPerformanceResponse response =
                deliveryPerformanceService.getDeliveryPerformance(from, to);

        // then
        assertThat(response).isNotNull();
        assertThat(response.totalOrderCount()).isGreaterThanOrEqualTo(0);
        assertThat(response.onTimeOrderCount()).isGreaterThanOrEqualTo(0);
        assertThat(response.onTimeDeliveryRate()).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("조회 기간에 주문이 없으면 이행률은 0이다")
    void getDeliveryPerformanceWithNoOrders() {
        // given
        LocalDate from = LocalDate.of(2099, 1, 1);
        LocalDate to = LocalDate.of(2099, 1, 31);

        // when
        DashboardDto.DeliveryPerformanceResponse response =
                deliveryPerformanceService.getDeliveryPerformance(from, to);

        // then
        assertThat(response.totalOrderCount()).isZero();
        assertThat(response.onTimeOrderCount()).isZero();
        assertThat(response.onTimeDeliveryRate()).isEqualTo(0.0);
    }
}