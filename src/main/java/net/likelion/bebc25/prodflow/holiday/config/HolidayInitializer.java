package net.likelion.bebc25.prodflow.holiday.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.likelion.bebc25.prodflow.holiday.service.HolidaySyncService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 설정 활성화 시 서버 시작마다 올해·다음 해 공휴일 수집
 * 기존 날짜 제외, 신규 날짜만 추가
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "holiday.initialization.enabled", havingValue = "true")
public class HolidayInitializer implements ApplicationRunner {
    private final HolidaySyncService holidaySyncService;

    @Override
    public void run(ApplicationArguments args) {
        // 한국 시간 기준 수집 연도 결정
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

        for (int year : new int[]{currentYear, currentYear + 1}) {
            try {
                int addedCount = holidaySyncService.importHolidays(year);
                log.info("공휴일 초기 수집 완료: year={}, addedCount={}", year, addedCount);
            } catch (Exception e) {
                // 실패 시 다음 연도 수집 및 서버 시작 계속
                // 자동 재시도 없음, 관리자 API로 재수집 가능
                log.error("공휴일 초기 수집 실패: year={}", year, e);
            }
        }
    }
}
