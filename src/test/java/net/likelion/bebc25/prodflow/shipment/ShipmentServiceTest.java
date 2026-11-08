package net.likelion.bebc25.prodflow.shipment;

import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.customer_order.repository.OrderProductRepository;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentDto;
import net.likelion.bebc25.prodflow.shipment.entity.Shipment;
import net.likelion.bebc25.prodflow.shipment.repository.ShipmentRepository;
import net.likelion.bebc25.prodflow.shipment.service.ShipmentService;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @Mock
    CustomerOrderRepository customerOrderRepository;
    @Mock
    OrderProductRepository orderProductRepository;
    @Mock
    ShipmentRepository shipmentRepository;
    @Mock
    CommonCodeRepository commonCodeRepository;
    @Mock
    StatusHistoryRepository statusHistoryRepository;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    ShipmentService shipmentService;

    @Test
    @DisplayName("배송중 운송장을 완료하면 수주도 COMPLETED가 된다")
    void completeShipment() {
        User user = mock(User.class);
        when(user.getName()).thenReturn("서도윤");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Shipment shipment = mock(Shipment.class);
        when(shipment.getId()).thenReturn(10L);
        when(shipment.getOrderId()).thenReturn(3L);
        when(shipment.getComplete()).thenReturn(true);
        when(shipment.getDeliveredAt()).thenReturn(LocalDateTime.of(2026, 11, 8, 15, 0));
        when(shipmentRepository.findById(10L)).thenReturn(Optional.of(shipment));

        CommonCode shipping = mock(CommonCode.class);
        when(shipping.getCode()).thenReturn("SHIPPING");
        CommonCode completed = mock(CommonCode.class);
        when(completed.getCode()).thenReturn("COMPLETED");

        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(3L);
        when(order.getOrderNo()).thenReturn("ORD-3");
        // 배송중 검사 → 이력 fromStatus → 응답 orderStatus
        when(order.getStatusCode()).thenReturn(shipping, shipping, completed);
        when(customerOrderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(commonCodeRepository.findByCodeGroup_GroupCodeAndCodeAndStatus(
                "ORDER_STATUS", "COMPLETED", CodeStatus.ACTIVE
        )).thenReturn(Optional.of(completed));

        ShipmentDto.CompleteResponse response = shipmentService.complete(10L, 1L);

        assertThat(response.orderStatus()).isEqualTo("COMPLETED");
        verify(shipment).markDelivered();
        verify(order).changeProgress(completed);
        verify(statusHistoryRepository).save(argThat(history ->
                "SHIPPING".equals(history.getFromStatus())
                        && "COMPLETED".equals(history.getToStatus())
        ));
    }
}
