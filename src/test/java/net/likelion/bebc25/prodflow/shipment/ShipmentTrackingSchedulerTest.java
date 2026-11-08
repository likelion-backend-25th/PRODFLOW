package net.likelion.bebc25.prodflow.shipment;

import net.likelion.bebc25.prodflow.shipment.entity.Shipment;
import net.likelion.bebc25.prodflow.shipment.repository.ShipmentRepository;
import net.likelion.bebc25.prodflow.shipment.scheduler.ShipmentTrackingScheduler;
import net.likelion.bebc25.prodflow.shipment.service.ShipmentTrackingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentTrackingSchedulerTest {

    @Mock
    ShipmentRepository shipmentRepository;
    @Mock
    ShipmentTrackingService shipmentTrackingService;
    @InjectMocks
    ShipmentTrackingScheduler scheduler;

    @Test
    @DisplayName("미완료 운송장을 찾아 하나씩 배송조회한다")
    void refreshIncompleteShipments() {
        ReflectionTestUtils.setField(scheduler, "systemUserId", 1L);

        Shipment first = mock(Shipment.class);
        Shipment second = mock(Shipment.class);
        when(first.getId()).thenReturn(10L);
        when(second.getId()).thenReturn(11L);
        when(shipmentRepository.findIncompleteShipments()).thenReturn(List.of(first, second));

        scheduler.refreshIncompleteShipments();

        verify(shipmentTrackingService).refreshByShipmentId(10L, 1L);
        verify(shipmentTrackingService).refreshByShipmentId(11L, 1L);
    }
}
