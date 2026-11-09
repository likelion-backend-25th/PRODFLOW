package net.likelion.bebc25.prodflow.mail.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.MessagingException;

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

    // PDF 첨부 이메일 발송
    public void sendAttachment(String recipientEmail, String subject, String content, String filename, byte[] pdf) {
        var message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(content);
            helper.addAttachment(filename, new ByteArrayResource(pdf), "application/pdf");
        } catch (MessagingException ex) {
            throw new MailPreparationException("PDF 첨부 이메일을 구성하지 못했습니다.", ex);
        }
        mailSender.send(message);
    }
}
