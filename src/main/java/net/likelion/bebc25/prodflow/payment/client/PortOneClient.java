package net.likelion.bebc25.prodflow.payment.client;

import net.likelion.bebc25.prodflow.payment.client.dto.PortOneDto;
import net.likelion.bebc25.prodflow.payment.properties.PortOneProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 외부 PortOne V2 HTTP API 연동 전용 (Payment 엔티티/DB 상태는 수정하지 않음).
 */
@Component
public class PortOneClient {
    private final RestClient restClient;
    private final PortOneProperties properties;

    public PortOneClient(
            @Qualifier("portOneRestClient") RestClient restClient,
            PortOneProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * POST /payments/{paymentId}/instant
     * 재요청 시 반드시 같은 paymentId / idempotencyKey / 요청 Body를 사용해야 합니다.
     */
    public PortOneDto.IssueResult issueVirtualAccount(PortOneDto.IssueRequest request) {
        properties.requireConfigured();
        Map<String, Object> body = Map.of(
                "storeId", properties.storeId(),
                "channelKey", properties.channelKey(),
                "orderName", request.orderName(),
                "amount", Map.of("total", request.amount()),
                "currency", "KRW",
                "customer", Map.of(
                        "name", Map.of("full", request.customerName()),
                        "email", request.customerEmail(),
                        "phoneNumber", request.customerPhone()
                ),
                "method", Map.of("virtualAccount", Map.of(
                        "bank", request.bank(),
                        "expiry", Map.of("dueDate", request.expiresAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
                        "option", Map.of("type", "NORMAL")
                ))
        );
        final Map<?, ?> json;
        try {
            json = restClient.post()
                    .uri("/payments/{paymentId}/instant", request.paymentId())
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    // PortOne V2는 RFC 8941 문자열 형식(큰따옴표 포함)을 요구합니다.
                    .header("Idempotency-Key", "\"" + request.idempotencyKey() + "\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            throw apiFailure("가상계좌 발급", e);
        } catch (ResourceAccessException e) {
            throw new PortOneClientException(
                    "PortOne 가상계좌 발급 응답을 확인하지 못했습니다. 동일 결제 ID로 조회 후 재시도하세요.",
                    true, e);
        }

        // POST 응답: {"payment": {"id": ..., "status": "VIRTUAL_ACCOUNT_ISSUED", ...}}
        Map<?, ?> payment = json != null && json.get("payment") instanceof Map<?, ?> map ? map : null;
        PortOneDto.PaymentSnapshot snapshot = PortOneDto.PaymentSnapshot.fromMap(payment);
        requireMatched(snapshot, request.paymentId(), request.amount());
        if (!"VIRTUAL_ACCOUNT_ISSUED".equals(snapshot.status())) {
            throw new IllegalStateException("발급 응답의 PortOne 상태가 예상과 다릅니다: " + snapshot.status());
        }
        if (snapshot.methodType() == null || snapshot.bankCode() == null || snapshot.accountNumber() == null || snapshot.expiredAt() == null) {
            // 결제수단 정보가 생략될 수 있으므로 결제 단건 조회로 재확인합니다.
            snapshot = getPayment(request.paymentId());
            requireMatched(snapshot, request.paymentId(), request.amount());
        }
        return snapshot.toIssueResult();
    }

    /**
     * GET /payments/{paymentId}
     * 발급 실패/타임아웃 후 정합성 확인 및 추후 입금 웹훅 검증에 사용합니다.
     */
    public PortOneDto.PaymentSnapshot getPayment(String paymentId) {
        properties.requireConfigured();
        if (paymentId == null || !paymentId.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("PortOne 결제 ID가 올바르지 않습니다.");
        }
        final Map<?, ?> json;
        try {
            // GET 결제 단건 조회 응답은 payment 래퍼 없이 결제 객체 자체입니다.
            json = restClient.get()
                    .uri("/payments/{paymentId}", paymentId)
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            throw apiFailure("결제 조회", e);
        } catch (ResourceAccessException e) {
            throw new PortOneClientException("PortOne 결제 조회 응답을 확인하지 못했습니다.", true, e);
        }
        PortOneDto.PaymentSnapshot snapshot = PortOneDto.PaymentSnapshot.fromMap(json);
        if (!paymentId.equals(snapshot.paymentId())) {
            throw new IllegalStateException("PortOne 결제 조회 결과의 결제 ID가 일치하지 않습니다.");
        }
        return snapshot;
    }

    private void requireMatched(PortOneDto.PaymentSnapshot snapshot, String paymentId, long amount) {
        if (!paymentId.equals(snapshot.paymentId())) {
            throw new IllegalStateException("PortOne 결제 ID가 요청과 다릅니다.");
        }
        if (snapshot.totalAmount() == null || snapshot.totalAmount() != amount) {
            throw new IllegalStateException("PortOne 결제 금액이 요청 금액과 다릅니다.");
        }
    }

    private String authorization() {
        return "PortOne " + properties.apiSecret();
    }

    private static PortOneClientException apiFailure(String operation, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        // 5xx/409는 처리 결과가 불확실할 수 있어 반드시 단건 조회로 확인해야 합니다.
        boolean uncertain = status >= 500 || status == 409;
        // 원문 오류 body나 Authorization 헤더를 그대로 로그/응답으로 노출하지 않습니다.
        return new PortOneClientException("PortOne " + operation + " 실패 (HTTP " + status + ")", uncertain, e);
    }
}
