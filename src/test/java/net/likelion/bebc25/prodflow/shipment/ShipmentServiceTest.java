package net.likelion.bebc25.prodflow.shipment;

import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.customer_order.repository.OrderProductRepository;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentDto;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentDto.CreateRequest;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentDto.Response;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentDto.OrderResponse;
import net.likelion.bebc25.prodflow.shipment.entity.Carrier;
import net.likelion.bebc25.prodflow.shipment.entity.Shipment;
import net.likelion.bebc25.prodflow.shipment.repository.CarrierRepository;
import net.likelion.bebc25.prodflow.shipment.repository.ShipmentRepository;
import net.likelion.bebc25.prodflow.shipment.service.ShipmentService;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.NoSuchElementException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentServiceTest {

    @Mock
    CustomerOrderRepository customerOrderRepository;
    @Mock
    OrderProductRepository orderProductRepository;
    @Mock
    ShipmentRepository shipmentRepository;
    @Mock
    CarrierRepository carrierRepository;
    @Mock
    CommonCodeRepository commonCodeRepository;
    @Mock
    StatusHistoryRepository statusHistoryRepository;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    ShipmentService shipmentService;

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        when(user.getName()).thenReturn("서도윤");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("배송중 운송장을 완료하면 수주도 COMPLETED가 된다")
    void completeShipment() {
        Shipment shipment = mock(Shipment.class);
        when(shipment.getId()).thenReturn(10L);
        when(shipment.getOrderId()).thenReturn(3L);
        when(shipment.getComplete()).thenReturn(true);
        when(shipment.getDeliveredAt()).thenReturn(LocalDateTime.of(2026, 11, 8, 15, 0));
        when(shipmentRepository.findById(10L)).thenReturn(Optional.of(shipment));

        CommonCode shipping = code("SHIPPING");
        CommonCode completed = code("COMPLETED");

        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(3L);
        when(order.getOrderNo()).thenReturn("ORD-3");
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

    @Test
    @DisplayName("출고대기 수주에 운송장을 등록하면 배송중이 되고 하이픈은 빠진다")
    void registerShipment() {
        CommonCode ready = code("READY_TO_SHIP");
        CommonCode shipping = code("SHIPPING");
        CustomerOrder order = order(3L, "READY_TO_SHIP");
        Carrier carrier = carrier();
        when(order.getStatusCode()).thenReturn(ready, shipping);
        when(carrierRepository.findById("04")).thenReturn(Optional.of(carrier));
        when(commonCodeRepository.findByCodeGroup_GroupCodeAndCodeAndStatus(
                "ORDER_STATUS", "SHIPPING", CodeStatus.ACTIVE)).thenReturn(Optional.of(shipping));
        when(shipmentRepository.save(any())).thenAnswer(invocation -> {
            Shipment shipment = invocation.getArgument(0);
            ReflectionTestUtils.setField(shipment, "id", 10L);
            return shipment;
        });

        Response response = shipmentService.register(new CreateRequest(3L, "04", "5300-9999-0001"), 1L);

        assertThat(response.orderStatus()).isEqualTo("SHIPPING");
        assertThat(response.trackingNumber()).isEqualTo("530099990001");
        assertThat(response.startedAt()).isNotNull();
        verify(order).changeProgress(shipping);
        verify(statusHistoryRepository).save(argThat(history ->
                history.getEntity() == HistoryEntityType.ORDER
                        && "READY_TO_SHIP".equals(history.getFromStatus())
                        && "SHIPPING".equals(history.getToStatus())));
    }

    @Test
    @DisplayName("출고대기가 아닌 수주는 운송장을 등록할 수 없다")
    void rejectWhenNotReadyToShip() {
        order(1L, "PACKING");

        assertThatThrownBy(() -> shipmentService.register(new CreateRequest(1L, "04", "530099990002"), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("출고대기 수주만 운송장을 등록할 수 있습니다.");
    }

    @Test
    @DisplayName("이미 운송장이 있는 수주는 다시 등록할 수 없다")
    void rejectWhenShipmentExists() {
        order(5L, "READY_TO_SHIP");
        when(shipmentRepository.existsByOrderId(5L)).thenReturn(true);

        assertThatThrownBy(() -> shipmentService.register(new CreateRequest(5L, "05", "450011122233"), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 운송장이 등록된 수주입니다.");
    }

    @Test
    @DisplayName("같은 택배사의 운송장 번호는 다른 수주에도 쓸 수 없다")
    void rejectDuplicateTrackingNumber() {
        order(3L, "READY_TO_SHIP");
        Carrier carrier = carrier();
        when(carrierRepository.findById("04")).thenReturn(Optional.of(carrier));
        when(shipmentRepository.existsByCarrier_CodeAndTrackingNumber("04", "530012345671")).thenReturn(true);

        assertThatThrownBy(() -> shipmentService.register(new CreateRequest(3L, "04", "530012345671"), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("같은 택배사에 이미 등록된 운송장 번호입니다.");
    }

    @Test
    @DisplayName("선택한 수주에 저장된 배송 정보만 조회하고 상태를 변경하지 않는다")
    void getByOrderId() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 11, 6, 10, 0);
        Shipment shipment = Shipment.start(3L, carrier(), "530099990001", startedAt);
        shipment.applyTracking(3, "Y", false, "2026-11-09");
        order(3L, "SHIPPING");
        when(shipmentRepository.findByOrderId(3L)).thenReturn(Optional.of(shipment));

        OrderResponse response = shipmentService.getByOrderId(3L);

        assertThat(response.shipment().orderId()).isEqualTo(3L);
        assertThat(response.shipment().orderStatus()).isEqualTo("SHIPPING");
        assertThat(response.shipment().carrierCode()).isEqualTo("04");
        assertThat(response.shipment().carrierName()).isEqualTo("CJ대한통운");
        assertThat(response.shipment().trackingNumber()).isEqualTo("530099990001");
        assertThat(response.shipment().level()).isEqualTo(3);
        assertThat(response.shipment().result()).isEqualTo("Y");
        assertThat(response.shipment().complete()).isFalse();
        assertThat(response.shipment().estimate()).isEqualTo("2026-11-09");
        assertThat(response.shipment().startedAt()).isEqualTo(startedAt);
        assertThat(response.shipment().deliveredAt()).isNull();
        verify(customerOrderRepository).findById(3L);
        verify(shipmentRepository).findByOrderId(3L);
        verifyNoMoreInteractions(customerOrderRepository, shipmentRepository);
        verifyNoInteractions(carrierRepository, commonCodeRepository, statusHistoryRepository);
    }

    @Test
    @DisplayName("배송 완료 수주의 저장된 완료일과 완료 여부를 조회한다")
    void getCompletedShipment() {
        Shipment shipment = Shipment.start(7L, carrier(), "530099990007", LocalDateTime.of(2026, 11, 5, 10, 0));
        shipment.applyTracking(6, "Y", true, null);
        LocalDateTime deliveredAt = shipment.getDeliveredAt();
        order(7L, "COMPLETED");
        when(shipmentRepository.findByOrderId(7L)).thenReturn(Optional.of(shipment));

        OrderResponse response = shipmentService.getByOrderId(7L);

        assertThat(response.shipment().orderStatus()).isEqualTo("COMPLETED");
        assertThat(response.shipment().level()).isEqualTo(6);
        assertThat(response.shipment().complete()).isTrue();
        assertThat(response.shipment().deliveredAt()).isEqualTo(deliveredAt);
    }

    @Test
    @DisplayName("운송장만 등록되고 추적 전인 배송도 null 필드를 그대로 반환한다")
    void getShipmentBeforeTracking() {
        Shipment shipment = Shipment.start(3L, carrier(), "530099990003", LocalDateTime.of(2026, 11, 6, 10, 0));
        order(3L, "SHIPPING");
        when(shipmentRepository.findByOrderId(3L)).thenReturn(Optional.of(shipment));

        OrderResponse response = shipmentService.getByOrderId(3L);

        assertThat(response.shipment().level()).isNull();
        assertThat(response.shipment().estimate()).isNull();
        assertThat(response.shipment().complete()).isFalse();
    }

    @Test
    @DisplayName("배송이 미등록된 수주는 배송 정보를 null로 반환한다")
    void getOrderWithoutShipment() {
        order(10L, "CONFIRMED");
        when(shipmentRepository.findByOrderId(10L)).thenReturn(Optional.empty());

        OrderResponse response = shipmentService.getByOrderId(10L);

        assertThat(response.shipment()).isNull();
        verify(shipmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 수주는 배송 조회 전에 실패한다")
    void rejectUnknownOrder() {
        assertThatThrownBy(() -> shipmentService.getByOrderId(Long.MAX_VALUE))
                .isInstanceOf(NoSuchElementException.class).hasMessage("수주를 찾을 수 없습니다.");
        verifyNoInteractions(shipmentRepository);
    }

    @Test
    @DisplayName("수주 ID가 없으면 DB 조회 전에 거부한다")
    void rejectMissingOrderId() {
        assertThatThrownBy(() -> shipmentService.getByOrderId(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("수주 ID를 입력해 주세요.");
        verifyNoInteractions(customerOrderRepository, shipmentRepository);
    }

    private CustomerOrder order(Long id, String status) {
        CommonCode statusCode = code(status);
        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(id);
        when(order.getOrderNo()).thenReturn("PF-2026-003");
        when(order.getStatusCode()).thenReturn(statusCode);
        when(customerOrderRepository.findById(id)).thenReturn(Optional.of(order));
        return order;
    }

    private Carrier carrier() {
        Carrier carrier = mock(Carrier.class);
        when(carrier.getCode()).thenReturn("04");
        when(carrier.getName()).thenReturn("CJ대한통운");
        return carrier;
    }

    private CommonCode code(String value) {
        CommonCode code = mock(CommonCode.class);
        when(code.getCode()).thenReturn(value);
        return code;
    }
}
