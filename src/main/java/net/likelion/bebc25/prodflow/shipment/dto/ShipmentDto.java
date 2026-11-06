package net.likelion.bebc25.prodflow.shipment.dto;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.shipment.entity.Shipment;

import java.time.LocalDateTime;

public class ShipmentDto {

    public record CreateRequest(
            Long orderId,
            String carrierCode,
            String trackingNumber
    ) {
    }

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
