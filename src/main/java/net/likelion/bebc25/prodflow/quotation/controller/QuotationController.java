package net.likelion.bebc25.prodflow.quotation.controller;

import jakarta.validation.Valid;
import java.beans.PropertyEditorSupport;
import java.net.URI;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.WebDataBinder;

import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationPdfDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationRescheduleDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationScheduleDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationSortType;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.service.QuotationPdfStorageService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationRescheduleService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationSchedulePreviewService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationSendService;

@RestController
@RequestMapping("/api/v1/quotations")
@Tag(name = "견적", description = "견적 생성 및 목록·상세 조회 API")
@RequiredArgsConstructor
public class QuotationController {
    private final QuotationService quotationService;
    private final QuotationSchedulePreviewService quotationSchedulePreviewService;
    private final QuotationRescheduleService quotationRescheduleService;
    private final QuotationPdfStorageService quotationPdfStorageService;
    private final QuotationSendService quotationSendService;

    // 확인한 견적 PDF 발송
    @Operation(summary = "견적 발송 확정 및 PDF 이메일 전송",
            description = "미리보기의 confirmationToken으로 확인한 PDF를 발송한다. "
                    + "SENT 커밋 후 이메일을 전송하며, 중복 요청은 다시 전송하지 않는다. "
                    + "emailStatus로 SENT·FAILED·UNKNOWN을 구분한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "견적 확정 및 이메일 처리 결과. 중복 요청 포함"),
            @ApiResponse(responseCode = "400", description = "확인값 입력 오류", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
            @ApiResponse(responseCode = "403", description = "발송 권한 또는 계정 조건 미충족", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 견적", content = @Content),
            @ApiResponse(responseCode = "409", description = "견적·PDF·납기·유효기간 재확인 필요", content = @Content),
            @ApiResponse(responseCode = "502", description = "발송 확정 전 PDF 읽기 실패", content = @Content)
    })
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    @PostMapping("/{quotationId}/send")
    public ResponseEntity<QuotationSendDto.Response> send(
            @PathVariable Long quotationId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody QuotationSendDto.Request request
    ) {
        QuotationSendDto.Response response = quotationSendService.send(quotationId, request, userDetails.getUserId());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
    }

    // 견적 상태 입력 변환
    @InitBinder
    public void bindQuotationStatus(WebDataBinder binder) {
        binder.registerCustomEditor(QuotationStatus.class, new PropertyEditorSupport() {
            // 견적 상태 문자열 변환
            @Override
            public void setAsText(String text) {
                setValue(QuotationStatus.valueOf(text));
            }
        });
    }

    // 견적 초안 저장 및 PDF 준비
    @Operation(summary = "견적 초안 저장 및 PDF 생성",
            description = "초안을 저장·커밋한 뒤 PDF를 S3에 저장하고 미리보기 URL을 JSON으로 반환한다. "
                    + "실패해도 초안은 유지하며 응답의 quotationId로 POST 재생성 API를 호출한다. 이메일은 발송하지 않는다.")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "초안 저장 및 PDF 준비 성공"),
            @ApiResponse(responseCode = "400", description = "입력·업무 규칙 또는 PDF 생성 오류", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
            @ApiResponse(responseCode = "403", description = "작성 권한 또는 계정 조건 미충족", content = @Content),
            @ApiResponse(responseCode = "502", description = "PDF 준비 실패. 저장된 견적 ID로 재시도", content = @Content)
    })
    @PostMapping("/pdf")
    public ResponseEntity<QuotationPdfDto.PreviewResponse> createPdf(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody QuotationDto.CreateRequest request
    ) {
        QuotationPdfDto.PreviewResponse response = quotationPdfStorageService.create(request, userDetails.getUserId());
        return ResponseEntity.created(URI.create("/api/v1/quotations/" + response.quotationId()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Quotation-Id", response.quotationId().toString())
                .body(response);
    }

    // 견적 초안 수정 및 PDF 준비
    @Operation(summary = "견적 초안 수정 및 PDF 재생성",
            description = "DRAFT 견적을 수정하며 견적 ID·번호·최초 작성자는 유지한다. "
                    + "생성과 동일한 전체 입력을 받으며 품목·금액·납기를 갱신한다. "
                    + "수정 커밋 후 PDF를 S3에 저장하고 미리보기 URL을 JSON으로 반환한다. 실패해도 수정은 유지한다.")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "초안 수정 및 PDF 준비 성공"),
            @ApiResponse(responseCode = "400", description = "입력·초안 상태 또는 PDF 생성 오류", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
            @ApiResponse(responseCode = "403", description = "수정 권한 또는 계정 조건 미충족", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 견적", content = @Content),
            @ApiResponse(responseCode = "502", description = "PDF 준비 실패. 수정한 초안은 유지", content = @Content)
    })
    @PutMapping("/{quotationId}/pdf")
    public ResponseEntity<QuotationPdfDto.PreviewResponse> updatePdf(
            @PathVariable Long quotationId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody QuotationDto.CreateRequest request
    ) {
        QuotationPdfDto.PreviewResponse response = quotationPdfStorageService.update(
                quotationId, request, userDetails.getUserId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Quotation-Id", response.quotationId().toString())
                .location(URI.create("/api/v1/quotations/" + response.quotationId()))
                .body(response);
    }

    // 견적 PDF 재생성
    @Operation(summary = "저장된 견적 초안 PDF 재생성",
            description = "기존 DRAFT로 PDF를 다시 생성해 S3에 저장한다. 새 견적을 만들거나 이메일을 보내지 않는다.")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF 재생성 성공"),
            @ApiResponse(responseCode = "400", description = "초안 상태 또는 PDF 생성 오류", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
            @ApiResponse(responseCode = "403", description = "재생성 권한 없음", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 견적", content = @Content),
            @ApiResponse(responseCode = "502", description = "PDF 준비 실패", content = @Content)
    })
    @PostMapping("/{quotationId}/pdf/regenerate")
    public ResponseEntity<QuotationPdfDto.PreviewResponse> regeneratePdf(@PathVariable Long quotationId) {
        QuotationPdfDto.PreviewResponse response = quotationPdfStorageService.prepare(quotationId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Quotation-Id", response.quotationId().toString())
                .location(URI.create("/api/v1/quotations/" + response.quotationId()))
                .body(response);
    }

    // 견적 PDF 미리보기 URL 조회
    @Operation(summary = "견적 PDF 미리보기 URL 조회",
            description = "업로드 완료 후 저장된 S3 객체 키로 10분 유효 조회 URL을 발급한다. PDF를 생성하지 않는다. 미준비 상태는 409 응답한다.")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "미리보기 URL 발급 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
            @ApiResponse(responseCode = "403", description = "조회 권한 없음", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 견적", content = @Content),
            @ApiResponse(responseCode = "409", description = "PDF 미준비. 재생성 필요", content = @Content),
            @ApiResponse(responseCode = "502", description = "미리보기 URL 발급 실패", content = @Content)
    })
    @GetMapping("/{quotationId}/pdf")
    public ResponseEntity<QuotationPdfDto.PreviewResponse> getPdf(@PathVariable Long quotationId) {
        QuotationPdfDto.PreviewResponse response = quotationPdfStorageService.getPreview(quotationId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Quotation-Id", response.quotationId().toString())
                .location(URI.create("/api/v1/quotations/" + response.quotationId()))
                .body(response);
    }

    // 견적 목록 조회
    @Operation(
            summary = "견적 목록 조회",
            description = "검색어와 상태로 견적 목록을 조회한다. 상태를 생략하면 전체 상태를 조회한다. "
                    + "선택한 날짜 기준 내림차순으로 정렬하고, 같은 날짜는 ID 내림차순으로 정렬한다. "
                    + "기간은 startDate와 endDate로 입력하며 날짜 형식은 yyyy-MM-dd이다. 생략하거나 빈 값이면 날짜 조건을 지정하지 않은 것으로 본다. "
                    + "현재 기간 파라미터는 입력 형식 검증만 수행하며 실제 조회 조건 연결은 후속 단계에서 적용한다. "
                    + "page는 0부터 시작하며 size 기본값은 12이다. Pageable의 sort 대신 sortType을 사용한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 상태·정렬 기준·날짜 형식 또는 페이징 값", content = @Content)
    })
    @GetMapping
    public ResponseEntity<PageResponse<QuotationDto.Response>> getQuotations(
            @Parameter(description = "검색어 (견적번호, 거래처명, 작성자)", example = "QT-20261103")
            @RequestParam(required = false) String keyword, // 값없으면 null
            @Parameter(description = "견적 상태 (생략 시 전체)",
                    schema = @Schema(allowableValues = {"SENT", "APPROVED", "REJECTED", "CANCELLED", "EXPIRED"}))
            @RequestParam(required = false) QuotationStatus status,
            @Parameter(description = "정렬 기준 (작성일, 희망 납기일, 예상 납기일, 유효기간)", schema = @Schema(defaultValue = "CREATED_AT",
                    allowableValues = {"CREATED_AT", "REQUESTED_DUE_DATE", "ESTIMATED_DUE_DATE", "VALID_UNTIL"}))
            @RequestParam(defaultValue = "CREATED_AT") QuotationSortType sortType,
            @Parameter(description = "선택한 sortType 기준 기간 시작일 (해당 날짜 포함, 조회 조건 연결 예정)", example = "2026-11-01")
            @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "선택한 sortType 기준 기간 종료일 (해당 날짜 포함, 조회 조건 연결 예정)", example = "2026-11-30")
            @RequestParam(required = false) LocalDate endDate,
            @ParameterObject @PageableDefault(size = 12) Pageable pageable
    ) {
        PageResponse<QuotationDto.Response> response = quotationService.getQuotationList(
                keyword, status, sortType, pageable);

        return ResponseEntity.ok(response);
    }

    // 견적 상세 조회
    @Operation(summary = "견적 상세 조회", description = "견적 ID로 견적 정보, 품목, 금액 및 저장된 납기 일정을 조회한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 견적", content = @Content)
    })
    @GetMapping("/{quotationId}")
    public ResponseEntity<QuotationDto.DetailResponse> getQuotationById(
            @Parameter(description = "견적 ID", example = "1", required = true)
            @PathVariable("quotationId") Long quotationId
    ) {
        QuotationDto.DetailResponse response = quotationService.getQuotationById(quotationId);

        return ResponseEntity.ok(response);
    }

    // 신규 견적 납기 미리보기
    @Operation(
            summary = "견적 납기 미리보기",
            description = "최고 관리자 또는 수주 관리자가 품목과 수량으로 납기를 계산한다. "
                    + "희망 납기를 생략하면 가장 빠른 납기와 생산 일정을 반환한다. "
                    + "희망 납기가 가장 빠른 납기보다 이르면 feasible=false와 비어 있는 생산 일정을 반환한다. "
                    + "납기는 한국 날짜 기준 다음 해 12월 31일까지 계산할 수 있다. "
                    + "견적을 저장하거나 생산 자리를 예약하지 않으며, 최종 견적 생성 시 다시 계산한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "미리보기 성공 (선택한 날짜가 불가능한 경우도 포함)"),
            @ApiResponse(responseCode = "400", description = "잘못된 입력·품목 또는 계산 범위 초과", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
            @ApiResponse(responseCode = "403", description = "미리보기 권한 없음", content = @Content)
    })
    @PostMapping("/schedule")
    public ResponseEntity<QuotationScheduleDto.Response> previewSchedule(
            @Valid @RequestBody QuotationScheduleDto.Request request
    ) {
        QuotationScheduleDto.Response response = quotationSchedulePreviewService.previewSchedule(request);

        return ResponseEntity.ok(response);
    }

    // 기존 견적 납기 재산정 미리보기
    @Operation(summary = "기존 견적 납기 재산정 미리보기",
            description = "최고 관리자 또는 수주 관리자가 유효기간 내 SENT 견적의 저장된 품목 수량으로 납기를 계산한다. "
                    + "희망 납기를 생략하면 가장 빠른 납기를 반환하며, 불가능한 날짜는 feasible=false로 반환한다. "
                    + "오늘 기준 다음 해 말일까지 계산하고 견적 정보는 변경하지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "미리보기 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 입력·재산정 불가 상태·유효기간 만료·계산 범위 초과", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
            @ApiResponse(responseCode = "403", description = "재산정 권한 없음", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 견적", content = @Content)
    })
    @PostMapping("/{quotationId}/schedule")
    public ResponseEntity<QuotationScheduleDto.Response> previewReschedule(
            @Parameter(description = "기존 견적 ID", example = "1") @PathVariable Long quotationId,
            @Valid @RequestBody QuotationRescheduleDto.PreviewRequest request
    ) {
        return ResponseEntity.ok(quotationRescheduleService.previewSchedule(quotationId, request));
    }

    // 기존 견적 납기 변경
    @Operation(summary = "기존 견적 납기 변경",
            description = "최고 관리자 또는 수주 관리자가 유효기간 내 SENT 견적의 희망 납기를 변경한다. "
                    + "저장 직전 최신 조건으로 다시 계산하며, 불가능하면 기존 일정을 유지한다. "
                    + "희망 납기·예상 납기·생산 시작 및 완료 예정일만 변경하고 품목·금액·유효기간·상태는 유지한다. 이메일은 발송하지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "일정 변경 성공 및 견적 상세 반환"),
            @ApiResponse(responseCode = "400", description = "잘못된 입력·재산정 불가 상태·유효기간 만료·불가능한 납기", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
            @ApiResponse(responseCode = "403", description = "재산정 권한 없음", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 견적", content = @Content)
    })
    @PatchMapping("/{quotationId}/schedule")
    public ResponseEntity<QuotationDto.DetailResponse> updateSchedule(
            @Parameter(description = "기존 견적 ID", example = "1") @PathVariable Long quotationId,
            @Valid @RequestBody QuotationRescheduleDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(quotationRescheduleService.updateSchedule(quotationId, request));
    }
}
