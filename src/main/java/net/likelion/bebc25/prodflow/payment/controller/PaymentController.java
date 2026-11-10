package net.likelion.bebc25.prodflow.payment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.payment.dto.PaymentDto;
import net.likelion.bebc25.prodflow.payment.dto.PortOneDto;
import net.likelion.bebc25.prodflow.payment.dto.VirtualAccountDto;
import net.likelion.bebc25.prodflow.payment.service.PaymentQueryService;
import net.likelion.bebc25.prodflow.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "결제", description = "수주 결제·입금 조회 API")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentQueryService paymentQueryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${portone.webhook-secret:}")
    private String webhookSecret;

    // 현금 계약금 수동 입금 처리
    @Operation(
            summary = "현금 계약금 수동 입금 처리",
            description = "계약금 전액을 현금으로 받았음을 기록하고 수주를 생산대기로 변경한다. "
                    + "작업지시와 생산계획을 함께 생성하며 실패 시 모두 롤백한다. "
                    + "가상계좌 발급·말소와 PortOne 호출은 수행하지 않는다."
    )
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
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        PaymentDto.ManualDepositResponse response =
                paymentService.recordManualDeposit(
                        request,
                        userDetails.getUserId()
                );

        return ResponseEntity.ok(response);
    }

    // 수주별 결제·입금 조회
    // orderId 파라미터가 있을 때만 이 메서드로 매핑
    @Operation(
            summary = "수주별 결제·입금 조회",
            description = "계약금 예정액·누적 입금액·미수금과 결제별 입금 내역·최신 계좌를 반환한다. "
                    + "계약금은 총액의 50%를 원 단위 내림하며, PAID 결제만 누적 입금액에 합산한다. "
                    + "결제가 없으면 빈 목록을 반환하며 결제·계좌를 생성하거나 외부 API를 호출하지 않는다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                    schema = @Schema(
                            implementation = PaymentDto.OrderResponse.class
                    )
            )
    )
    @ApiResponse(responseCode = "400", description = "수주 ID 형식 오류")
    @ApiResponse(responseCode = "401", description = "로그인 필요")
    @ApiResponse(responseCode = "404", description = "수주를 찾을 수 없음")
    @GetMapping(params = "orderId")
    public ResponseEntity<PaymentDto.OrderResponse> getByOrderId(
            @Parameter(description = "수주 ID", required = true)
            @RequestParam Long orderId
    ) {

        PaymentDto.OrderResponse response =
                paymentService.getByOrderId(orderId);

        return ResponseEntity.ok(response);
    }

    // DB에 생성된 READY 결제만 최초 발급 POST 가능. REQUESTED/ISSUE_UNCERTAIN은 조회만 한다.
    @Operation(summary = "미발급 가상계좌 발급 시도/결과 조회")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    @PostMapping("/{paymentId}/issue")
    public ResponseEntity<PaymentDto.IssueResponse> issue(@PathVariable Long paymentId) {
        paymentService.issuePayment(paymentId);
        return ResponseEntity.ok(paymentService.synchronizePayment(paymentId));
    }

    // 기존 계좌 말소를 PortOne에서 확인한 뒤 새 ID로 재발급한다.
    @Operation(summary = "가상계좌 재발급", description = "요청 본문 없이 재발급합니다. 입금기한은 PAYMENT_VA_DUE_DAYS로 자동 계산하고, 기존 계좌 말소가 확인된 뒤 새 계좌를 발급합니다.")
    @ApiResponse(responseCode = "200", description = "새 가상계좌 발급 성공")
    @ApiResponse(responseCode = "409", description = "이미 입금되었거나 이전 계좌 말소 확인 불가")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    @PostMapping("/{paymentId}/reissue")
    public ResponseEntity<PaymentDto.IssueResponse> reissue(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.reissueVirtualAccount(paymentId));
    }

    // 운영자 전용: 발급 응답 유실/웹훅 누락 시 PG 상태를 재조회한다. 새 POST는 보내지 않는다.
    @Operation(summary = "PortOne 결제 상태 재동기화")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    @PostMapping("/{paymentId}/synchronize")
    public ResponseEntity<PaymentDto.IssueResponse> synchronize(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.synchronizePayment(paymentId));
    }

    @Operation(summary = "가상계좌 발급·재발급 이력 조회", description = "현재 계좌 및 말소된 이전 계좌를 최신순으로 반환합니다.")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    @GetMapping("/{paymentId}/virtual-accounts")
    public ResponseEntity<List<VirtualAccountDto.Response>> getVirtualAccountHistory(
            @PathVariable Long paymentId
    ) {
        return ResponseEntity.ok(paymentService.getVirtualAccountHistory(paymentId));
    }

    // PortOne 서버의 요청이므로 JWT가 없다. 서명 검증 후 실제 PG 단건 조회로 확정한다.
    @Operation(summary = "PortOne 서명 검증 웹훅 수신")
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "webhook-id", required = false) String id,
            @RequestHeader(value = "webhook-timestamp", required = false) String timestamp,
            @RequestHeader(value = "webhook-signature", required = false) String signature
    ) {
        verifyWebhook(rawBody, id, timestamp, signature);
        PortOneDto.Webhook webhook;
        try {
            webhook = objectMapper.readValue(rawBody, PortOneDto.Webhook.class);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "웹훅 본문이 올바르지 않습니다.", ex);
        }
        if (webhook.data() != null) {
            paymentService.handleWebhook(webhook);
        }
        return ResponseEntity.noContent().build();
    }

    private void verifyWebhook(String body, String id, String timestamp, String signatures) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PortOne 웹훅 서명 검증 키가 설정되지 않았습니다.");
        }
        if (id == null || id.isBlank() || timestamp == null || signatures == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "웹훅 서명 헤더가 없습니다.");
        }
        long seconds;
        try {
            seconds = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "웹훅 타임스탬프 형식 오류");
        }
        if (Math.abs(Instant.now().getEpochSecond() - seconds) > 300) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "웹훅 유효 시간이 만료되었습니다.");
        }
        try {
            String secret = webhookSecret.startsWith("whsec_") ? webhookSecret.substring(6) : webhookSecret;
            byte[] key = Base64.getDecoder().decode(secret);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] expected = mac.doFinal((id + "." + timestamp + "." + body)
                    .getBytes(StandardCharsets.UTF_8));
            for (String item : signatures.trim().split("\\s+")) {
                if (!item.startsWith("v1,")) continue;
                try {
                    if (MessageDigest.isEqual(expected, Base64.getDecoder().decode(item.substring(3)))) {
                        return;
                    }
                } catch (IllegalArgumentException ignored) {
                    // 올바른 다른 서명이 있을 수 있으므로 다음 서명 확인
                }
            }
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "웹훅 서명을 확인할 수 없습니다.");
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "웹훅 서명 검증 실패");
    }

    // 입금 통계 조회
    @Operation(
            summary = "입금 통계 조회",
            description = "전체 결제 예정 금액, 입금 대기 금액, 입금 완료 금액을 조회합니다."
    )
    @GetMapping("/summary")
    public ResponseEntity<PaymentDto.SummaryResponse> getSummary() {

        return ResponseEntity.ok(
                paymentQueryService.getSummary()
        );
    }

    // 입금 목록 검색 및 페이징
    // orderId 파라미터가 없을 때 목록 조회로 매핑
    @Operation(
            summary = "입금 목록 조회",
            description = "수주번호, 거래처, 결제유형, 결제상태, 기간으로 조회합니다."
    )
    @GetMapping(params = "!orderId")
    public ResponseEntity<Page<PaymentDto.ListResponse>> getPayments(
            @ModelAttribute PaymentDto.SearchRequest condition,
            @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "id",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {

        return ResponseEntity.ok(
                paymentQueryService.getPayments(
                        condition,
                        pageable
                )
        );
    }

    // 입금 상세 조회
    @Operation(
            summary = "입금 상세 조회",
            description = "수주, 결제, 최신 가상계좌 및 입금내역을 조회합니다."
    )
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentDto.DetailResponse> getPaymentDetail(
            @PathVariable Long paymentId
    ) {

        return ResponseEntity.ok(
                paymentQueryService.getPaymentDetail(paymentId)
        );
    }
}
