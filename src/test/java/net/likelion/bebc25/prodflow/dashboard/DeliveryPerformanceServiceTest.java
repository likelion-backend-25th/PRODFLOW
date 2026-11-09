package net.likelion.bebc25.prodflow.dashboard;

import jakarta.persistence.EntityManager;

import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.service.DeliveryPerformanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(FixedDashboardClock.class)
@Transactional
class DeliveryPerformanceServiceTest {

    @Autowired
    private DeliveryPerformanceService deliveryPerformanceService;

    @Autowired
    private EntityManager em;

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

    @Test
    @DisplayName("배송 완료일이 요청 납기일 이전이면 정시 배송이다")
    void onTimeDeliveryUsesDeliveredDate() {
        LocalDate dueDate = FixedDashboardClock.TODAY.minusDays(1);
        em.createQuery("update CustomerOrder o set o.requestedDueDate = :date where o.id = 7")
                .setParameter("date", dueDate).executeUpdate();
        em.createQuery("update Shipment s set s.deliveredAt = :date where s.orderId = 7")
                .setParameter("date", dueDate.atTime(16, 0)).executeUpdate();

        DashboardDto.DeliveryPerformanceResponse response =
                deliveryPerformanceService.getDeliveryPerformance(dueDate, dueDate);

        assertThat(response.totalOrderCount()).isEqualTo(1);
        assertThat(response.onTimeOrderCount()).isEqualTo(1);
        assertThat(response.onTimeDeliveryRate()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("배송 완료 시각이 없으면 지연 배송이다")
    void missingDeliveryIsLate() {
        LocalDate dueDate = FixedDashboardClock.TODAY.minusDays(2);
        // 운송장 자체가 없는 생산중 수주와, 운송장은 있지만 배송중인 수주를 함께 검증한다.
        em.createQuery("update CustomerOrder o set o.requestedDueDate = :date where o.id in (2, 6)")
                .setParameter("date", dueDate).executeUpdate();

        DashboardDto.DeliveryPerformanceResponse response =
                deliveryPerformanceService.getDeliveryPerformance(dueDate, dueDate);

        assertThat(response.totalOrderCount()).isEqualTo(2);
        assertThat(response.onTimeOrderCount()).isZero();
        assertThat(response.onTimeDeliveryRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("오늘 이후 납기와 취소 수주는 분모에 포함되지 않는다")
    void futureAndCancelledOrdersAreExcluded() {
        LocalDate futureDate = FixedDashboardClock.TODAY.plusDays(1);
        LocalDate cancelledDate = LocalDate.of(2026, 1, 20);
        em.createQuery("update CustomerOrder o set o.requestedDueDate = :date where o.id = 11")
                .setParameter("date", cancelledDate).executeUpdate();

        DashboardDto.DeliveryPerformanceResponse future =
                deliveryPerformanceService.getDeliveryPerformance(futureDate, futureDate.plusDays(1));
        DashboardDto.DeliveryPerformanceResponse cancelled =
                deliveryPerformanceService.getDeliveryPerformance(cancelledDate, cancelledDate);

        assertThat(future.totalOrderCount()).isZero();
        assertThat(cancelled.totalOrderCount()).isZero();
    }
}
