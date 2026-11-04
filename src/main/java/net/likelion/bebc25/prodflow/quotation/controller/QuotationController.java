package net.likelion.bebc25.prodflow.quotation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationRescheduleDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationScheduleDto;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationSortType;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.service.QuotationPdfService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationRescheduleService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationSchedulePreviewService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.WebDataBinder;
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

import java.beans.PropertyEditorSupport;
import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/quotations")
@RequiredArgsConstructor
@Tag(name = "견적", description = "견적 생성 및 목록·상세 조회 API")
public class QuotationController {
    private final QuotationService quotationService;
    private final QuotationSchedulePreviewService quotationSchedulePreviewService;
    private final QuotationRescheduleService quotationRescheduleService;
    private final QuotationPdfService quotationPdfService;

    // 견적 상태는 생략할 수 있지만 빈 문자열이나 공백은 허용하지 않는다.
    @InitBinder
    public void bindQuotationStatus(WebDataBinder binder) {
        binder.registerCustomEditor(QuotationStatus.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(QuotationStatus.valueOf(text));
            }
        });
    }

//    @Operation(
//            summary = "견적 초안 저장",
//            description = "최고 관리자 또는 수주 관리자가 DRAFT 견적을 저장한다. 납기와 금액은 서버에서 계산하며 생산 자리를 예약하지 않는다. "
//                    + "기존 거래처 ID 또는 신규 거래처 정보 중 하나만 입력한다. 품목별 수량은 최소 100개이다. "
//                    + "유효기간은 발행일 기준 7일 또는 14일만 허용하며, 생략하면 14일을 적용한다."
//    )
//    @ApiResponses({
//            @ApiResponse(responseCode = "201", description = "생성 성공"),
//            @ApiResponse(responseCode = "400", description = "입력값 또는 업무 규칙 위반", content = @Content),
//            @ApiResponse(responseCode = "401", description = "인증 실패 또는 작성자 계정 없음", content = @Content),
//            @ApiResponse(responseCode = "403", description = "작성 권한 없음 또는 계정 상태·비밀번호 변경 조건 미충족", content = @Content)
//    })
//    @PostMapping
//    public ResponseEntity<QuotationDto.DetailResponse> create(
//            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
//            @Valid @RequestBody QuotationDto.CreateRequest request
//    ) {
//        Long userId = userDetails.getUserId();
//
//        QuotationDto.DetailResponse response = quotationService.create(request, userId);
//        return ResponseEntity.created(URI.create("/api/v1/quotations/" + response.id())).body(response);
//    }

    @Operation(summary = "견적 초안 저장 및 PDF 생성",
            description = "초안을 DRAFT로 저장한 뒤 PDF를 생성한다. PDF 생성 실패 시에도 초안은 유지한다. "
                    + "Location과 X-Quotation-Id 헤더로 저장된 초안을 확인하고 GET PDF API로 재시도할 수 있다. "
                    + "S3 저장과 이메일 발송은 하지 않는다.")
    @PostMapping("/pdf")
    public ResponseEntity<byte[]> createPdf(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody QuotationDto.CreateRequest request,
            HttpServletResponse servletResponse
    ) {
        QuotationDto.DetailResponse draft = quotationService.create(request, userDetails.getUserId());
        // create의 트랜잭션 커밋 후 생성한다. 실패해도 재시도할 초안 ID를 전달한다.
        servletResponse.setHeader(HttpHeaders.LOCATION, "/api/v1/quotations/" + draft.id());
        servletResponse.setHeader("X-Quotation-Id", draft.id().toString());
        return pdfResponse(draft, quotationPdfService.generate(draft), true);
    }

    @Operation(summary = "견적 초안 수정 및 PDF 재생성",
            description = "DRAFT 견적을 수정하며 견적 ID·번호·최초 작성자는 유지한다. "
                    + "생성과 동일한 전체 입력을 받으며 품목·금액·납기를 갱신한다. "
                    + "수정 커밋 후 PDF를 생성하므로 PDF 실패 시 수정 내용은 유지된다. S3와 이메일은 처리하지 않는다.")
    @PutMapping("/{quotationId}/pdf")
    public ResponseEntity<byte[]> updatePdf(
            @PathVariable Long quotationId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody QuotationDto.CreateRequest request,
            HttpServletResponse servletResponse
    ) {
        QuotationDto.DetailResponse draft = quotationService.updateDraft(quotationId, request, userDetails.getUserId());
        servletResponse.setHeader(HttpHeaders.LOCATION, "/api/v1/quotations/" + draft.id());
        servletResponse.setHeader("X-Quotation-Id", draft.id().toString());
        return pdfResponse(draft, quotationPdfService.generate(draft), false);
    }

    @Operation(summary = "저장된 견적 초안 PDF 생성",
            description = "pdf 생성 실패 시 기존 DRAFT의 저장된 데이터로 PDF를 생성한다. 새 견적을 만들거나 이메일을 보내지 않는다.")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    @GetMapping("/{quotationId}/pdf")
    public ResponseEntity<byte[]> getPdf(@PathVariable Long quotationId) {
        QuotationDto.DetailResponse draft = quotationService.getQuotationById(quotationId);
        return pdfResponse(draft, quotationPdfService.generate(draft), false);
    }

    private ResponseEntity<byte[]> pdfResponse(QuotationDto.DetailResponse draft, byte[] pdf, boolean created) {
        return ResponseEntity.status(created ? 201 : 200)
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(draft.quotationNo() + ".pdf").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Quotation-Id", draft.id().toString())
                .location(URI.create("/api/v1/quotations/" + draft.id()))
                .body(pdf);
    }

    // 견적 목록 조회
    // 견적번호, 거래처명, 작성자 입력 검색
    // 발송완료, 승인, 거절, 취소, 만료
    // 작성일·희망납기·예상납기·유효기간 내림차순
    // 상태 생략 시 전체 상태 조회
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
            @Parameter(description = "견적 상태 (생략 시 전체)", schema = @Schema(allowableValues = {"SENT", "APPROVED", "REJECTED", "CANCELLED", "EXPIRED"}))
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
        PageResponse<QuotationDto.Response> response = quotationService.getQuotationList(keyword, status, sortType, pageable);

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

    // 견적 저장 없이 가장 빠른 납기와 선택한 날짜의 생산 가능 여부 조회
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

    // 견적 작성 후 기존 견적의 품목 수량으로 납기 재산정 미리보기
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

    // 재산정한 납기를 최종 검증하고 기존 견적 일정 변경
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
