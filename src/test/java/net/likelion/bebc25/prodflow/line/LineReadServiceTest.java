package net.likelion.bebc25.prodflow.line;

import net.likelion.bebc25.prodflow.line.dto.LineDto.DetailResponse;
import net.likelion.bebc25.prodflow.line.dto.LineDto.DetailWorkOrder;
import net.likelion.bebc25.prodflow.line.dto.LineDto.LineList;
import net.likelion.bebc25.prodflow.line.dto.LineDto.ListResponse;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.line.serivce.LineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// 애플리케이션 설정은 유지한다. 이 테스트만 테이블을 지우지 않고 data.sql도 다시 실행하지 않는다.
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=never"
})
@Transactional
class LineReadServiceTest {

    @Autowired
    LineService lineService;

    @Test
    @DisplayName("목록은 생산 중인 작업 한 건을 붙이고 생산 중이 아닌 라인은 비워 둔다")
    void listShowsCurrentProduceAndKeepsIdleLines() {
        ListResponse response = lineService.findAll();

        assertThat(response.totalCount()).isEqualTo(5);
        assertThat(response.runningCount()).isEqualTo(2);
        assertThat(response.availableCount()).isEqualTo(1);
        assertThat(response.inspectionOrBrokenCount()).isEqualTo(2);
        assertThat(response.lines()).extracting(LineList::lineId).containsExactly(1L, 2L, 3L, 4L, 5L);

        LineList producing = lineOf(response, "A라인");
        assertThat(producing.status()).isEqualTo(LineStatus.OPERATING);
        assertThat(producing.productionCapacity()).isEqualTo(1000);
        assertThat(producing.workNo()).isEqualTo("WI-2026-006");
        assertThat(producing.productName()).isEqualTo("디자인 D 실리콘 케이스");
        assertThat(producing.orderNo()).isEqualTo("PF-2026-002");
        assertThat(producing.managerName()).isEqualTo("김태호");

        LineList idle = lineOf(response, "C라인");
        assertThat(idle.status()).isEqualTo(LineStatus.AVAILABLE);
        assertThat(idle.workNo()).isNull();
        assertThat(idle.productName()).isNull();
        assertThat(idle.orderNo()).isNull();
        assertThat(idle.managerName()).isNull();
    }

    @Test
    @DisplayName("상세의 현재 작업은 생산 중 한 건이고 작업 목록은 현재와 예정만 둔다")
    void detailKeepsCurrentAndScheduledWorks() {
        DetailResponse detail = lineService.findByLineId(1L);

        assertThat(detail.lineName()).isEqualTo("A라인");
        assertThat(detail.status()).isEqualTo(LineStatus.OPERATING);
        assertThat(detail.productionCapacity()).isEqualTo(1000);
        assertThat(detail.workNo()).isEqualTo("WI-2026-006");
        assertThat(detail.productName()).isEqualTo("디자인 D 실리콘 케이스");
        assertThat(detail.orderNo()).isEqualTo("PF-2026-002");
        assertThat(detail.managerName()).isEqualTo("김태호");
        assertThat(detail.workOrderCount()).isEqualTo(3);
        assertThat(detail.waitingCount()).isEqualTo(2);
        assertThat(detail.workOrders()).extracting(DetailWorkOrder::workNo)
                .containsExactly("WI-2026-006", "WI-2026-001", "WI-2026-015");
        assertThat(detail.workOrders()).extracting(DetailWorkOrder::status)
                .containsExactly("PRODUCE", "PREPARE", "PREPARE");
        assertThat(detail.capacityLogs()).singleElement().satisfies(log -> {
            assertThat(log.beforeCapacity()).isEqualTo(800);
            assertThat(log.afterCapacity()).isEqualTo(1000);
            assertThat(log.reason()).isEqualTo("설비 증설로 일일 생산 능력을 800개에서 1,000개로 조정");
            assertThat(log.managerName()).isEqualTo("김태호");
        });
    }

    private LineList lineOf(ListResponse response, String lineName) {
        return response.lines().stream()
                .filter(line -> lineName.equals(line.lineName()))
                .findFirst()
                .orElseThrow();
    }
}
