package net.likelion.bebc25.prodflow.holiday;

import com.sun.net.httpserver.HttpServer;
import net.likelion.bebc25.prodflow.holiday.client.HolidayApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HolidayApiClientTest {
    private HttpServer server;
    private HolidayApiClient client;
    private final AtomicReference<String> path = new AtomicReference<>();
    private String body;
    private int status;

    @BeforeEach
    void setUp() throws Exception {
        status = 200;
        body = "[]";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        client = new HolidayApiClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void mapsNagerResponseAndRequestsKoreanHolidaysForGivenYear() {
        body = """
                [{"date":"2026-05-05","localName":"어린이날","name":"Children's Day",
                  "countryCode":"KR","global":true,"counties":null,"types":["Public"]}]
                """;
        var holidays = client.fetchHolidays(2026);
        assertThat(path.get()).isEqualTo("/api/v3/PublicHolidays/2026/KR");
        assertThat(holidays).singleElement().satisfies(holiday -> {
            assertThat(holiday.date()).isEqualTo(LocalDate.of(2026, 5, 5));
            assertThat(holiday.localName()).isEqualTo("어린이날");
            assertThat(holiday.types()).containsExactly("Public");
        });
    }

    @Test
    void rejectsHttpFailure() {
        status = 503;
        assertThatThrownBy(() -> client.fetchHolidays(2026)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsEmptyOrMalformedResponse() {
        assertThatThrownBy(() -> client.fetchHolidays(2026)).isInstanceOf(IllegalStateException.class);
        body = "not-json";
        assertThatThrownBy(() -> client.fetchHolidays(2026)).isInstanceOf(IllegalStateException.class);
    }
}
