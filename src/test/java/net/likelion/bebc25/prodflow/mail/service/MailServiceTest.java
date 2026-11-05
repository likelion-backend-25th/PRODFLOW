package net.likelion.bebc25.prodflow.mail.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("실제 메일 발송 테스트: 필요할 때만 해제")
@SpringBootTest
class MailServiceTest {

    @Autowired
    private MailService mailService;

    @Test
    void sendTestMail() {
        mailService.send(
                "dltmddjs98@naver.com",
                "[PRODFLOW] 메일 발송 테스트",
                "Gmail SMTP를 이용한 테스트 메일입니다."
        );
    }
}