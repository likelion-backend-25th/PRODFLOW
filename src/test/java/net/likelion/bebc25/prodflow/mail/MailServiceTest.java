package net.likelion.bebc25.prodflow.mail;

import java.util.Properties;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import net.likelion.bebc25.prodflow.mail.service.MailService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailServiceTest {
    @Test
    void attachesOriginalPdfWithCorrectRecipientAndContentType() throws Exception {
        var sender = mock(JavaMailSender.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        var service = new MailService(sender);
        ReflectionTestUtils.setField(service, "senderEmail", "sender@example.com");
        byte[] pdf = "%PDF-1.7 test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        service.sendAttachment("client@example.com", "견적서", "첨부합니다.", "QT-123.pdf", pdf);
        verify(sender).send(any(MimeMessage.class));
        message.saveChanges();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("client@example.com");
        assertThat(message.getSubject()).isEqualTo("견적서");
        Multipart multipart = (Multipart) message.getContent();
        var attachment = multipart.getBodyPart(multipart.getCount() - 1);
        assertThat(attachment.getFileName()).isEqualTo("QT-123.pdf");
        assertThat(attachment.isMimeType("application/pdf")).isTrue();
        assertThat(attachment.getInputStream().readAllBytes()).containsExactly(pdf);
    }
}
