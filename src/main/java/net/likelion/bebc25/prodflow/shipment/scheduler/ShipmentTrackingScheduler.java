package net.likelion.bebc25.prodflow.shipment.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.likelion.bebc25.prodflow.shipment.entity.Shipment;
import net.likelion.bebc25.prodflow.shipment.repository.ShipmentRepository;
import net.likelion.bebc25.prodflow.shipment.service.ShipmentTrackingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "shipment.tracking",
        name = "refresh-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ShipmentTrackingScheduler {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingService shipmentTrackingService;

    // 주기 조회로 수주를 완료할 때 상태 이력에 남길 직원
    @Value("${shipment.tracking.system-user-id:1}")
    private Long systemUserId;

    // 이전 실행이 끝난 뒤 interval만큼 쉬고 다시 돈다. 기본 10분.
    @Scheduled(fixedDelayString = "${shipment.tracking.refresh-interval-ms:600000}")
    public void refreshIncompleteShipments() {
        List<Shipment> shipments = shipmentRepository.findIncompleteShipments();
        log.info("미완료 운송장 주기 조회 시작. count={}", shipments.size());

        for (Shipment shipment : shipments) {
            try {
                shipmentTrackingService.refreshByShipmentId(shipment.getId(), systemUserId);
            } catch (RuntimeException ex) {
                // 한 건 실패해도 다음 운송장은 계속 조회한다.
                log.error("운송장 주기 조회 실패. shipmentId={}", shipment.getId(), ex);
            }
        }
    }
}
