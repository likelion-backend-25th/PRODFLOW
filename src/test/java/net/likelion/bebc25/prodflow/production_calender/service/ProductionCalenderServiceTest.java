package net.likelion.bebc25.prodflow.production_calender.service;

import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.production_calender.dto.ProductionCalenderDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ProductionCalenderServiceTest {

    @Autowired
    private ProductionCalenderService productionCalenderService;

    @Autowired
    private CustomerOrderRepository customerOrderRepository;

    @Autowired
    private CommonCodeRepository commonCodeRepository;

    @Test
    @DisplayName("생산 캘린더를 조회하면 작업지시 목록을 반환한다")
    void getCalenderSchedules() {

        // when
        List<ProductionCalenderDto.Response> result =
                productionCalenderService.getCalenderSchedules();

        // then
        assertThat(result).isNotNull();

        assertThat(result)
                .allSatisfy(schedule -> {
                    assertThat(schedule.lineId()).isNotNull();
                    assertThat(schedule.lineName()).isNotBlank();
                    assertThat(schedule.workOrderId()).isNotNull();
                    assertThat(schedule.workNo()).isNotBlank();
                    assertThat(schedule.plannedStartDate()).isNotNull();
                    assertThat(schedule.plannedEndDate()).isNotNull();
                    assertThat(schedule.status())
                            .isIn("PREPARE", "PRODUCE", "PACK", "SUSPEND");
                });
    }

    @Test
    @DisplayName("생산 캘린더에는 지정된 작업지시 상태만 포함된다")
    void getCalenderSchedulesWithValidStatus() {
        // when
        List<ProductionCalenderDto.Response> result =
                productionCalenderService.getCalenderSchedules();

        // then
        assertThat(result)
                .extracting(ProductionCalenderDto.Response::status)
                .allSatisfy(status ->
                        assertThat(status)
                                .isIn("PREPARE", "PRODUCE", "PACK", "SUSPEND")
                );
    }

    @Test
    @DisplayName("생산 캘린더의 종료 예정일은 시작 예정일보다 빠를 수 없다")
    void getCalenderSchedulesWithValidDateRange() {
        // when
        List<ProductionCalenderDto.Response> result =
                productionCalenderService.getCalenderSchedules();

        // then
        assertThat(result)
                .allSatisfy(schedule ->
                        assertThat(schedule.plannedEndDate())
                                .isAfterOrEqualTo(schedule.plannedStartDate())
                );
    }

    @Test
    @DisplayName("생산 캘린더는 생산라인과 시작 예정일 순서로 정렬된다")
    void getCalenderSchedulesWithSortedOrder() {
        // when
        List<ProductionCalenderDto.Response> result =
                productionCalenderService.getCalenderSchedules();

        // then
        List<ProductionCalenderDto.Response> sortedResult = result.stream()
                .sorted(
                        java.util.Comparator
                                .comparing(ProductionCalenderDto.Response::lineId)
                                .thenComparing(
                                        ProductionCalenderDto.Response::plannedStartDate
                                )
                )
                .toList();

        assertThat(result).containsExactlyElementsOf(sortedResult);
    }

    @Test
    @DisplayName("생산 캘린더에는 동일한 작업지시가 중복으로 조회되지 않는다")
    void getCalenderSchedulesWithoutDuplicateWorkOrders() {
        // when
        List<ProductionCalenderDto.Response> result =
                productionCalenderService.getCalenderSchedules();

        // then
        assertThat(result)
                .extracting(ProductionCalenderDto.Response::workOrderId)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("생산 캘린더는 수주번호와 계약 당시 제품명, 배정 수량을 반환한다")
    void getCalenderSchedulesWithOrderProduct() {
        // when
        List<ProductionCalenderDto.Response> result =
                productionCalenderService.getCalenderSchedules();

        // then
        ProductionCalenderDto.Response schedule = result.stream()
                .filter(item -> "WI-2026-001".equals(item.workNo()))
                .findFirst()
                .orElseThrow();

        assertThat(schedule.orderNo()).isEqualTo("PF-2026-001");
        assertThat(schedule.productName()).isEqualTo("디자인 A 투명 케이스");
        assertThat(schedule.quantity()).isEqualTo(2000);
        assertThat(schedule.lineId()).isEqualTo(1L);
        assertThat(schedule.lineName()).isEqualTo("A라인");
        assertThat(schedule.status()).isEqualTo("PREPARE");
        assertThat(schedule.plannedStartDate()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(schedule.plannedEndDate()).isEqualTo(LocalDate.of(2026, 10, 28));
    }

    @Test
    @DisplayName("한 수주의 여러 품목은 작업지시마다 제품명과 수량이 다르다")
    void getCalenderSchedulesForMultipleProducts() {
        // when
        List<ProductionCalenderDto.Response> result =
                productionCalenderService.getCalenderSchedules();

        // then

        assertThat(result)
                .filteredOn(schedule -> "PF-2026-001".equals(schedule.orderNo()))
                .extracting(
                        ProductionCalenderDto.Response::workNo,
                        ProductionCalenderDto.Response::productName,
                        ProductionCalenderDto.Response::quantity
                )
                .contains(
                        org.assertj.core.groups.Tuple.tuple(
                                "WI-2026-001", "디자인 A 투명 케이스", 2000
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "WI-2026-002", "디자인 A 투명 케이스", 1000
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "WI-2026-003", "디자인 B 맥세이프 케이스", 1000
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "WI-2026-005", "디자인 C 카드수납 케이스", 1000
                        )
                );
    }

    @Test
    @DisplayName("같은 품목의 작업지시가 여러 개여도 빠지거나 중복되지 않는다")
    void getCalenderSchedulesKeepsEachWorkOrder() {
        // when
        List<ProductionCalenderDto.Response> result =
                productionCalenderService.getCalenderSchedules();

        // then

        assertThat(result)
                .filteredOn(schedule -> "디자인 A 투명 케이스".equals(schedule.productName()))
                .extracting(ProductionCalenderDto.Response::workNo)
                .containsExactlyInAnyOrder("WI-2026-001", "WI-2026-002");
    }

    @Test
    @DisplayName("예정 기간이 요청 범위와 겹치는 작업지시만 그 범위에 해당한다")
    void getCalenderSchedulesWithinRequestedDates() {
        LocalDate from = LocalDate.of(2026, 10, 27);
        LocalDate to = LocalDate.of(2026, 10, 28);

        // when
        List<ProductionCalenderDto.Response> result =
                productionCalenderService.getCalenderSchedules();

        // then
        assertThat(result)
                .filteredOn(schedule ->
                        !schedule.plannedEndDate().isBefore(from)
                                && !schedule.plannedStartDate().isAfter(to)
                )
                .extracting(ProductionCalenderDto.Response::workNo)
                .containsExactlyInAnyOrder(
                        "WI-2026-001",
                        "WI-2026-002",
                        "WI-2026-003",
                        "WI-2026-004",
                        "WI-2026-005"
                );

        assertThat(result)
                .extracting(ProductionCalenderDto.Response::workNo)
                .contains("WI-2026-006", "WI-2026-014")
                .doesNotContain("WI-2026-008");
    }

    @Test
    @DisplayName("확정된 진행 중 작업지시가 없으면 빈 목록을 반환한다")
    void getCalenderSchedulesWhenEmpty() {
        // given
        CommonCode waiting = commonCodeRepository.findById(6L).orElseThrow();
        for (CustomerOrder order : customerOrderRepository.findAll()) {
            order.changeProgress(waiting);
        }

        // when
        List<ProductionCalenderDto.Response> result =
                productionCalenderService.getCalenderSchedules();

        // then
        assertThat(result).isEmpty();
    }
}
