package net.likelion.bebc25.prodflow.shipment.controller;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.shipment.client.ShipmentTrackingClient;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentTrackingDto;
import net.likelion.bebc25.prodflow.shipment.dto.TrackingApiDto;
import net.likelion.bebc25.prodflow.shipment.service.ShipmentTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
public class ShipmentTrackingController {

    private final ShipmentTrackingClient shipmentTrackingClient;
    private final ShipmentTrackingService shipmentTrackingService;

    @GetMapping("/tracking")
    public ResponseEntity<TrackingApiDto> trackingDirect(
            @RequestParam String carrierCode,
            @RequestParam String trackingNumber
    ) {
        return ResponseEntity.ok(
                shipmentTrackingClient.fetchTrackingInfo(carrierCode, trackingNumber)
        );
    }

    @PostMapping("/{id}/tracking/refresh")
    public ResponseEntity<ShipmentTrackingDto> refreshTracking(@PathVariable Long id) {
        return ResponseEntity.ok(shipmentTrackingService.refreshByShipmentId(id));
    }
}
