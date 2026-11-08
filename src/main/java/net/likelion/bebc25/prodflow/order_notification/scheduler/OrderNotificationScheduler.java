package net.likelion.bebc25.prodflow.order_notification.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.likelion.bebc25.prodflow.order_notification.service.OrderNotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationScheduler {

    private final OrderNotificationService orderNotificationService;

    // 한국 시간 자정에 납기 임박·지연 알림을 저장한다.
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void notifyDueOrders() {
        try {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            int created = orderNotificationService.createDueNotifications(today);
            log.info("납기 알림 생성 완료. created={}", created);
        } catch (RuntimeException ex) {
            log.error("납기 알림 생성 실패. 다음 실행에서 다시 처리합니다.", ex);
        }
    }
}
