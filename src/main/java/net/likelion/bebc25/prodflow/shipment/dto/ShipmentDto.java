package net.likelion.bebc25.prodflow.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.shipment.entity.Shipment;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ShipmentDto {

    private ShipmentDto() {
    }

    // 배송 관리 목록의 한 줄
    @Schema(name = "ShipmentListItem")
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
    @Schema(name = "ShipmentTabCounts")
    public record TabCounts(
            long packing,
            long readyToShip,
            long shipping,
            long balance
    ) {
    }

    // 배송 목록 응답. 탭 건수는 전체 기준, page는 현재 탭 목록 페이징
    @Schema(name = "ShipmentListResponse")
    public record ListResponse(
            String tab,
            TabCounts counts,
            PageResponse<ListItem> page
    ) {
    }

    // 배송 완료 처리 응답
    @Schema(name = "ShipmentCompleteResponse")
    public record CompleteResponse(
            Long shipmentId,
            Long orderId,
            String orderNo,
            String orderStatus,
            Boolean complete,
            LocalDateTime deliveredAt
    ) {
    }

    // 수주가 존재하지만 배송은 아직 등록되지 않은 경우도 구분한다.
    @Schema(name = "ShipmentOrderResponse")
    public record OrderResponse(ShipmentTrackingDto shipment) {
        public static OrderResponse from(Shipment shipment, String orderStatus) {
            return new OrderResponse(shipment == null ? null : ShipmentTrackingDto.from(shipment, orderStatus));
        }
    }

    @Schema(name = "ShipmentCreateRequest")
    public record CreateRequest(
            Long orderId,
            String carrierCode,
            String trackingNumber
    ) {
    }

    @Schema(name = "ShipmentResponse")
    public record Response(
            Long id,
            Long orderId,
            String orderNo,
            String orderStatus,
            String carrierCode,
            String carrierName,
            String trackingNumber,
            LocalDateTime startedAt
    ) {
        public static Response from(Shipment shipment, CustomerOrder order) {
            return new Response(
                    shipment.getId(),
                    order.getId(),
                    order.getOrderNo(),
                    order.getStatusCode().getCode(),
                    shipment.getCarrier().getCode(),
                    shipment.getCarrier().getName(),
                    shipment.getTrackingNumber(),
                    shipment.getStartedAt()
            );
        }
    }
}
