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

    private String authorization() {
        return "PortOne " + properties.apiSecret();
    }
}
