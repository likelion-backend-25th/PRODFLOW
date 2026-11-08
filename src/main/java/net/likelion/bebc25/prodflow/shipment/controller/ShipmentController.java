package net.likelion.bebc25.prodflow.shipment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentDto;
import net.likelion.bebc25.prodflow.shipment.service.ShipmentService;
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
public class ShipmentController {

    private final ShipmentService shipmentService;

    @Operation(
            summary = "배송 목록 조회",
            description = "배송 관리 화면용 목록이다. tab으로 포장중(PACKING), 출고대기(READY_TO_SHIP), 배송중(SHIPPING), 잔금대기(BALANCE)를 고른다. tab을 생략하면 출고대기다. 응답에 탭별 건수와 납기 빠른 순 목록이 포함된다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = ShipmentDto.ListResponse.class))
    )
    @ApiResponse(responseCode = "400", description = "지원하지 않는 tab")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @GetMapping
    public ResponseEntity<ShipmentDto.ListResponse> findList(
            @RequestParam(required = false, defaultValue = "READY_TO_SHIP") String tab
    ) {
        return ResponseEntity.ok(shipmentService.findList(tab));
    }

    @Operation(
            summary = "배송 완료 처리",
            description = "배송중 운송장을 수동으로 완료 처리하고 수주 상태를 COMPLETED로 바꾼다. 잔금이 남아 있으면 잔금대기 목록에 보인다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "완료 처리 성공",
            content = @Content(schema = @Schema(implementation = ShipmentDto.CompleteResponse.class))
    )
    @ApiResponse(responseCode = "400", description = "배송중이 아님")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @ApiResponse(responseCode = "404", description = "운송장 또는 수주를 찾을 수 없음")
    @PostMapping("/{id}/complete")
    public ResponseEntity<ShipmentDto.CompleteResponse> complete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(shipmentService.complete(id, userDetails.getUserId()));
    }
}
