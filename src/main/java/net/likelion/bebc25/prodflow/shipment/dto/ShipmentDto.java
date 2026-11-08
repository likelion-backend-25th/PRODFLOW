package net.likelion.bebc25.prodflow.shipment.dto;

import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ShipmentDto {

    private ShipmentDto() {
    }

    // 배송 관리 목록의 한 줄
    public record ListItem(
            Long orderId,
            String orderNo,
            String clientName,
            String productSummary,
            int totalQuantity,
            String shippingAddress,
            LocalDate estimatedDueDate,
            String orderStatus,
            OrderPaymentStatus paymentStatus,
            Long totalAmount,
            Long shipmentId,
            String carrierCode,
            String carrierName,
            String trackingNumber,
            LocalDateTime startedAt,
            LocalDateTime deliveredAt
    ) {
    }

    // 상단 탭 건수
    public record TabCounts(
            long packing,
            long readyToShip,
            long shipping,
            long balance
    ) {
    }

    // 배송 목록 응답
    public record ListResponse(
            String tab,
            TabCounts counts,
            List<ListItem> items
    ) {
    }

    // 배송 완료 처리 응답
    public record CompleteResponse(
            Long shipmentId,
            Long orderId,
            String orderNo,
            String orderStatus,
            Boolean complete,
            LocalDateTime deliveredAt
    ) {
    }
}
