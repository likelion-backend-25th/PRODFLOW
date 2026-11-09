package net.likelion.bebc25.prodflow.shipment;

import net.likelion.bebc25.prodflow.global.exception.GlobalExceptionHandler;
import net.likelion.bebc25.prodflow.shipment.controller.ShipmentController;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentDto;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentTrackingDto;
import net.likelion.bebc25.prodflow.shipment.service.ShipmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ShipmentControllerTest {
    private final ShipmentService shipmentService = mock(ShipmentService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ShipmentController(shipmentService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("수주 ID로 저장된 단건 배송 정보를 조회한다")
    void getByOrderId() throws Exception {
        LocalDateTime startedAt = LocalDateTime.of(2026, 11, 6, 10, 0);
        ShipmentTrackingDto shipment = new ShipmentTrackingDto(10L, 3L, "SHIPPING", "04", "CJ대한통운",
                "530099990001", 3, "Y", false, "2026-11-09", startedAt, null);
        when(shipmentService.getByOrderId(3L)).thenReturn(new ShipmentDto.OrderResponse(shipment));

        mvc.perform(get("/api/v1/shipments/{orderId}", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment.shipmentId").value(10))
                .andExpect(jsonPath("$.shipment.orderId").value(3))
                .andExpect(jsonPath("$.shipment.carrierCode").value("04"))
                .andExpect(jsonPath("$.shipment.carrierName").value("CJ대한통운"))
                .andExpect(jsonPath("$.shipment.trackingNumber").value("530099990001"))
                .andExpect(jsonPath("$.shipment.level").value(3))
                .andExpect(jsonPath("$.shipment.complete").value(false))
                .andExpect(jsonPath("$.shipment.estimate").value("2026-11-09"))
                .andExpect(jsonPath("$.shipment.startedAt").value("2026-11-06T10:00:00"));
        verify(shipmentService).getByOrderId(3L);
    }

    @Test
    @DisplayName("배송이 미등록된 수주도 200과 null 배송 정보로 응답한다")
    void getOrderWithoutShipment() throws Exception {
        when(shipmentService.getByOrderId(10L)).thenReturn(new ShipmentDto.OrderResponse(null));

        mvc.perform(get("/api/v1/shipments/{orderId}", 10L))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"shipment\":null}"));
    }

    @Test
    @DisplayName("없는 수주는 404로 응답한다")
    void unknownOrderReturnsNotFound() throws Exception {
        when(shipmentService.getByOrderId(Long.MAX_VALUE)).thenThrow(new NoSuchElementException("수주를 찾을 수 없습니다."));

        mvc.perform(get("/api/v1/shipments/{orderId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("수주를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("수주 ID 경로가 없으면 기본 탭의 배송 목록을 조회한다")
    void getListWithoutOrderId() throws Exception {
        when(shipmentService.findList("READY_TO_SHIP")).thenReturn(new ShipmentDto.ListResponse(
                "READY_TO_SHIP", new ShipmentDto.TabCounts(0, 0, 0, 0), java.util.List.of()));

        mvc.perform(get("/api/v1/shipments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tab").value("READY_TO_SHIP"))
                .andExpect(jsonPath("$.items").isEmpty());
        verify(shipmentService).findList("READY_TO_SHIP");
        verify(shipmentService, never()).getByOrderId(any());
    }

    @Test
    @DisplayName("숫자가 아닌 수주 ID는 서비스 호출 전에 거부한다")
    void rejectInvalidOrderId() throws Exception {
        mvc.perform(get("/api/v1/shipments/{orderId}", "invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(shipmentService);
    }
}
