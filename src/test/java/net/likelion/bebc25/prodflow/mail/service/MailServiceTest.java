package net.likelion.bebc25.prodflow.mail.service;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class MailServiceTest {

    @Autowired
    private MailService mailService;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    @DisplayName("외부 SMTP 없이 메일 내용 검증")
    void sendTestMail() {
        mailService.send(
                "recipient@example.com",
                "[PRODFLOW] 메일 발송 테스트",
                "테스트 메일입니다."
        );

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("test@prodflow.local");
        assertThat(message.getTo()).containsExactly("recipient@example.com");
        assertThat(message.getSubject()).isEqualTo("[PRODFLOW] 메일 발송 테스트");
        assertThat(message.getText()).isEqualTo("테스트 메일입니다.");
    }

    @Test
    @DisplayName("수신자·본문·PDF 첨부 내용 검증")
    void sendAttachment() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        byte[] pdf = "%PDF-1.7 test".getBytes(StandardCharsets.UTF_8);

        mailService.sendAttachment("client@example.com", "견적서", "첨부합니다.", "QT-123.pdf", pdf);

        verify(mailSender).send(message);
        message.saveChanges();
        assertThat(message.getFrom()[0].toString()).isEqualTo("test@prodflow.local");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("client@example.com");
        assertThat(message.getSubject()).isEqualTo("견적서");
        Multipart multipart = (Multipart) message.getContent();
        Multipart content = (Multipart) multipart.getBodyPart(0).getContent();
        BodyPart body = content.getBodyPart(0);
        assertThat(body.getContent().toString()).isEqualTo("첨부합니다.");
        BodyPart attachment = multipart.getBodyPart(multipart.getCount() - 1);
        assertThat(attachment.getFileName()).isEqualTo("QT-123.pdf");
        assertThat(attachment.isMimeType("application/pdf")).isTrue();
        assertThat(attachment.getInputStream().readAllBytes()).containsExactly(pdf);
    }

    @Test
    @DisplayName("메일 전송 실패 예외 전달")
    void sendFailure() {
        doThrow(new MailSendException("메일 전송 실패")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> mailService.send("recipient@example.com", "제목", "본문"))
                .isInstanceOf(MailSendException.class).hasMessageContaining("메일 전송 실패");
    }
}
