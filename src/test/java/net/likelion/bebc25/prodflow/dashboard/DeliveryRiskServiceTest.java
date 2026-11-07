package net.likelion.bebc25.prodflow.dashboard;

import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.service.DeliveryRiskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(FixedDashboardClock.class)
@Transactional
class DeliveryRiskServiceTest {

    @Autowired
    private DeliveryRiskService deliveryRiskService;

    @Test
    @DisplayName("납기 위험 수주 목록을 조회한다")
    void getDeliveryRisks() {
        // when
        DashboardDto.DeliveryRiskStatisticsResponse response =
                deliveryRiskService.getDeliveryRisks();

        // then
        assertThat(response).isNotNull();
        assertThat(response.items()).isNotNull();
        assertThat(response.totalRiskOrderCount()).isEqualTo(response.items().size());
    }

    @Test
    @DisplayName("납기 임박 수주가 조회된다")
    void getUrgentDeliveryRisk() {
        // given
        String orderNo = "PF-2026-009";

        // when
        DashboardDto.DeliveryRiskStatisticsResponse response =
                deliveryRiskService.getDeliveryRisks();

        // then
        assertThat(response.items())
                .anySatisfy(item -> {
                    assertThat(item.orderNo()).isEqualTo(orderNo);
                    assertThat(item.riskType()).isEqualTo("URGENT");
                    assertThat(item.requestedDueDate()).isEqualTo(LocalDate.of(2026, 11, 6));
                });
    }

    @Test
    @DisplayName("납기일이 지난 수주는 지연 상태로 조회된다")
    void getDelayedDeliveryRisk() {
        // given
        String orderNo = "PF-2026-012";

        // when
        DashboardDto.DeliveryRiskStatisticsResponse response =
                deliveryRiskService.getDeliveryRisks();

        // then
        assertThat(response.items())
                .anySatisfy(item -> {
                    assertThat(item.orderNo()).isEqualTo(orderNo);
                    assertThat(item.riskType()).isEqualTo("DELAYED");
                    assertThat(item.riskReason()).isEqualTo("납기일이 지났습니다.");
                });
    }

    @Test
    @DisplayName("납기 위험 수주는 납기일이 빠른 순서로 정렬된다")
    void getDeliveryRisksSortedByDueDate() {
        // when
        DashboardDto.DeliveryRiskStatisticsResponse response =
                deliveryRiskService.getDeliveryRisks();

        // then
        List<LocalDate> dueDates = response.items().stream()
                .map(DashboardDto.DeliveryRiskStatisticsItemResponse::requestedDueDate)
                .toList();

        assertThat(dueDates).isSortedAccordingTo(LocalDate::compareTo);
    }

    @Test
    @DisplayName("하나의 수주에 작업지시가 여러 개 있어도 한 건만 조회된다")
    void getDeliveryRisksDistinctByOrder() {
        // when
        DashboardDto.DeliveryRiskStatisticsResponse response =
                deliveryRiskService.getDeliveryRisks();

        // then
        List<Long> orderIds = response.items().stream()
                .map(DashboardDto.DeliveryRiskStatisticsItemResponse::orderId)
                .toList();

        assertThat(orderIds).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("납기 위험 수주 개수는 조회된 목록의 개수와 일치한다")
    void getDeliveryRiskCount() {
        // when
        DashboardDto.DeliveryRiskStatisticsResponse response =
                deliveryRiskService.getDeliveryRisks();

        // then
        assertThat(response.totalRiskOrderCount()).isEqualTo(response.items().size());
    }

    @Test
    @DisplayName("납기 위험 수주에 결제 상태와 결제 금액이 포함된다")
    void getDeliveryRiskPaymentInfo() {
        // when
        DashboardDto.DeliveryRiskStatisticsResponse response =
                deliveryRiskService.getDeliveryRisks();

        // then
        assertThat(response.items())
                .allSatisfy(item -> {
                    assertThat(item.paymentStatus()).isNotNull();
                    assertThat(item.paidAmount()).isGreaterThanOrEqualTo(0);
                });
    }

    @Test
    @DisplayName("납기 위험 수주의 결제 상태는 수주 결제 상태와 같다")
    void getDeliveryRiskPaidStatus() {
        // given
        String orderNo = "PF-2026-009";

        // when
        DashboardDto.DeliveryRiskStatisticsResponse response =
                deliveryRiskService.getDeliveryRisks();

        // then
        assertThat(response.items())
                .anySatisfy(item -> {
                    if (item.orderNo().equals(orderNo)) {
                        assertThat(item.paymentStatus()).isEqualTo(OrderPaymentStatus.UNPAID);
                        assertThat(item.paidAmount()).isZero();
                    }
                });
    }

    @Test
    @DisplayName("여러 품목의 이름과 수량을 한 수주로 합친다")
    void joinsProductsOnOneOrder() {
        DashboardDto.DeliveryRiskStatisticsResponse response =
                deliveryRiskService.getDeliveryRisks();

        assertThat(response.items())
                .anySatisfy(item -> {
                    if ("PF-2026-001".equals(item.orderNo())) {
                        assertThat(item.productName()).isEqualTo(
                                "디자인 A 투명 케이스, 디자인 B 맥세이프 케이스, 디자인 C 카드수납 케이스"
                        );
                        assertThat(item.quantity()).isEqualTo(6000);
                    }
                });
    }

    @Test
    @DisplayName("미배송 수주는 작업지시 유무와 관계없이 포함하고 배송 완료와 취소는 뺀다")
    void includesUndeliveredOrdersOnly() {
        DashboardDto.DeliveryRiskStatisticsResponse response =
                deliveryRiskService.getDeliveryRisks();

        assertThat(response.items())
                .extracting(DashboardDto.DeliveryRiskStatisticsItemResponse::orderNo)
                .contains("PF-2026-006", "PF-2026-010")
                .doesNotContain("PF-2026-007", "PF-2026-011");
    }
}