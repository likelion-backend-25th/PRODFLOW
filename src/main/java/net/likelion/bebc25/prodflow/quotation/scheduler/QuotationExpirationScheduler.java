package net.likelion.bebc25.prodflow.quotation.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.likelion.bebc25.prodflow.quotation.service.QuotationStatusService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "quotation.expiration", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class QuotationExpirationScheduler {
    private final QuotationStatusService quotationStatusService;

    // 한국 시간 자정 이후 및 서버 시작 시 만료 처리
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @EventListener(ApplicationReadyEvent.class)
    public void expireQuotations() {
        try {
            int count = quotationStatusService.expireQuotations();
            log.info("견적 자동 만료 처리 완료. count={}", count);
        } catch (RuntimeException ex) {
            log.error("견적 자동 만료 처리 실패. 다음 실행에서 만료 대상을 다시 처리합니다.", ex);
        }
    }
}
