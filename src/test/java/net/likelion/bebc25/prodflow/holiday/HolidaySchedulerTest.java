package net.likelion.bebc25.prodflow.holiday;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import net.likelion.bebc25.prodflow.global.exception.ExternalApiException;
import net.likelion.bebc25.prodflow.holiday.scheduler.HolidayScheduler;
import net.likelion.bebc25.prodflow.holiday.service.HolidaySyncService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class HolidaySchedulerTest {
    private final HolidaySyncService service = mock(HolidaySyncService.class);
    private final HolidayScheduler scheduler = new HolidayScheduler(service);
    private final int year = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

    @Test
    void collectsCurrentAndNextYearUsingExistingService() {
        scheduler.syncHolidays();
        var order = inOrder(service);
        order.verify(service).importHolidays(year);
        order.verify(service).importHolidays(year + 1);
        verifyNoMoreInteractions(service);
    }

    @Test
    void firstYearFailureDoesNotSkipNextYearAndLaterExecutionCanSucceed() {
        when(service.importHolidays(year)).thenThrow(new ExternalApiException("외부 API 실패")).thenReturn(1);
        assertThatCode(scheduler::syncHolidays).doesNotThrowAnyException();
        assertThatCode(scheduler::syncHolidays).doesNotThrowAnyException();
        verify(service, times(2)).importHolidays(year);
        verify(service, times(2)).importHolidays(year + 1);
    }

    @Test
    void bothYearFailuresAreContainedWithinScheduledExecution() {
        when(service.importHolidays(year)).thenThrow(new ExternalApiException("올해 수집 실패"));
        when(service.importHolidays(year + 1)).thenThrow(new ExternalApiException("다음 해 수집 실패"));
        assertThatCode(scheduler::syncHolidays).doesNotThrowAnyException();
        verify(service).importHolidays(year);
        verify(service).importHolidays(year + 1);
    }

    @Test
    void cronRunsAtStartOfEachMonthInKoreanTimeAcrossYearBoundary() throws Exception {
        var schedule = HolidayScheduler.class.getMethod("syncHolidays").getAnnotation(Scheduled.class);
        var zone = ZoneId.of(schedule.zone());
        var cron = CronExpression.parse(schedule.cron());
        var endOfYear = ZonedDateTime.of(2026, 12, 31, 23, 59, 59, 0, ZoneId.of("Asia/Seoul"));
        var january = cron.next(endOfYear.withZoneSameInstant(zone));
        assertThat(january).isEqualTo(ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZoneId.of("Asia/Seoul")));
        assertThat(cron.next(january))
                .isEqualTo(ZonedDateTime.of(2027, 2, 1, 0, 0, 0, 0, ZoneId.of("Asia/Seoul")));
    }

    @Test
    void monthlyCollectionCanBeDisabledIndependentlyOfInitialization() {
        var runner = new ApplicationContextRunner()
                .withBean(HolidaySyncService.class, () -> service)
                .withUserConfiguration(HolidayScheduler.class);
        runner.run(context -> {
            assertThat(context.getBeansOfType(HolidayScheduler.class)).hasSize(1);
            verifyNoInteractions(service);
        });
        runner.withPropertyValues("holiday.scheduling.enabled=false", "holiday.initialization.enabled=true")
                .run(context -> {
                    assertThat(context.getBeansOfType(HolidayScheduler.class)).isEmpty();
                    verifyNoInteractions(service);
                });
    }
}
