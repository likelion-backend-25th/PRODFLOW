package net.likelion.bebc25.prodflow.dashboard.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {

    private final OrderStatisticsService orderStatisticsService;
    private final ProductionStatisticsService productionStatisticsService;
    private final DeliveryPerformanceService deliveryPerformanceService;
    private final ProductProductionStatisticsService productProductionStatisticsService;
    private final DeliveryRiskService deliveryRiskService;

    // 대시보드 통합 조회
    public DashboardDto.DashboardResponse getDashboard(DashboardDto.Request request) {

        validateDateRange(request);

        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();

        // 월간 통계 기준
        YearMonth targetMonth = YearMonth.from(endDate);

        // 수주 통계 조회
        DashboardDto.OrderStatisticsResponse orderStatistics =
                orderStatisticsService.getOrderStatistics(
                        startDate,
                        endDate
                );

        // 수주 상태별 통계 조회
        List<DashboardDto.OrderStatusStatisticsResponse> orderStatusStatistics =
                orderStatisticsService.getOrderStatusStatistics();

        // 날짜별 생산 통계 조회
        List<DashboardDto.ProductionResponse> productionStatistics =
                productionStatisticsService.getDailyProduction(
                        startDate,
                        endDate
                );

        // 요청 기간에 포함된 달의 생산 통계 조회
        List<DashboardDto.MonthlyProductionResponse> monthlyProduction =
                productionStatisticsService.getMonthlyProduction(
                        startDate,
                        endDate
                );

        // 주간 생산 통계 조회
        List<DashboardDto.WeeklyProductionResponse> weeklyProduction =
                productionStatisticsService.getWeeklyProduction(
                        targetMonth.getYear(),
                        targetMonth.getMonthValue()
                );

        // 납기 이행률 통계 조회
        DashboardDto.DeliveryPerformanceResponse deliveryPerformance =
                deliveryPerformanceService.getDeliveryPerformance(
                        startDate,
                        endDate
                );

        // 품목별 생산 비중 조회
        DashboardDto.ProductProductionShareResponse productProductionShare =
                productProductionStatisticsService.getProductProductionShare(
                        targetMonth
                );

        // 품목별 생산 비중 응답을 품목별 생산 통계 응답으로 변환
        List<DashboardDto.ProductProductionResponse> productStatistics =
                productProductionShare.items().stream()
                        .map(item -> new DashboardDto.ProductProductionResponse(
                                item.productId(),
                                item.productName(),
                                item.completedQuantity()
                        ))
                        .toList();

        // 납기 위험 수주 조회
        DashboardDto.DeliveryRiskStatisticsResponse deliveryRiskStatistics =
                deliveryRiskService.getDeliveryRisks();

        // 상단 요약 통계 계산
        DashboardDto.SummaryResponse summary =
                createSummary(
                        orderStatistics,
                        productionStatistics,
                        deliveryPerformance
                );

        // 통합 응답 생성
        return new DashboardDto.DashboardResponse(
                summary,
                orderStatistics,
                orderStatusStatistics,
                productionStatistics,
                monthlyProduction,
                weeklyProduction,
                deliveryPerformance,
                productStatistics,
                productProductionShare,
                deliveryRiskStatistics
        );

    }

    // 상단 요약 통계 생성
    private DashboardDto.SummaryResponse createSummary(
            DashboardDto.OrderStatisticsResponse orderStatistics,
            List<DashboardDto.ProductionResponse> productionStatistics,
            DashboardDto.DeliveryPerformanceResponse deliveryPerformance
    ) {
        // 조회 기간 생산 계획량 합계
        long plannedQuantity = productionStatistics.stream()
                .mapToLong(DashboardDto.ProductionResponse::plannedQuantity)
                .sum();

        // 조회 기간 생산 완료량 합계
        long completedQuantity = productionStatistics.stream()
                .mapToLong(DashboardDto.ProductionResponse::completedQuantity)
                .sum();

        // 생산 진행률 계산
        double productionProgressRate = plannedQuantity == 0
                ? 0.0
                : (double) completedQuantity / plannedQuantity * 100;
        productionProgressRate =
                Math.round(productionProgressRate * 100.0) / 100.0;

        // 미입금 금액 조회
        long unpaidAmount = orderStatisticsService.getUnpaidAmount();

        return new DashboardDto.SummaryResponse(
                orderStatistics.totalOrderCount(),
                plannedQuantity,
                completedQuantity,
                productionProgressRate,
                deliveryPerformance.onTimeDeliveryRate(),
                unpaidAmount
        );
    }

    // 조회 기간 유효성 검사
    private void validateDateRange(DashboardDto.Request request) {
        if (request == null || request.startDate() == null || request.endDate() == null) {
            throw new IllegalArgumentException("조회 시작일과 종료일은 필수입니다.");
        }
        if (request.startDate().isAfter(request.endDate())) {
            throw new IllegalArgumentException("조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }


}
