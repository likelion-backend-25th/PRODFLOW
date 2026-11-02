package net.likelion.bebc25.prodflow.workorder.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.global.security.jwt.JwtProvider;
import net.likelion.bebc25.prodflow.workorder.dto.WorkOrderDto;
import net.likelion.bebc25.prodflow.workorder.serivce.WorkOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("api/v1/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final JwtProvider jwtProvider;

    // 계약금 확인 전이므로 수주 흐름 대신 이 API로 등록을 시험한다.
    @Operation(
            summary = "작업지시 등록",
            description = "수주 품목, 첫 라인, 담당자, 수량, 생산 기간으로 작업지시와 생산계획을 만든다. 한 라인에 다 들어가지 않으면 다른 가동 라인으로 나눈다."
    )
    @ApiResponse(
            responseCode = "201",
            description = "등록 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = WorkOrderDto.Response.class)))
    )
    @ApiResponse(responseCode = "400", description = "수주 품목, 라인, 담당자, 준비 상태 코드를 찾을 수 없거나 기간 안 생산 가능량을 초과함")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @PostMapping
    public ResponseEntity<List<WorkOrderDto.Response>> create(
            @RequestBody WorkOrderDto.CreateRequest request,
            @Parameter(hidden = true) HttpServletRequest httpRequest
    ) {
        String header = httpRequest.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        Long userId = jwtProvider.getUserId(header.substring(7));
        List<WorkOrderDto.Response> responses = workOrderService.create(request, userId);
        return ResponseEntity
                .created(URI.create("/api/v1/work-orders/" + responses.getFirst().id()))
                .body(responses);
    }


    @Operation(
            summary = "작업지시 목록 조회",
            description = "로그인한 사용자가 작업지시를 조회한다. 완료 예정일이 빠른 순이고, 같은 날이면 최근 등록이 앞이다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = WorkOrderDto.ListResponse.class)))
    )
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @GetMapping
    public ResponseEntity<List<WorkOrderDto.ListResponse>> getList() {
        // 서비스가 완료 예정일, 최근 등록 순으로 이미 정렬해 둔다.
        return ResponseEntity.ok(workOrderService.getList());
    }
}
