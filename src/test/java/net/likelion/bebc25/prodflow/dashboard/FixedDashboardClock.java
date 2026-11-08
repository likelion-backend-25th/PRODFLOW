package net.likelion.bebc25.prodflow.dashboard;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

// 테스트 실행 날짜를 2026년 11월 4일로 고정해 항상 동일한 조건으로 테스트한다.
@TestConfiguration
public class FixedDashboardClock {

    public static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    @Bean
    @Primary
    Clock fixedClock() {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        return Clock.fixed(TODAY.atStartOfDay(zone).toInstant(), zone);
    }
}
