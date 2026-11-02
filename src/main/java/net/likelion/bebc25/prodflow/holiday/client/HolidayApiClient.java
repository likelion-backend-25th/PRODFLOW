package net.likelion.bebc25.prodflow.holiday.client;

import net.likelion.bebc25.prodflow.holiday.dto.HolidayApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Component
public class HolidayApiClient {
    private final RestClient restClient;

    public HolidayApiClient(@Value("${holiday.api.base-url:https://date.nager.at}") String baseUrl) {
        // 설정 주소 사용, 기본값은 Nager.Date
        // 테스트 시 테스트 서버 주소로 대체
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 연결·응답 읽기 대기 시간 개별 제한
        // 요청 전체 시간 제한과 별개
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * 연도별 한국 공휴일 조회
     * JSON 배열 응답, 페이지 순회 없음
     *
     * @param year 조회 연도(1~9999)
     * @return 주말 공휴일 포함 응답 목록, 중복 제거 전
     * @throws IllegalArgumentException 허용 연도 범위 초과
     * @throws IllegalStateException    호출 실패·비정상 상태·변환 실패·빈 응답
     */
    public List<HolidayApiResponse> fetchHolidays(int year) {
        // API 호출 전 연도 검증
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("공휴일 조회 연도는 1~9999여야 합니다.");
        }
        try {
            // 한국(KR) 지정 및 연도 경로 치환
            // JSON 응답 요청, body()에서 호출·DTO 변환
            HolidayApiResponse[] response = restClient.get()
                    .uri("/api/v3/PublicHolidays/{year}/KR", year)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    // 2xx 이외 응답은 실패 처리
                    .onStatus(status -> !status.is2xxSuccessful(), (request, result) -> {
                        throw new IllegalStateException("공휴일 API가 정상 응답하지 않았습니다: " + result.getStatusCode());
                    })
                    .body(HolidayApiResponse[].class);
            // 빈 응답은 수집 실패 처리
            if (response == null || response.length == 0) {
                throw new IllegalStateException("공휴일 API 응답이 비어 있습니다.");
            }
            // 고정 크기 List로 변환
            return Arrays.asList(response);
        } catch (RestClientException e) {
            // 통신·JSON 변환 오류를 수집 실패 예외로 변환
            // 원인 예외 보존
            throw new IllegalStateException("공휴일 API 조회에 실패했습니다.", e);
        }
    }
}
