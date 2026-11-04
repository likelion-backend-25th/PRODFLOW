package net.likelion.bebc25.prodflow.holiday.client;

import net.likelion.bebc25.prodflow.global.exception.ExternalApiException;
import net.likelion.bebc25.prodflow.holiday.dto.HolidayApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class HolidayApiClient {
    private static final int PAGE_SIZE = 100;
    private final RestClient restClient;
    private final String baseUrl;
    private final String serviceKey;

    public HolidayApiClient(
            @Value("${holiday.api.base-url}") String baseUrl,
            @Value("${holiday.api.service-key:}") String serviceKey
    ) {
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    // 해당 연도의 모든 페이지를 수집하고 전체 응답이 정상일 때만 반환
    public List<HolidayApiResponse> fetchHolidays(int year) {
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("공휴일 조회 연도는 1~9999여야 합니다.");
        }
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new ExternalApiException("공휴일 API 인증키가 설정되지 않았습니다.");
        }

        List<HolidayApiResponse> holidays = new ArrayList<>();
        Integer expectedTotal = null;
        for (int pageNo = 1; ; pageNo++) {
            Page page = fetchPage(year, pageNo);
            if (page.pageNo() != pageNo || page.totalCount() <= 0 || page.items().isEmpty()
                    || (expectedTotal != null && page.totalCount() != expectedTotal)) {
                throw new ExternalApiException("공휴일 API 응답의 페이지 정보 또는 데이터가 올바르지 않습니다.");
            }
            expectedTotal = page.totalCount();
            holidays.addAll(page.items());
            if (holidays.size() > expectedTotal) {
                throw new ExternalApiException("공휴일 API 응답 건수가 전체 건수를 초과했습니다.");
            }
            if (holidays.size() == expectedTotal) {
                return holidays;
            }
        }
    }

    // 인증키는 디코딩 후 한 번만 인코딩하여 일반키와 인코딩키 모두 지원
    private URI requestUri(int year, int pageNo) {
        String decodedKey = UriUtils.decode(serviceKey.trim(), StandardCharsets.UTF_8);
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/getRestDeInfo")
                .queryParam("serviceKey", "{serviceKey}")
                .queryParam("solYear", String.format("%04d", year))
                .queryParam("numOfRows", PAGE_SIZE)
                .queryParam("pageNo", pageNo)
                .encode()
                .buildAndExpand(decodedKey)
                .toUri();
    }

    // HTTP 실패와 XML 처리 실패를 외부 API 예외로 변환하며 인증키 포함 URL은 노출하지 않음
    private Page fetchPage(int year, int pageNo) {
        try {
            byte[] response = restClient.get()
                    .uri(requestUri(year, pageNo))
                    .accept(MediaType.APPLICATION_XML)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, result) -> {
                        throw new ExternalApiException("공휴일 API가 정상 응답하지 않았습니다: " + result.getStatusCode().value());
                    })
                    .body(byte[].class);
            return parsePage(response);
        } catch (RestClientException e) {
            // 원본 예외에 인증키가 담긴 요청 URI가 포함될 수 있어 전달하지 않는다.
            throw new ExternalApiException("공휴일 API 통신에 실패했습니다.");
        } catch (ParserConfigurationException | SAXException | IOException | IllegalArgumentException | DateTimeException e) {
            throw new ExternalApiException("공휴일 API 응답 형식이 올바르지 않습니다.");
        }
    }

    // 외부 엔티티를 차단한 XML 파서로 응답 코드·페이지 정보·공휴일 항목 추출
    private Page parsePage(byte[] response) throws ParserConfigurationException, SAXException, IOException {
        if (response == null || response.length == 0) {
            throw new ExternalApiException("공휴일 API 응답이 비어 있습니다.");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler());
        Element root = builder.parse(new ByteArrayInputStream(response)).getDocumentElement();

        if (!"response".equals(root.getTagName()) || !"00".equals(text(root, "resultCode"))) {
            throw new ExternalApiException("공휴일 API 인증 또는 서비스 응답 오류가 발생했습니다.");
        }

        int totalCount = Integer.parseInt(text(root, "totalCount"));
        int pageNo = Integer.parseInt(text(root, "pageNo"));
        List<HolidayApiResponse> items = new ArrayList<>();
        NodeList nodes = root.getElementsByTagName("item");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element item = (Element) nodes.item(index);
            LocalDate date = LocalDate.parse(text(item, "locdate"), DateTimeFormatter.BASIC_ISO_DATE);
            items.add(new HolidayApiResponse(date, text(item, "dateName"), text(item, "isHoliday")));
        }
        return new Page(pageNo, totalCount, items);
    }

    // 필수 XML 요소의 텍스트를 읽고 누락된 응답은 실패 처리
    private String text(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagName(name);
        if (nodes.getLength() != 1) {
            throw new ExternalApiException("공휴일 API 응답에 필수 항목이 없거나 중복되었습니다.");
        }
        return nodes.item(0).getTextContent().trim();
    }

    private record Page(int pageNo, int totalCount, List<HolidayApiResponse> items) {
    }
}
