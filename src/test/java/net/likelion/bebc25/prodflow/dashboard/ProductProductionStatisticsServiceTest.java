package net.likelion.bebc25.prodflow.dashboard;

import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.repository.ProductProductionStatisticsRepository;
import net.likelion.bebc25.prodflow.dashboard.service.ProductProductionStatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ProductProductionStatisticsServiceTest {

    @Autowired
    private ProductProductionStatisticsService productProductionStatisticsService;

    @Autowired
    private ProductProductionStatisticsRepository productProductionStatisticsRepository;

    // 생산 비중 조회 정상 작동 테스트
    @Test
    void getProductProductionShare_success() {
        // given
        YearMonth month = YearMonth.of(2026, 10);

        // when
        DashboardDto.ProductProductionShareResponse response =
                productProductionStatisticsService.getProductProductionShare(month);

        // then
        assertNotNull(response);

        assertEquals(3500L, response.totalCompletedQuantity());
        assertEquals(3, response.items().size());

        assertEquals(3L, response.items().get(0).productId());
        assertEquals("갤럭시 S25 카드수납 슬라이드 케이스", response.items().get(0).productName());
        assertEquals(1500L, response.items().get(0).completedQuantity());
        assertEquals(42.9, response.items().get(0).productionShare(), 0.0001);

        assertEquals(4L, response.items().get(1).productId());
        assertEquals("갤럭시 S25 울트라 소프트 실리콘 케이스", response.items().get(1).productName());
        assertEquals(1000L, response.items().get(1).completedQuantity());
        assertEquals(28.6, response.items().get(1).productionShare(), 0.0001);

        assertEquals(5L, response.items().get(2).productId());
        assertEquals("아이폰 15 사피아노 가죽 케이스", response.items().get(2).productName());
        assertEquals(1000L, response.items().get(2).completedQuantity());
        assertEquals(28.6, response.items().get(2).productionShare(), 0.0001);
    }

    // 생산 완료 데이터가 없는 월 조회
    @Test
    void getProductProductionShare_noCompletedProduction_returnsZero() {
        // given
        YearMonth month = YearMonth.of(2026, 5);

        // when
        DashboardDto.ProductProductionShareResponse response =
                productProductionStatisticsService.getProductProductionShare(month);

        // then
        assertNotNull(response);
        assertEquals(0L, response.totalCompletedQuantity());
        assertTrue(response.items().isEmpty());
    }

    // 품목별 생산 비중 범위 확인
    @Test
    void getProductProductionShare_monthWithOneProduct_success() {
        // given
        YearMonth month = YearMonth.of(2026, 10);

        // when
        DashboardDto.ProductProductionShareResponse response =
                productProductionStatisticsService.getProductProductionShare(month);

        // then
        assertNotNull(response);
        assertTrue(response.totalCompletedQuantity() > 0);
        assertFalse(response.items().isEmpty());

        // 각 품목의 생산 비중은 0% 초과, 100% 이하여야 한다.
        response.items().forEach(item -> {
            assertTrue(item.productionShare() > 0);
            assertTrue(item.productionShare() <= 100);
        });
    }

    // 품목별 수량 합계 확인
    @Test
    void getProductProductionShare_totalQuantityMatchesItemSum() {
        // given
        YearMonth month = YearMonth.of(2026, 10);

        // when
        DashboardDto.ProductProductionShareResponse response =
                productProductionStatisticsService.getProductProductionShare(month);

        // then
        long itemQuantitySum = response.items().stream()
                .mapToLong(DashboardDto.ProductProductionShareItemResponse::completedQuantity)
                .sum();

        assertEquals(response.totalCompletedQuantity(), itemQuantitySum);
    }

    // 항목별 소수점 첫째 자리 반올림에 따른 합계 오차 범위를 확인한다.
    @Test
    void getProductProductionShare_productionSharesSumTo100Percent() {
        // given
        YearMonth month = YearMonth.of(2026, 10);

        // when
        DashboardDto.ProductProductionShareResponse response =
                productProductionStatisticsService.getProductProductionShare(month);

        // then
        double totalShare = response.items().stream()
                .mapToDouble(DashboardDto.ProductProductionShareItemResponse::productionShare)
                .sum();

        // 항목 하나의 반올림 오차는 최대 0.05%p다.
        double roundingTolerance = response.items().size() * 0.05 + 0.0001;
        assertEquals(100.0, totalShare, roundingTolerance);
    }

}
