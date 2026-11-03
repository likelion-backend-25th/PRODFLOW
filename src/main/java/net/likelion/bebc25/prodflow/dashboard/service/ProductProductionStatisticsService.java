package net.likelion.bebc25.prodflow.dashboard.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.repository.ProductProductionStatisticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductProductionStatisticsService {

    private final ProductProductionStatisticsRepository productProductionStatisticsRepository;

    // 선택한 월의 품목별 생산 비중 조회
    public DashboardDto.ProductProductionShareResponse getProductProductionShare(YearMonth month) {
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.plusMonths(1).atDay(1);

        // 품목별 생산 완료 수량 조회
        List<DashboardDto.ProductProductionResponse> productStatisticsList =
                productProductionStatisticsRepository.findProductProductionQuantities(startDate, endDate);

        // 전체 생산완료 수량 조회
        long totalCompletedQuantity =
                productProductionStatisticsRepository.sumCompletedProductionQuantity(startDate, endDate);

        // 품목별 생산 비중 계산
        List<DashboardDto.ProductProductionShareItemResponse> productStatistics =
                productStatisticsList.stream()
                        .map(statistics -> {
                            double productionShare = totalCompletedQuantity == 0
                                    ? 0.0
                                    : statistics.completedQuantity() * 100.0 / totalCompletedQuantity;
                            productionShare = Math.round(productionShare * 10) / 10.0;

                            return new DashboardDto.ProductProductionShareItemResponse(
                                    statistics.productId(),
                                    statistics.productName(),
                                    statistics.completedQuantity(),
                                    productionShare
                            );
                        })
                        .toList();

        return new DashboardDto.ProductProductionShareResponse(
                totalCompletedQuantity,
                productStatistics
        );
    }

}
