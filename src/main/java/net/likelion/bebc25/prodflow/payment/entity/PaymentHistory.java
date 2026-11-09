package net.likelion.bebc25.prodflow.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Getter
@Entity
@Table
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "payment_id",
            nullable = false,
            unique = true
    )
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "virtual_account_id"
    )
    private VirtualAccount virtualAccount;

    @Column(unique = true)
    private String transactionId;

    @Column(nullable = false)
    private OffsetDateTime paidAt;

    @CreationTimestamp
    @Column(
            nullable = false,
            updatable = false)
    private LocalDateTime createdAt;

    @Column
    OffsetDateTime webhookRemoteUpdatedAt;

    @Column
    private String lastWebhookType;

    @Builder
    private PaymentHistory(
            Payment payment,
            VirtualAccount virtualAccount,
            String transactionId,
            OffsetDateTime paidAt
    ) {
        this.payment = payment;
        this.virtualAccount = virtualAccount;
        this.transactionId = transactionId;
        this.paidAt = paidAt;
    }

    public void updatePaidAt(OffsetDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public void updateWebhookRemoteUpdatedAt(OffsetDateTime webhookRemoteUpdatedAt) {
        this.webhookRemoteUpdatedAt = webhookRemoteUpdatedAt;
    }

    public void recordWebhookType(String type) {
        this.lastWebhookType = type;
    }
}
