package net.likelion.bebc25.prodflow.quotation;

import java.time.Duration;

import net.likelion.bebc25.prodflow.quotation.scheduler.QuotationExpirationScheduler;
import net.likelion.bebc25.prodflow.quotation.service.QuotationStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotationExpirationSchedulerTest {
    private final QuotationStatusService service = mock(QuotationStatusService.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(QuotationStatusService.class, () -> service)
            .withUserConfiguration(QuotationExpirationScheduler.class);

    @Test
    void readyEventRunsCatchUpAndFailureDoesNotPreventLaterExecution() {
        when(service.expireQuotations()).thenThrow(new IllegalStateException("일시적 DB 실패")).thenReturn(2);
        runner.run(context -> {
            context.publishEvent(new ApplicationReadyEvent(
                    new SpringApplication(), new String[0], context.getSourceApplicationContext(), Duration.ZERO));
            context.getBean(QuotationExpirationScheduler.class).expireQuotations();
            verify(service, times(2)).expireQuotations();
            assertThat(context.isRunning()).isTrue();
        });
    }

    @Test
    void schedulerCanBeDisabledForSeedAndIsolatedTests() {
        runner.withPropertyValues("quotation.expiration.enabled=false").run(context ->
                assertThat(context.getBeansOfType(QuotationExpirationScheduler.class)).isEmpty());
    }
}
