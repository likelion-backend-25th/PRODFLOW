package net.likelion.bebc25.prodflow.shipment.dto;

import net.likelion.bebc25.prodflow.shipment.entity.Shipment;

import java.time.LocalDateTime;


public record ShipmentTrackingDto(
        Long shipmentId,
        Long orderId,
        String carrierCode,
        String carrierName,
        String trackingNumber,
        Integer level,
        String result,
        Boolean complete,
        String estimate,
        LocalDateTime startedAt,
        LocalDateTime deliveredAt
) {
    public static ShipmentTrackingDto from(Shipment shipment) {
        return new ShipmentTrackingDto(
                shipment.getId(),
                shipment.getOrderId(),
                shipment.getCarrier().getCode(),
                shipment.getCarrier().getName(),
                shipment.getTrackingNumber(),
                shipment.getLevel(),
                shipment.getResult(),
                shipment.getComplete(),
                shipment.getEstimate(),
                shipment.getStartedAt(),
                shipment.getDeliveredAt()
        );
    }
}
