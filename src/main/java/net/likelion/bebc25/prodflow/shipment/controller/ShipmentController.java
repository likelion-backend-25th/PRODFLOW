package net.likelion.bebc25.prodflow.shipment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentDto;
import net.likelion.bebc25.prodflow.shipment.service.ShipmentService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @Operation(
            summary = "배송 목록 조회",
            description = "배송 관리 화면용 목록이다. tab으로 포장중(PACKING), 출고대기(READY_TO_SHIP), 배송중(SHIPPING), 잔금대기(BALANCE)를 고른다. "
                    + "tab을 생략하면 출고대기다. 응답에 탭별 전체 건수와 현재 탭의 페이징 목록이 포함된다. "
                    + "page는 0부터 시작하며 기본 size는 10, 납기(estimatedDueDate) 오름차순이다. "
                    + "포장중 탭은 작업지시가 있고 전부 포장완료(PACK_DONE)인 수주만 목록·건수에 포함하며, "
                    + "readyToShipEligible은 그 항목에서 true다."
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
            @RequestParam(required = false, defaultValue = "READY_TO_SHIP") String tab,
            @PageableDefault(size = 10, sort = "estimatedDueDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(shipmentService.findList(tab, pageable));
    }

    @Operation(
            summary = "포장중 → 출고대기 전환",
            description = "포장중 수주를 출고대기로 바꾼다. 해당 수주의 모든 작업지시가 포장완료(PACK_DONE)여야 한다. "
                    + "출고대기에서만 운송장을 등록할 수 있다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "전환 성공",
            content = @Content(schema = @Schema(implementation = ShipmentDto.ReadyToShipResponse.class))
    )
    @ApiResponse(responseCode = "400", description = "포장중이 아님, 작업 미완료")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @ApiResponse(responseCode = "404", description = "수주 또는 직원을 찾을 수 없음")
    @PostMapping("/orders/{orderId}/ready-to-ship")
    public ResponseEntity<ShipmentDto.ReadyToShipResponse> markReadyToShip(
            @Parameter(description = "수주 ID", required = true)
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(shipmentService.markReadyToShip(orderId, userDetails.getUserId()));
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

    @Operation(
            summary = "수주별 배송 정보 조회",
            description = "수주 ID로 저장된 배송 정보만 조회한다. 배송 미등록 시 shipment는 null이다. "
                    + "외부 배송 추적 API를 호출하거나 수주·배송 상태를 변경하지 않는다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = ShipmentDto.OrderResponse.class)))
    @ApiResponse(responseCode = "400", description = "수주 ID 형식 오류")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @ApiResponse(responseCode = "404", description = "수주를 찾을 수 없음")
    @GetMapping("/{orderId}")
    public ResponseEntity<ShipmentDto.OrderResponse> getByOrderId(
            @Parameter(description = "수주 ID", required = true)
            @PathVariable Long orderId) {
        ShipmentDto.OrderResponse response = shipmentService.getByOrderId(orderId);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "운송장 등록",
            description = "출고대기 수주의 운송장과 잔금 계좌를 준비하고 거래처 이메일 안내가 완료되면 배송중으로 확정한다. "
                    + "실패 시 출고대기를 유지한다. 같은 운송장으로 재시도하며 기존 유효 계좌·메일 성공 결과를 재사용한다. "
                    + "전송 결과가 불명확하면 중복 발송하지 않는다. 수주당 운송장은 하나이다."
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
