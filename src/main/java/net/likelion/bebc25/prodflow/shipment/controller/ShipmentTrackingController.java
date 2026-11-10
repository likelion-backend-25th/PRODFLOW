package net.likelion.bebc25.prodflow.shipment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.shipment.client.ShipmentTrackingClient;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentTrackingDto;
import net.likelion.bebc25.prodflow.shipment.dto.TrackingApiDto;
import net.likelion.bebc25.prodflow.shipment.service.ShipmentTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @Operation(
            summary = "운송장 배송조회 갱신",
            description = "스마트택배 API로 운송장 상태를 다시 조회한다. 결과가 배송완료면 수주 상태를 COMPLETED로 바꾼다."
    )
    @ApiResponse(responseCode = "200", description = "갱신 성공")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @ApiResponse(responseCode = "404", description = "운송장을 찾을 수 없음")
    @PostMapping("/{id}/tracking/refresh")
    public ResponseEntity<ShipmentTrackingDto> refreshTracking(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                shipmentTrackingService.refreshByShipmentId(id, userDetails.getUserId())
        );
    }
}
