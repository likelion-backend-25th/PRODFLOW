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
    private final ShipmentService shipmentService;

    @Transactional
    public ShipmentTrackingDto refreshByShipmentId(Long shipmentId, Long userId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "운송장을 찾을 수 없습니다: id=" + shipmentId
                ));

        if (shipment.getStartedAt() == null) {
            throw new IllegalArgumentException("계좌 준비와 이메일 전송을 완료한 출고만 배송 조회할 수 있습니다.");
        }

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

        // 조회 결과가 배송완료면 수주도 COMPLETED로 맞춘다.
        String orderStatus = shipmentService.completeOrderIfDelivered(shipment, userId);

        return ShipmentTrackingDto.from(shipment, orderStatus);
    }
}
