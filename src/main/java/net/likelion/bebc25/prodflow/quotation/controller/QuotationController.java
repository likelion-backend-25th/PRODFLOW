package net.likelion.bebc25.prodflow.quotation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.global.security.jwt.JwtProvider;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationSortType;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.service.QuotationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/quotations")
@RequiredArgsConstructor
public class QuotationController {
    private final QuotationService quotationService;
    private final JwtProvider jwtProvider;

    @PostMapping
    public ResponseEntity<QuotationDto.DetailResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody QuotationDto.CreateRequest request
    ) {
        Long userId = userDetails.getUserId();

        QuotationDto.DetailResponse response = quotationService.create(request, userId);
        return ResponseEntity.created(URI.create("/api/v1/quotations/" + response.id())).body(response);
    }

    // 견적 목록 조회
    // 견적번호, 거래처명, 작성자 입력 검색
    // 발송완료, 승인, 거절, 취소, 만료
    // 작성일·희망납기·예상납기·유효기간 내림차순
    // 상태 생략 시 전체 상태 조회
    @GetMapping()
    public ResponseEntity<PageResponse<QuotationDto.Response>> getQuotations(
            @RequestParam(required = false) String keyword, // 값없으면 null
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "CREATED_AT") String sortType,
            @PageableDefault(size = 12) Pageable pageable
    ) {
        QuotationSortType quotationSortType;
        try {
            quotationSortType = QuotationSortType.valueOf(sortType);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "정렬 기준은 CREATED_AT, REQUESTED_DUE_DATE, ESTIMATED_DUE_DATE, VALID_UNTIL 중 하나여야 합니다.");
        }

        QuotationStatus quotationStatus = null;
        if (status != null) {
            try {
                quotationStatus = QuotationStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "상태는 SENT, APPROVED, REJECTED, CANCELLED, EXPIRED 중 하나여야 합니다.");
            }
        }
        PageResponse<QuotationDto.Response> response = quotationService.getQuotationList(
                keyword, quotationStatus, quotationSortType, pageable);

        return ResponseEntity.ok(response);
    }

    // 견적 상세 조회
    @GetMapping("/{quotationId}")
    public ResponseEntity<QuotationDto.DetailResponse> getQuotationById(
            @PathVariable("quotationId") Long quotationId
    ) {
        QuotationDto.DetailResponse response = quotationService.getQuotationById(quotationId);

        return ResponseEntity.ok(response);
    }
}
