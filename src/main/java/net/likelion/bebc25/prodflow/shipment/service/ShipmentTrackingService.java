package net.likelion.bebc25.prodflow.shipment.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.shipment.client.ShipmentTrackingClient;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentTrackingDto;
import net.likelion.bebc25.prodflow.shipment.dto.TrackingApiDto;
import net.likelion.bebc25.prodflow.shipment.entity.Shipment;
import net.likelion.bebc25.prodflow.shipment.repository.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ShipmentTrackingService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingClient shipmentTrackingClient;

    @Transactional
    public ShipmentTrackingDto refreshByShipmentId(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "운송장을 찾을 수 없습니다: id=" + shipmentId
                ));

        TrackingApiDto api = shipmentTrackingClient.fetchTrackingInfo(
                shipment.getCarrier().getCode(),
                shipment.getTrackingNumber()
        );

        shipment.applyTracking(
                api.level(),
                api.result(),
                api.complete(),
                api.estimate()
        );

        return ShipmentTrackingDto.from(shipment);
    }
}
