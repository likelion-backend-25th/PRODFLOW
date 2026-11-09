package net.likelion.bebc25.prodflow.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.payment.dto.PaymentDto;
import net.likelion.bebc25.prodflow.payment.dto.PortOneDto;
import net.likelion.bebc25.prodflow.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "결제", description = "수주 결제·입금 조회 API")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @Operation(summary = "현금 계약금 수동 입금 처리", description = "계약금 전액을 현금으로 받았음을 기록하고 수주를 생산대기로 변경한다. "
            + "작업지시와 생산계획을 함께 생성하며 실패 시 모두 롤백한다. 가상계좌 발급·말소와 PortOne 호출은 수행하지 않는다.")
    @ApiResponse(responseCode = "200", description = "현금 계약금 처리 완료")
    @ApiResponse(responseCode = "400", description = "입금 금액·일시 오류")
    @ApiResponse(responseCode = "401", description = "로그인 필요")
    @ApiResponse(responseCode = "403", description = "처리 권한 없음")
    @ApiResponse(responseCode = "404", description = "수주를 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "이미 처리된 계약금, 수주 상태 또는 작업 배정 충돌")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    @PostMapping("/deposit/manual")
    public ResponseEntity<PaymentDto.ManualDepositResponse> recordManualDeposit(
            @Valid @RequestBody PaymentDto.ManualDepositRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        PaymentDto.ManualDepositResponse response = paymentService.recordManualDeposit(request, userDetails.getUserId());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "수주별 결제·입금 조회", description = "계약금 예정액·누적 입금액·미수금과 결제별 입금 내역·최신 계좌를 반환한다. "
            + "계약금은 총액의 50%를 원 단위 내림하며, PAID 결제만 누적 입금액에 합산한다. "
            + "결제가 없으면 빈 목록을 반환하며 결제·계좌를 생성하거나 외부 API를 호출하지 않는다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = PaymentDto.OrderResponse.class)))
    @ApiResponse(responseCode = "400", description = "수주 ID 누락 또는 형식 오류")
    @ApiResponse(responseCode = "401", description = "로그인 필요")
    @ApiResponse(responseCode = "404", description = "수주를 찾을 수 없음")
    @GetMapping
    public ResponseEntity<PaymentDto.OrderResponse> getByOrderId(
            @Parameter(description = "수주 ID", required = true)
            @RequestParam(required = false) Long orderId) {
        PaymentDto.OrderResponse response = paymentService.getByOrderId(orderId);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "웹훅 수신 API",
            description = "웹훅을 수신 받아 결제 상태를 갱신한다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "갱신 성공")
    })
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody PortOneDto.Webhook webhook) {
        log.info("PortOne 웹훅 수신");
        if (webhook.data() == null) {
            log.info("PortOne 웹훅 무시: data 없음");
            return ResponseEntity.noContent().build();
        }
        paymentService.handleWebhook(webhook);
        return ResponseEntity.noContent().build();
    }
}
