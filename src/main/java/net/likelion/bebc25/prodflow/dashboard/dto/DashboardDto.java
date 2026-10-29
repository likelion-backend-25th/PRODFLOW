package net.likelion.bebc25.prodflow.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

public class DashboardDto {

    // 대시보드 통계 조회 조건
    public record Request(
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    // 대시보드 상단 요약 통계
    public record SummaryResponse(
            long totalOrderCount,
            long plannedQuantity,
            long completedQuantity,
            double productionProgressRate,
            double onTimeDeliveryRate,
            long unpaidAmount
    ) {
    }

    // 수주 건수 통계 응답
    public record OrderStatisticsResponse(
            long totalOrderCount,
            long periodOrderCount
    ) {
    }

    // 수주 상태별 통계
    public record OrderStatusResponse(
            Long statusCode,
            long orderCount
    ) {
    }

    // 날짜별 생산 계획 통계
    public record ProductionResponse(
            LocalDate date,
            long plannedQuantity,
            long completedQuantity
    ) {
    }

    // 제품별 생산 통계
    public record ProductProductionResponse(
            Long productId,
            String productName,
            long completedQuantity
    ) {
    }

    // 납기 위험 수주 정보
    public record DeliveryRiskResponse(
            Long orderId,
            String orderNo,
            LocalDate requestedDueDate,
            LocalDate plannedEndDate,
            Long statusCode
    ) {
    }

    // 대시보드 전체 응답
    public record DashboardResponse(
            SummaryResponse summary,
            List<OrderStatusResponse> orderStatuses,
            List<ProductionResponse> productionStatistics,
            List<ProductProductionResponse> productStatistics,
            List<DeliveryRiskResponse> deliveryRisks
    ) {
    }
}
