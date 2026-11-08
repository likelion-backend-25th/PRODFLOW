package net.likelion.bebc25.prodflow.work_order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.dto.StatusHistoryDto;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.service.StatusHistoryService;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderHistoryDto;
import net.likelion.bebc25.prodflow.work_order.serivce.WorkOrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("api/v1/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final StatusHistoryService statusHistoryService;

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
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<WorkOrderDto.Response> responses = workOrderService.create(request, userDetails.getUserId());
        return ResponseEntity
                .created(URI.create("/api/v1/work-orders/" + responses.getFirst().id()))
                .body(responses);
    }


    @Operation(
            summary = "작업지시 페이징 조회",
            description = "로그인한 사용자가 작업지시를 조회한다. 완료 예정일이 빠른 순이고, 같은 날이면 최근 등록이 앞이다. 한 페이지는 5건이다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = WorkOrderDto.PageResponse.class))
    )
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @GetMapping
    public ResponseEntity<Page<WorkOrderDto.PageResponse>> getPage(
            @RequestParam(required = false) String keyword, // 수주번호, 품목명, 거래처명, 담당자명
            @RequestParam(required = false) LocalDate from, // 지시일 시작
            @RequestParam(required = false) LocalDate to, // 지시일 종료
            @RequestParam(required = false) String status, // 작업 상태 코드
            @RequestParam(required = false) Long lineId, // 생산 라인
            @Parameter(description = "페이지 번호. 0부터 시작한다.", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "5")
            @RequestParam(defaultValue = "5") int size
    ) {
        // 정렬이 없는 페이지다. 순서는 레포지토리 메서드가 정한다.
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(workOrderService.findAll(lineId, status, from, to, keyword, pageable));
    }


    @Operation(
            summary = "작업지시 상세 조회",
            description = "로그인한 사용자가 작업지시 한 장을 조회한다. 목록 항목에 시작 예정일, 완료일, 메모, 수주 납기, 날짜별 계획 수량을 포함한다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = WorkOrderDto.DetailResponse.class))
    )
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @ApiResponse(responseCode = "404", description = "작업지시를 찾을 수 없음")
    @GetMapping("/{workOrderId}")
    public ResponseEntity<WorkOrderDto.DetailResponse> getDetail(
            @Parameter(description = "작업지시 ID", example = "1")
            @PathVariable Long workOrderId
    ) {
        return ResponseEntity.ok(workOrderService.getDetail(workOrderId));
    }


    @Operation(
            summary = "작업지시서 수정",
            description = "작업지시서의 배정 생산 라인과 담당자를 변경한다. 라인 또는 담당자 중 변경할 항목만 전달할 수 있으며, 두 항목 모두 변경할 수도 있다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "수정 성공",
            content = @Content(schema = @Schema(implementation = WorkOrderDto.DetailResponse.class))
    )
    @ApiResponse(responseCode = "400", description = "변경할 항목이 없거나 배정할 수 없는 라인 또는 담당자 정보가 유효하지 않음")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @ApiResponse(responseCode = "403", description = "라인 변경은 최고 관리자·생산 현장 관리자, 담당자 변경은 최고 관리자·수주 관리자만 가능")
    @ApiResponse(responseCode = "404", description = "작업지시서, 생산 라인 또는 담당자를 찾을 수 없음")
    @PatchMapping("/{workOrderId}")
    public ResponseEntity<WorkOrderDto.DetailResponse> update(
            @PathVariable Long workOrderId,
            @RequestBody WorkOrderDto.UpdatedRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                workOrderService.update(workOrderId, request, userDetails.getUserId())
        );
    }

    @Operation(
            summary = "작업지시 상태 변경",
            description = "배정된 생산 현장 관리자 또는 최고 관리자가 작업 상태를 바꾼다. 허용된 전이만 가능하고, 변경 내용은 status_history에 남긴다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "상태 변경 성공",
            content = @Content(schema = @Schema(implementation = WorkOrderDto.DetailResponse.class))
    )
    @ApiResponse(responseCode = "400", description = "같은 상태, 허용되지 않은 전이, 작업 상태 코드 없음, 시작 예정일 전, 대기 수주")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @ApiResponse(responseCode = "403", description = "배정된 생산 현장 관리자 또는 최고 관리자가 아님")
    @ApiResponse(responseCode = "404", description = "작업지시를 찾을 수 없음")
    @PatchMapping("/{workOrderId}/status")
    public ResponseEntity<WorkOrderDto.DetailResponse> changeStatus(
            @Parameter(description = "작업지시 ID", example = "1")
            @PathVariable Long workOrderId,
            @RequestBody WorkOrderDto.UpdateStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                workOrderService.changeStatus(workOrderId, request, userDetails.getUserId())
        );
    }

    // 작업지시서 상태 변경 이력 조회
    @Operation(
            summary = "작업지시서 상태 변경 이력 조회",
            description = "로그인한 사용자가 특정 작업지시서의 상태 변경 이력을 변경 일시 기준 최신순으로 조회한다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공 (이력이 없는 경우 빈 배열 반환)")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @GetMapping("/{workOrderId}/status-history")
    public List<StatusHistoryDto.Response> getStatusHistories(
            @PathVariable Long workOrderId
    ) {
        // 해당 작업지시서의 상태 변경 이력 조회
        return statusHistoryService.getStatusHistories(
                HistoryEntityType.WORK_ORDER,
                workOrderId
        );
    }

    // 작업지시서 라인 및 담당자 변경 이력 조회
    @Operation(
            summary = "작업지시서 변경 이력 조회",
            description = "로그인한 사용자가 특정 작업지시서의 라인 및 담당자 변경 이력을 최근 5개까지 최신순으로 조회한다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공 (이력이 없는 경우 빈 배열 반환)")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @GetMapping("/{workOrderId}/history")
    public ResponseEntity<List<WorkOrderHistoryDto.Response>> getWorkOrderHistories(
            @Parameter(description = "작업지시서 ID", example = "16")
            @PathVariable Long workOrderId
    ) {
        // 해당 작업지시서의 최근 변경 이력 5개 조회
        return ResponseEntity.ok(
                workOrderService.getWorkOrderHistories(workOrderId)
        );
    }
}
