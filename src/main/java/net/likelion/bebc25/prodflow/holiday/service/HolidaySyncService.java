package net.likelion.bebc25.prodflow.holiday.service;

import net.likelion.bebc25.prodflow.global.exception.ExternalApiException;
import net.likelion.bebc25.prodflow.holiday.client.HolidayApiClient;
import net.likelion.bebc25.prodflow.holiday.dto.HolidayApiResponse;
import net.likelion.bebc25.prodflow.holiday.entity.Holiday;
import net.likelion.bebc25.prodflow.holiday.repository.HolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 외부 공휴일 검증 및 신규 날짜 등록
 * 기존 데이터 유지, 호출자가 실행 시점 결정
 */
@Service
public class HolidaySyncService {
    private final HolidayApiClient holidayApiClient;
    private final HolidayRepository holidayRepository;
    private final TransactionTemplate transactionTemplate;

    public HolidaySyncService(HolidayApiClient apiClient, HolidayRepository repository,
                              PlatformTransactionManager transactionManager) {
        this.holidayApiClient = apiClient;
        this.holidayRepository = repository;

        // execute() 내부 DB 작업만 트랜잭션 적용
        // API 응답 수신 후 저장 트랜잭션 시작
        this.transactionTemplate = new TransactionTemplate(transactionManager);

        // 호출자 트랜잭션 중단 후 별도 쓰기 트랜잭션 사용
        // 공휴일 커밋 후 호출자 롤백과 무관하게 유지
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 연도별 공휴일 수집 및 신규 날짜 일괄 등록
     * 전체 응답 검증 후 저장
     *
     * @param year 수집할 연도(1~9999)
     * @return 신규 날짜 수, 추가 대상이 없으면 0
     * @throws IllegalArgumentException 허용 연도 범위 초과
     * @throws ExternalApiException     외부 조회 실패·잘못된 응답
     */
    public int importHolidays(int year) {
        // API 호출·DB 조회에 사용할 연도 검증
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("공휴일 조회 연도는 1~9999여야 합니다.");
        }

        // 저장 트랜잭션 전 API 호출 및 응답 검증
        List<HolidayApiResponse> response = holidayApiClient.fetchHolidays(year);
        if (response == null || response.isEmpty()) {
            throw new ExternalApiException("공휴일 API 응답이 비어 있습니다.");
        }

        // 날짜 기준 중복 제거
        // API 응답의 날짜 등장 순서 유지
        Map<LocalDate, String> holidays = new LinkedHashMap<>();
        for (HolidayApiResponse item : response) {
            // 연도·필수값·이름 길이·공휴일 여부 검증
            // 잘못된 항목 발견 시 전체 수집 중단
            if (item == null || item.date() == null || item.date().getYear() != year
                    || item.name() == null || item.name().isBlank() || item.name().length() > 255
                    || !("Y".equals(item.isHoliday()) || "N".equals(item.isHoliday()))) {
                throw new ExternalApiException("공휴일 API 응답의 날짜·이름·공휴일 여부가 올바르지 않습니다.");
            }
            // 공공기관 휴일 여부가 Y인 날짜만 저장
            // 주말과 겹친 공휴일 포함
            // 일반 주말은 미저장, 납기 계산에서 요일 판단
            if (!"Y".equals(item.isHoliday())) {
                continue;
            }
            // 같은 날짜의 첫 공휴일 이름 유지
            holidays.putIfAbsent(item.date(), item.name());
        }
        return transactionTemplate.execute(status -> {
            // 요청 연도의 기존 날짜 일괄 조회
            // Set으로 기존 날짜 포함 여부 확인
            Set<LocalDate> existingDates = new HashSet<>();
            holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                    .forEach(holiday -> existingDates.add(holiday.getHolidayDate()));

            // 기존 날짜 제외, 순차 재실행 시 중복 방지
            // 동시 실행 중복은 DB UNIQUE 제약으로 차단
            // 충돌 트랜잭션 실패, 자동 재시도 없음
            List<Holiday> newHolidays = holidays.entrySet().stream()
                    .filter(entry -> !existingDates.contains(entry.getKey()))
                    .map(entry -> new Holiday(entry.getKey(), entry.getValue()))
                    .toList();

            // flush로 INSERT·제약조건 검사, 커밋과 별개
            // 저장 실패 시 신규 등록 전체 롤백, 기존 행 유지
            // execute() 커밋 완료 후 추가 건수 반환
            holidayRepository.saveAllAndFlush(newHolidays);
            return newHolidays.size();
        });
    }
}
