package net.likelion.bebc25.prodflow.customer_order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderListDto;
import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListSortType;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListTab;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.customer_order.service.CustomerOrderService;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "수주", description = "수주 등록 및 조회 API")
@RequiredArgsConstructor
public class CustomerOrderController {
    private final CustomerOrderService customerOrderService;

    // 수주 등록
    @Operation(summary = "수주 등록", description = "승인 견적으로 수주와 품목을 등록한다. 품목별 수량은 100개 이상, "
            + "견적 수량 이하이며 단가·VAT·거래처 정보는 견적에서 가져온다. 최종 납기는 저장 직전에 재검증한다. "
            + "회사 주소 배송이면 shippingAddress를 생략한다."
            + "결제 건·가상계좌·작업지시는 생성하지 않는다. 이미 수주가 있으면 409와 기존 수주번호를 반환한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "수주 등록 완료"),
            @ApiResponse(responseCode = "400", description = "입력값·품목·수량·배송지·납기 오류", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
            @ApiResponse(responseCode = "403", description = "권한 또는 계정 조건 미충족", content = @Content),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 견적", content = @Content),
            @ApiResponse(responseCode = "409", description = "승인 상태가 아니거나 이미 등록된 수주", content = @Content)
    })
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    @PostMapping
    public ResponseEntity<CustomerOrderDto.CreateResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CustomerOrderDto.CreateRequest request
    ) {
        CustomerOrderDto.CreateResponse response = customerOrderService.create(request, userDetails.getUserId());

        return ResponseEntity.created(URI.create("/api/v1/orders/" + response.id())).body(response);
    }

    // 수주 목록 조회
    @Operation(summary = "수주 목록 조회", description = "OPEN은 완료·취소 제외, DONE은 완료만 조회. "
            + "검색어는 수주번호·거래처명·저장된 품목명에 적용하며 조건은 모두 AND로 적용한다. "
            + "기본 8개, 등록일 최신순. COMPLETED_AT은 배송 완료일 최신순이며 완료일이 없으면 뒤에 표시한다. "
            + "진행 상태는 그룹 ID 2의 common_code이며 statusCodeId로 검색한다. "
            + "납기·총액은 저장된 수주 값, completedAt은 배송 완료일, 입금 상태는 PARTIALLY_PAID를 사용한다.")
    @GetMapping
    public ResponseEntity<PageResponse<CustomerOrderListDto.Response>> getOrders(
            @RequestParam(defaultValue = "OPEN") OrderListTab tab,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ConfirmationStatus confirmationStatus,
            @RequestParam(required = false) Long statusCodeId,
            @RequestParam(required = false) OrderPaymentStatus paymentStatus,
            @RequestParam(defaultValue = "CREATED_AT") OrderListSortType sortType,
            @ParameterObject @PageableDefault(size = 8) Pageable pageable) {

        return ResponseEntity.ok(customerOrderService.getOrders(new CustomerOrderListDto.SearchCondition(
                tab, keyword, confirmationStatus, statusCodeId, paymentStatus, sortType), pageable));
    }

    // 수주 전체 집계 조회
    @Operation(summary = "수주 전체 집계", description = "검색·페이지와 독립된 카드 및 탭 건수. "
            + "생산중은 IN_PRODUCTION·PACKING·ON_HOLD, 배송중은 READY_TO_SHIP·SHIPPING 합계. 이번 달은 한국 날짜의 배송 완료일 기준.")
    @GetMapping("/summary")
    public ResponseEntity<CustomerOrderListDto.SummaryResponse> getSummary() {

        return ResponseEntity.ok(customerOrderService.getSummary());
    }

    // 수주 진행 상태 선택 목록 조회
    @Operation(summary = "수주 진행 상태 목록", description = "common_code 중 code_group_id=2이고 ACTIVE인 코드를 sort_order 순으로 반환한다.")
    @GetMapping("/statuses")
    public ResponseEntity<List<CustomerOrderListDto.StatusResponse>> getStatuses() {

        return ResponseEntity.ok(customerOrderService.getStatuses());
    }

    // 검색 결과 전체 CSV 다운로드
    @Operation(summary = "수주 목록 CSV 내보내기", description = "목록과 동일한 검색 조건에 맞는 전체 결과를 UTF-8 BOM CSV로 반환한다. 페이지 제한 없음.")
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "OPEN") OrderListTab tab,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ConfirmationStatus confirmationStatus,
            @RequestParam(required = false) Long statusCodeId,
            @RequestParam(required = false) OrderPaymentStatus paymentStatus,
            @RequestParam(defaultValue = "CREATED_AT") OrderListSortType sortType) {

        byte[] csv = customerOrderService.export(new CustomerOrderListDto.SearchCondition(
                tab, keyword, confirmationStatus, statusCodeId, paymentStatus, sortType));

        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("수주목록.csv", StandardCharsets.UTF_8).build().toString()).body(csv);
    }
}
