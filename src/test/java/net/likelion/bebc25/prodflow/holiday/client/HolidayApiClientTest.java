package net.likelion.bebc25.prodflow.holiday.client;

import com.sun.net.httpserver.HttpServer;
import net.likelion.bebc25.prodflow.global.exception.ExternalApiException;
import net.likelion.bebc25.prodflow.holiday.client.HolidayApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

class HolidayApiClientTest {
    private HttpServer server;
    private HolidayApiClient client;
    private String baseUrl;
    private String body;
    private int status;
    private Function<Integer, String> pageBody;
    private final List<Map<String, String>> queries = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        status = 200;
        body = page(1, 1, item("20260505", "어린이날", "Y"));
        pageBody = null;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            Map<String, String> query = Arrays.stream(exchange.getRequestURI().getRawQuery().split("&"))
                    .map(value -> value.split("=", 2))
                    .collect(Collectors.toMap(pair -> pair[0],
                            pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
            queries.add(query);
            String response = pageBody == null ? body : pageBody.apply(Integer.parseInt(query.get("pageNo")));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/xml; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new HolidayApiClient(baseUrl, "test+key/value=");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void requestsPublicDataApiWithYearAndDecodesHolidayFields() {
        var holidays = client.fetchHolidays(2026);

        assertThat(paths).containsExactly("/getRestDeInfo");
        assertThat(queries.getFirst()).containsEntry("serviceKey", "test+key/value=")
                .containsEntry("solYear", "2026").containsEntry("numOfRows", "100").containsEntry("pageNo", "1");
        assertThat(holidays).singleElement().satisfies(holiday -> {
            assertThat(holiday.date()).isEqualTo(LocalDate.of(2026, 5, 5));
            assertThat(holiday.name()).isEqualTo("어린이날");
            assertThat(holiday.isHoliday()).isEqualTo("Y");
        });
    }

    @Test
    void acceptsAlreadyEncodedKeyWithoutDoubleEncoding() {
        new HolidayApiClient(baseUrl, "test%2Bkey%2Fvalue%3D").fetchHolidays(2026);
        assertThat(queries.getFirst()).containsEntry("serviceKey", "test+key/value=");
    }

    @Test
    void collectsEveryPageIncludingSingleItemFinalPage() {
        pageBody = number -> number == 1
                ? page(1, 3, item("20260101", "1월1일", "Y") + item("20260301", "삼일절", "Y"))
                : page(2, 3, item("20260505", "어린이날", "Y"));

        assertThat(client.fetchHolidays(2026)).extracting(holiday -> holiday.name())
                .containsExactly("1월1일", "삼일절", "어린이날");
        assertThat(queries).extracting(query -> query.get("pageNo")).containsExactly("1", "2");
    }

    @Test
    void rejectsHttpFailureWithoutLeakingKeyOrRequestUri() {
        status = 503;
        assertThatThrownBy(() -> client.fetchHolidays(2026))
                .isInstanceOf(ExternalApiException.class).hasMessageContaining("503")
                .hasMessageNotContaining("test+key").hasNoCause();
    }

    @Test
    void rejectsServiceErrorEvenWithHttp200() {
        body = "<response><header><resultCode>30</resultCode><resultMsg>ERROR</resultMsg></header></response>";
        assertThatThrownBy(() -> client.fetchHolidays(2026)).isInstanceOf(ExternalApiException.class);
        body = "<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>";
        assertThatThrownBy(() -> client.fetchHolidays(2026)).isInstanceOf(ExternalApiException.class);
    }

    @Test
    void rejectsEmptyMalformedMissingFieldsAndInvalidDates() {
        for (String invalid : List.of(" ", "not-xml", page(1, 0, ""),
                page(1, 1, item("20260230", "잘못된 날짜", "Y")),
                page(1, 1, "<item><dateName>누락</dateName></item>"))) {
            body = invalid;
            assertThatThrownBy(() -> client.fetchHolidays(2026)).isInstanceOf(ExternalApiException.class);
        }
    }

    @Test
    void rejectsPartialResultsWhenLaterPageFails() {
        pageBody = number -> number == 1 ? page(1, 2, item("20260101", "새해", "Y")) : "invalid";
        assertThatThrownBy(() -> client.fetchHolidays(2026)).isInstanceOf(ExternalApiException.class);
    }

    @Test
    void rejectsRepeatedPageOrChangedTotalCount() {
        pageBody = number -> page(1, 2, item("20260101", "새해", "Y"));
        assertThatThrownBy(() -> client.fetchHolidays(2026)).isInstanceOf(ExternalApiException.class);
        pageBody = number -> page(number, number == 1 ? 2 : 3, item("20260101", "새해", "Y"));
        assertThatThrownBy(() -> client.fetchHolidays(2026)).isInstanceOf(ExternalApiException.class);
    }

    @Test
    void rejectsDoctypeAndExternalEntities() {
        body = "<!DOCTYPE response [<!ENTITY external SYSTEM 'file:///not-to-be-read'>]>"
                + page(1, 1, item("20260101", "&external;", "Y"));
        assertThatThrownBy(() -> client.fetchHolidays(2026)).isInstanceOf(ExternalApiException.class);
    }

    @Test
    void rejectsMissingKeyAndInvalidYearBeforeCallingApi() {
        assertThatThrownBy(() -> new HolidayApiClient(baseUrl, "").fetchHolidays(2026))
                .isInstanceOf(ExternalApiException.class).hasMessageContaining("인증키");
        assertThatThrownBy(() -> client.fetchHolidays(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(queries).isEmpty();
    }

    private String page(int number, int total, String items) {
        return "<response><header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>"
                + "<body><items>" + items + "</items><numOfRows>100</numOfRows><pageNo>" + number
                + "</pageNo><totalCount>" + total + "</totalCount></body></response>";
    }

    private String item(String date, String name, String isHoliday) {
        return "<item><dateKind>01</dateKind><dateName>" + name + "</dateName><isHoliday>" + isHoliday
                + "</isHoliday><locdate>" + date + "</locdate><seq>1</seq></item>";
    }
}
