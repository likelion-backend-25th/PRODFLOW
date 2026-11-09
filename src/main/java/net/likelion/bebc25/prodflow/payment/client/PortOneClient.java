package net.likelion.bebc25.prodflow.payment.client;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.payment.config.PortOneProperties;
import net.likelion.bebc25.prodflow.payment.dto.PortOneDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component
@RequiredArgsConstructor
public class PortOneClient {
    private final RestClient restClient;
    private final PortOneProperties properties;

    private String authorization() {
        return "PortOne " + properties.apiSecret();
    }

    // 수기 결제 (가상계좌 발급)
    public void issueInstantPayment(PortOneDto.IssueRequest request) {
        properties.requireConfigured();

        // 발급 API 응답은 {payment:{...}}이며 계좌 상세는 단건 조회에서 가져온다.
        // PortOne 고객 주소는 별도 타입(ONE_LINE/SEPARATED)으로 정의되어 있어
        // 잘못된 addressLine1/addressLine2 형태 대신 선택 필드는 제외한다.
        Map<String, Object> body = new HashMap<String, Object>(Map.of(
                "storeId", properties.storeId(),
                "channelKey", properties.channelKey(),
                "orderName", request.orderName(),
                "amount", Map.of("total", request.amount()),
                "currency", "KRW",
                "customer", Map.of(
                        "name", Map.of("full", request.companyName()),
                        "email", request.email(),
                        "phoneNumber", request.phone()
                ),
                "method", Map.of(
                        "virtualAccount", Map.of(
                                "bank", request.bank(),
                                "expiry", Map.of("dueDate", request.dueDate()),
                                "option", Map.of("type", "NORMAL"))
                ),
                "noticeUrls", List.of(properties.webhookUrl())
        ));

        try {
            restClient.post().uri("/payments/{id}/instant", request.paymentId())
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .header("Idempotency-Key", "\"" + request.idempotencyKey() + "\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientResponseException e) {
            throw e;
        } catch (RestClientException e) {
            throw new RestClientException(
                    "PortOne 발급 응답을 받지 못했습니다. 중복 발급을 피하려면 이 결제의 상태를 먼저 조회해 주세요."
            );
        }
    }

    /**
     * PortOne V2 POST /payments/{paymentId}/virtual-account/close.
     * 정상 응답의 closedAt을 받아야만 재발급 다음 단계로 진행한다.
     * 이미 입금된 결제 취소/환불 API와 다른 엔드포인트다.
     */
    public PortOneDto.CloseVirtualAccountResponse closeVirtualAccount(String paymentId) {
        properties.requireConfigured();
        return restClient.post()
                .uri(uri -> uri.path("/payments/{id}/virtual-account/close")
                        .queryParam("storeId", properties.storeId())
                        .build(paymentId))
                .header(HttpHeaders.AUTHORIZATION, authorization())
                .retrieve()
                .body(PortOneDto.CloseVirtualAccountResponse.class);
    }

    public PortOneDto.PaymentSnapshot getPayment(String paymentId) {
        properties.requireConfigured();

        try {
            return restClient.get()
                    .uri(uri -> uri.path("/payments/{id}")
                            .queryParam("storeId", properties.storeId())
                            .build(paymentId)
                    )
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .retrieve()
                    .body(PortOneDto.PaymentSnapshot.class);

        } catch (RestClientResponseException e) {
            throw e;
        } catch (RestClientException e) {
            throw new RestClientException("PortOne 결제 상태를 가져오지 못했습니다. 잠시 후 다시 조회해 주세요.");
        }
    }
}
