package net.likelion.bebc25.prodflow.shipment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.shipment.dto.CarrierDto;
import net.likelion.bebc25.prodflow.shipment.service.CarrierService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/carriers")
@RequiredArgsConstructor
public class CarrierController {

    private final CarrierService carrierService;

    @Operation(
            summary = "택배사 목록 조회",
            description = "출고 화면 택배사 선택용이다. DB carrier 테이블에 등록된 택배사 code/name만 내려준다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CarrierDto.Response.class)))
    )
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @GetMapping
    public ResponseEntity<List<CarrierDto.Response>> findAll() {
        return ResponseEntity.ok(carrierService.findAll());
    }
}
