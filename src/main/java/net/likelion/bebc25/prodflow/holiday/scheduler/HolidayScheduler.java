package net.likelion.bebc25.prodflow.holiday.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.likelion.bebc25.prodflow.holiday.service.HolidaySyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "holiday.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class HolidayScheduler {
    private final HolidaySyncService holidaySyncService;

    // 매월 초 올해·다음 해 공휴일 수집
    @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Seoul")
    public void syncHolidays() {
        int currentYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        for (int year : new int[]{currentYear, currentYear + 1}) {
            try {
                int addedCount = holidaySyncService.importHolidays(year);
                log.info("공휴일 월간 수집 완료: year={}, addedCount={}", year, addedCount);
            } catch (RuntimeException ex) {
                log.error("공휴일 월간 수집 실패: year={}. 다른 연도 수집을 계속합니다.", year, ex);
            }
        }
    }
}
