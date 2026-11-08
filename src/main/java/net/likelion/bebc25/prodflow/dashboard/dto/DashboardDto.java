package net.likelion.bebc25.prodflow.dashboard.dto;

import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;

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

    // 날짜별 생산 계획 통계
    public record ProductionResponse(
            LocalDate date,
            long plannedQuantity,
            long completedQuantity
    ) {
    }

    // 월간 생산량 통계
    public record MonthlyProductionResponse(
            int year,
            int month,
            long plannedQuantity,
            long completedQuantity
    ) {
    }

    // 주차별 생산량 통계
    public record WeeklyProductionResponse(
            int week,
            LocalDate startDate,
            LocalDate endDate,
            long plannedQuantity,
            long completedQuantity
    ) {
    }

    // 납기 이행률 통계 응답
    public record DeliveryPerformanceResponse(
            long totalOrderCount,
            long onTimeOrderCount,
            double onTimeDeliveryRate
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

    // 수주 상태별 통계
    public record OrderStatusStatisticsResponse(
            String statusCode,
            String statusName,
            long orderCount
    ) {
    }

    // 품목별 생산 비중 통계 조회 응답
    public record ProductProductionShareResponse(
            long totalCompletedQuantity,
            List<ProductProductionShareItemResponse> items
    ) {
    }

    // 품목별 생산 비중 항목
    public record ProductProductionShareItemResponse(
            Long productId,
            String productName,
            long completedQuantity,
            double productionShare
    ) {
    }

    // 납기 위험 현황 조회 응답
    public record DeliveryRiskStatisticsResponse(
            long totalRiskOrderCount,
            List<DeliveryRiskStatisticsItemResponse> items
    ) {
    }

    // 납기 위험 수주 상세 항목
    public record DeliveryRiskStatisticsItemResponse(
            Long orderId,
            String orderNo,
            String customerName,
            String productName,
            int quantity,
            LocalDate requestedDueDate,
            LocalDate plannedEndDate,
            Long statusCode,
            String riskType,
            String riskReason,
            OrderPaymentStatus paymentStatus,
            long paidAmount
    ) {
    }

    // STAT-01 대시보드 통합 조회 응답
    public record DashboardResponse(
            SummaryResponse summary,
            OrderStatisticsResponse orderStatistics,
            List<OrderStatusStatisticsResponse> orderStatusStatistics,
            List<ProductionResponse> productionStatistics,
            List<MonthlyProductionResponse> monthlyProductionStatistics,
            List<WeeklyProductionResponse> weeklyProductionStatistics,
            DeliveryPerformanceResponse deliveryPerformance,
            List<ProductProductionResponse> productStatistics,
            ProductProductionShareResponse productProductionShare,
            DeliveryRiskStatisticsResponse deliveryRiskStatistics
    ) {
    }
}