package net.likelion.bebc25.prodflow.shipment.client;

import net.likelion.bebc25.prodflow.shipment.dto.TrackingApiDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;


@Component
public class ShipmentTrackingClient {

    private final RestClient restClient;
    private final String apiKey;

    public ShipmentTrackingClient(
            @Value("${shipment.tracking.api.base-url:https://info.sweettracker.co.kr}") String baseUrl,
            @Value("${shipment.tracking.api.key:}") String apiKey
    ) {
        this.apiKey = apiKey;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }


    public TrackingApiDto fetchTrackingInfo(String carrierCode, String trackingNumber) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "배송조회 API 키가 없습니다. shipment.tracking.api.key 또는 환경변수 SHIPMENT_TRACKING_API_KEY 를 설정하세요."
            );
        }
        if (carrierCode == null || carrierCode.isBlank()) {
            throw new IllegalArgumentException("택배사 코드(t_code)는 필수입니다.");
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("운송장 번호(t_invoice)는 필수입니다.");
        }

        Map<String, String> body = Map.of(
                "t_key", apiKey,
                "t_code", carrierCode.trim(),
                "t_invoice", trackingNumber.trim()
        );

        try {
            TrackingApiDto response = restClient.post()
                    .uri("/api/v1/trackingInfo")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, result) -> {
                        throw new IllegalStateException(
                                "배송조회 API 오류 상태: " + result.getStatusCode()
                        );
                    })
                    .body(TrackingApiDto.class);

            if (response == null) {
                throw new IllegalStateException("배송조회 API 응답이 비어 있습니다.");
            }
            return response;
        } catch (RestClientException e) {
            throw new IllegalStateException("배송조회 API 호출에 실패했습니다.", e);
        }
    }
}
