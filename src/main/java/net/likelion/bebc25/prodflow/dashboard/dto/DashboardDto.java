package net.likelion.bebc25.prodflow.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

public class DashboardDto {

    // 대시보드 통합 통계 조회 조건
    public record Request(
            LocalDate startDate,
            LocalDate endDate
    ) {

    }

    // 대시보드 상단 요약 통계
    public record SummaryResponse(
            long totalOrderCount,   // 전체 수주
            long plannedQuantity,   // 계획 생산량
            long completedQuantity, // 생산 완료량
            double productionProgressRate,  // 생산 진행률 (%)
            double onTimeDeliveryRate,  // 납기 준수율 (%)
            long unpaidAmount   // 미수금? 미입금?
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
