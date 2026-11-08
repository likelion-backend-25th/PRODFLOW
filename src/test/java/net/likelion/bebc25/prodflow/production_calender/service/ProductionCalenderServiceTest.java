package net.likelion.bebc25.prodflow.production_calender.service;

import net.likelion.bebc25.prodflow.production_calender.dto.ProductionCalenderDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ProductionCalenderServiceTest {

    @Autowired
    private ProductionCalenderService productionCalenderService;

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
}
