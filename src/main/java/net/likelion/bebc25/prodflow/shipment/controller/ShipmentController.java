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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @Operation(
            summary = "운송장 등록",
            description = "출고대기 수주에 택배사와 운송장 번호를 저장하고 수주 상태를 배송중으로 바꾼다. 수주당 운송장은 하나이고, 같은 택배사의 운송장 번호는 중복할 수 없다."
    )
    @ApiResponse(
            responseCode = "201",
            description = "등록 성공",
            content = @Content(schema = @Schema(implementation = ShipmentDto.Response.class))
    )
    @ApiResponse(responseCode = "400", description = "출고대기가 아님, 운송장 중복, 택배사 없음, 운송장 번호 형식 오류")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @ApiResponse(responseCode = "404", description = "수주 또는 직원을 찾을 수 없음")
    @PostMapping
    public ResponseEntity<ShipmentDto.Response> register(
            @RequestBody ShipmentDto.CreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ShipmentDto.Response response = shipmentService.register(request, userDetails.getUserId());
        return ResponseEntity
                .created(URI.create("/api/v1/shipments/" + response.id()))
                .body(response);
    }
}
