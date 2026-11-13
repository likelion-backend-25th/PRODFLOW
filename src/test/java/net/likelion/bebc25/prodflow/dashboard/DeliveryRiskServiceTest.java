package net.likelion.bebc25.prodflow.dashboard;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import java.util.Map;

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

    @Autowired
    private EntityManager em;

    @BeforeEach
    void setUpRiskDates() {
        // 임박·지연·미입금·다품목 조건을 시드의 절대 날짜와 독립적으로 구성한다.
        Map<Long, Integer> offsets = Map.of(1L, 1, 9L, 2, 10L, 3, 12L, -1);
        offsets.forEach((id, offset) -> em.createQuery(
                        "update CustomerOrder o set o.requestedDueDate = :date where o.id = :id")
                .setParameter("date", FixedDashboardClock.TODAY.plusDays(offset))
                .setParameter("id", id).executeUpdate());
        em.clear();
    }

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
        String orderNo = "PF-20261106-9";

        // when
        DashboardDto.DeliveryRiskStatisticsResponse response =
                deliveryRiskService.getDeliveryRisks();

        // then
        assertThat(response.items())
                .anySatisfy(item -> {
                    assertThat(item.orderNo()).isEqualTo(orderNo);
                    assertThat(item.riskType()).isEqualTo("URGENT");
                    assertThat(item.requestedDueDate()).isEqualTo(FixedDashboardClock.TODAY.plusDays(2));
                });
    }

    @Test
    @DisplayName("납기일이 지난 수주는 지연 상태로 조회된다")
    void getDelayedDeliveryRisk() {
        // given
        String orderNo = "PF-20261027-12";

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
        DashboardDto.DeliveryRiskStatisticsResponse response =
                deliveryRiskService.getDeliveryRisks();

        assertThat(response.items())
                .filteredOn(item -> item.orderId().equals(9L))
                .singleElement().satisfies(item -> {
                    assertThat(item.paymentStatus()).isEqualTo(OrderPaymentStatus.UNPAID);
                    assertThat(item.paidAmount()).isZero();
                });
    }

    @Test
    @DisplayName("여러 품목의 이름과 수량을 한 수주로 합친다")
    void joinsProductsOnOneOrder() {
        DashboardDto.DeliveryRiskStatisticsResponse response =
                deliveryRiskService.getDeliveryRisks();

        assertThat(response.items())
                .filteredOn(item -> item.orderId().equals(1L))
                .singleElement().satisfies(item -> {
                    assertThat(item.productName()).isEqualTo(
                            "아이폰 16 맥세이프 클리어 케이스, 아이폰 16 프로 맥세이프 범퍼 케이스, 갤럭시 S25 카드수납 슬라이드 케이스");
                    assertThat(item.quantity()).isEqualTo(6000);
                });
    }

    @Test
    @DisplayName("미배송 수주는 작업지시 유무와 관계없이 포함하고 배송 완료와 취소는 뺀다")
    void includesUndeliveredOrdersOnly() {
        DashboardDto.DeliveryRiskStatisticsResponse response =
                deliveryRiskService.getDeliveryRisks();

        assertThat(response.items())
                .extracting(DashboardDto.DeliveryRiskStatisticsItemResponse::orderNo)
                .contains("PF-20261027-3", "PF-20261108-10")
                .doesNotContain("PF-20260925-7", "PF-20261030-11", "PF-20261030-6");
    }
}
