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
    public PortOneDto.IssueResponse issueInstantPayment(PortOneDto.IssueRequest request) {
        properties.requireConfigured();

        // 발급 응답은 요약 정보뿐이므로 계좌 정보는 이어지는 단건 조회에서 가져옵니다.
        Map<String, Object> body = new HashMap<String, Object>(Map.of(
                "storeId", properties.storeId(),
                "channelKey", properties.channelKey(),
                "orderName", request.orderName(),
                "amount", Map.of("total", request.amount()),
                "currency", "KRW",
                "customer", Map.of(
                        "name", Map.of("full", request.companyName()),
                        "email", request.email(),
                        "phoneNumber", request.phone(),
                        "address", Map.of(
                                "addressLine1", request.roadAddress(),
                                "addressLine2", request.detailAddress()
                        )
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
            return restClient.post().uri("/payments/{id}/instant", request.paymentId())
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .header("Idempotency-Key", "\"" + request.idempotencyKey() + "\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(PortOneDto.IssueResponse.class);

        } catch (RestClientResponseException e) {
            throw e;
        } catch (RestClientException e) {
            throw new RestClientException(
                    "PortOne 발급 응답을 받지 못했습니다. 중복 발급을 피하려면 이 결제의 상태를 먼저 조회해 주세요."
            );
        }
    }
}
