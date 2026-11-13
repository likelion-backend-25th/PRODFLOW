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
    private jakarta.persistence.EntityManager em;

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
                .filter(item -> "WO-2026-001".equals(item.workNo()))
                .findFirst()
                .orElseThrow();

        assertThat(schedule.orderNo()).isEqualTo("PF-20261110-1");
        assertThat(schedule.productName()).isEqualTo("아이폰 16 맥세이프 클리어 케이스");
        assertThat(schedule.quantity()).isEqualTo(1000);
        assertThat(schedule.lineId()).isEqualTo(2L);
        assertThat(schedule.lineName()).isEqualTo("B라인");
        assertThat(schedule.status()).isEqualTo("PREPARE");
        assertThat(schedule.plannedStartDate()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(schedule.plannedEndDate()).isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    @DisplayName("한 수주의 여러 품목은 작업지시마다 제품명과 수량이 다르다")
    void getCalenderSchedulesForMultipleProducts() {
        var order = customerOrderRepository.findById(1L).orElseThrow();
        var product = em.find(net.likelion.bebc25.prodflow.product.entity.Product.class, 2L);
        var item = new net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct(order, product, product.getProductName(), 100, product.getCurrentPrice());
        em.persist(item);
        var work = new net.likelion.bebc25.prodflow.work_order.entity.WorkOrder("WO-CALENDAR-MULTI", item,
                em.find(net.likelion.bebc25.prodflow.line.entity.Line.class, 4L),
                em.find(net.likelion.bebc25.prodflow.user.entity.User.class, 5L), 100,
                order.getPlannedStartDate(), order.getPlannedEndDate(), "다품목 조회");
        work.changeStatus(commonCodeRepository.findById(16L).orElseThrow());
        em.persist(work);
        em.flush();
        assertThat(productionCalenderService.getCalenderSchedules())
                .filteredOn(row -> row.orderNo().equals(order.getOrderNo()))
                .extracting(ProductionCalenderDto.Response::workNo, ProductionCalenderDto.Response::productName, ProductionCalenderDto.Response::quantity)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("WO-2026-001", "아이폰 16 맥세이프 클리어 케이스", 1000),
                        org.assertj.core.groups.Tuple.tuple("WO-2026-002", "아이폰 16 맥세이프 클리어 케이스", 300),
                        org.assertj.core.groups.Tuple.tuple("WO-CALENDAR-MULTI", product.getProductName(), 100));
    }

    @Test
    @DisplayName("같은 품목의 작업지시가 여러 개여도 빠지거나 중복되지 않는다")
    void getCalenderSchedulesKeepsEachWorkOrder() {
        // when
        List<ProductionCalenderDto.Response> result =
                productionCalenderService.getCalenderSchedules();

        // then

        assertThat(result)
                .filteredOn(schedule -> "아이폰 16 맥세이프 클리어 케이스".equals(schedule.productName()))
                .extracting(ProductionCalenderDto.Response::workNo)
                .containsExactlyInAnyOrder("WO-2026-001", "WO-2026-002", "WO-2026-011");
    }

    @Test
    @DisplayName("예정 기간이 요청 범위와 겹치는 작업지시만 그 범위에 해당한다")
    void getCalenderSchedulesWithinRequestedDates() {
        LocalDate from = LocalDate.of(2026, 11, 12);
        LocalDate to = LocalDate.of(2026, 11, 13);
        var result = productionCalenderService.getCalenderSchedules();
        assertThat(result).filteredOn(schedule -> !schedule.plannedEndDate().isBefore(from)
                        && !schedule.plannedStartDate().isAfter(to))
                .extracting(ProductionCalenderDto.Response::workNo)
                .containsExactlyInAnyOrder("WO-2026-003", "WO-2026-004", "WO-2026-011");
        assertThat(result).extracting(ProductionCalenderDto.Response::workNo)
                .contains("WO-2026-003", "WO-2026-010")
                .doesNotContain("WO-2026-006");
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
