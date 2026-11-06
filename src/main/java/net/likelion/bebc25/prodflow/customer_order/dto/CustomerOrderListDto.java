package net.likelion.bebc25.prodflow.customer_order.dto;

import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListSortType;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListTab;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class CustomerOrderListDto {
    // 수주 목록 검색 조건
    public record SearchCondition(
            OrderListTab tab,
            String keyword,
            ConfirmationStatus confirmationStatus,
            Long statusCodeId,
            OrderPaymentStatus paymentStatus,
            OrderListSortType sortType) {
    }

    // 그룹 ID 2의 수주 진행 상태 응답
    public record StatusResponse(Long id, String code, String name, int sortOrder) {
    }

    // 수주 품목 응답
    public record ItemResponse(
            Long id,
            Long productId,
            String productName,
            int quantity
    ) {
    }

    // 납기 지연·주의 응답
    public record DelayResponse(String level, String reason) {
    }

    // 수주 목록 응답
    public record Response(
            Long id,
            String orderNo,
            Long clientId,
            String clientCompany,
            List<ItemResponse> items,
            int itemCount,
            long totalQuantity,
            long totalAmount,
            LocalDate dueDate,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate depositDueDate,
            ConfirmationStatus confirmationStatus,
            Long statusCodeId,
            String status,
            String statusName,
            OrderPaymentStatus paymentStatus,
            String carrierName,
            String trackingNumber,
            LocalDateTime completedAt,
            LocalDateTime createdAt,
            DelayResponse delay
    ) {
    }

    // 수주 전체 집계 응답
    public record SummaryResponse(
            long waiting,
            long making,
            long shipping,
            long doneThisMonth,
            long openCount,
            long doneCount
    ) {
    }
}
