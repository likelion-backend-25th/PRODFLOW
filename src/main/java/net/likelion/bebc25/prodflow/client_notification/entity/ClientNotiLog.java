package net.likelion.bebc25.prodflow.client_notification.entity;

import java.time.LocalDateTime;
import java.time.ZoneId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import net.likelion.bebc25.prodflow.client_notification.enums.ClientNotiStatus;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.user.entity.User;

@Entity
@Getter
@Table(name = "client_noti_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientNotiLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_order_id")
    private CustomerOrder customerOrder;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false, length = 10)
    private String channel;

    @Column(nullable = false, length = 100)
    private String recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id")
    private Quotation quotation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientNotiStatus status;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sent_by", nullable = false)
    private User sentBy;

    @Column(name = "error_message")
    private String errorMessage;

    public static ClientNotiLog deposit(
            CustomerOrder order, User sender, String recipient, ClientNotiStatus status, String errorMessage
    ) {
        ClientNotiLog log = new ClientNotiLog();
        log.customerOrder = order;
        log.sentBy = sender;
        log.kind = "DEPOSIT";
        log.channel = "EMAIL";
        log.recipient = recipient;
        log.status = status;
        log.sentAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        log.errorMessage = errorMessage == null ? null
                : errorMessage.substring(0, Math.min(255, errorMessage.length()));
        return log;
    }

    // 배송 시작 및 잔금 안내는 계약금 안내와 발송 이력을 구분한다.
    public static ClientNotiLog shipping(
            CustomerOrder order, User sender, String recipient, ClientNotiStatus status, String errorMessage
    ) {
        ClientNotiLog log = deposit(order, sender, recipient, status, errorMessage);
        log.kind = "SHIPPING";
        return log;
    }

    // 견적 이메일 발송 결과 생성
    public static ClientNotiLog quotation(
            Quotation quotation, User sender, String recipient, ClientNotiStatus status, String errorMessage
    ) {
        ClientNotiLog log = new ClientNotiLog();
        log.quotation = quotation;
        log.sentBy = sender;
        log.kind = "QUOTATION";
        log.channel = "EMAIL";
        log.recipient = recipient;
        log.status = status;
        log.sentAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        log.errorMessage = status == ClientNotiStatus.SENT || errorMessage == null ? null
                : errorMessage.substring(0, Math.min(255, errorMessage.length()));
        return log;
    }
}
