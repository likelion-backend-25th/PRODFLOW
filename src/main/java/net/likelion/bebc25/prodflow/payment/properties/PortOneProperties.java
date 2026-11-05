package net.likelion.bebc25.prodflow.payment.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;

@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
        String apiSecret,
        String storeId,
        String channelKey,
        String webhookUrl
) {
    public boolean isConfigured() {
        return StringUtils.hasText(apiSecret)
                && StringUtils.hasText(storeId)
                && StringUtils.hasText(channelKey)
                && StringUtils.hasText(webhookUrl);
    }

    public void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalArgumentException("PortOne 연동 설정이 필요합니다. .env와 application.yaml를 확인해 주세요.");
        }

        URI uri = java.net.URI.create(webhookUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("portone.webhook-url에는 외부에서 접근 가능한 HTTPS 웹훅 주소를 입력해 주세요.");
        }
    }
}
