package net.likelion.bebc25.prodflow.production_calender.repository;

import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ProductionCalenderRepositoryTest {

    @Autowired
    private ProductionCalenderRepository productionCalenderRepository;

    @Test
    @DisplayName("확정된 수주의 진행 중인 작업지시를 조회한다")
    void findCalenderSchedules() {
        // given
        List<String> statuses = List.of(
                "PREPARE",
                "PRODUCE",
                "PACK",
                "SUSPEND"
        );

        // when
        List<WorkOrder> result =
                productionCalenderRepository.findCalenderSchedules(
                        ConfirmationStatus.CONFIRMED,
                        statuses
                );

        // then
        assertThat(result).isNotNull();

        assertThat(result)
                .allSatisfy(workOrder -> {
                    assertThat(workOrder.getOrderProduct()
                            .getCustomerOrder()
                            .getConfirmationStatus())
                            .isEqualTo(ConfirmationStatus.CONFIRMED);

                    assertThat(workOrder.getCommonCode().getCode())
                            .isIn(statuses);
                });
    }

    @Test
    @DisplayName("존재하지 않는 작업지시 상태로 조회하면 빈 리스트를 반환한다")
    void findCalenderSchedulesWithNoResult() {
        // given
        List<String> statuses = List.of("NOT_EXIST_STATUS");

        // when
        List<WorkOrder> result =
                productionCalenderRepository.findCalenderSchedules(
                        ConfirmationStatus.CONFIRMED,
                        statuses
                );

        // then
        assertThat(result).isEmpty();
    }
}