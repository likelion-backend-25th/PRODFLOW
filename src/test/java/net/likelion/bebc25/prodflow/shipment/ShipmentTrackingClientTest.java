package net.likelion.bebc25.prodflow.shipment;

import net.likelion.bebc25.prodflow.shipment.client.ShipmentTrackingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShipmentTrackingClientTest {

    @Test
    @DisplayName("API 키가 없으면 외부 배송조회 호출 전에 IllegalStateException을 던진다")
    void throwsWhenApiKeyIsBlank() {
        ShipmentTrackingClient client = new ShipmentTrackingClient(
                "https://info.sweettracker.co.kr",
                ""
        );

        assertThatThrownBy(() -> client.fetchTrackingInfo("04", "123456789012"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("배송조회 API 키");
    }
}
