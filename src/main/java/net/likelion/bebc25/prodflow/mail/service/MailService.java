package net.likelion.bebc25.prodflow.mail.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public void send(String recipientEmail, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail); // 발신자
        message.setTo(recipientEmail); // 수신자
        message.setSubject(subject); // 제목
        message.setText(content); // 본문

        mailSender.send(message);
    }
}
