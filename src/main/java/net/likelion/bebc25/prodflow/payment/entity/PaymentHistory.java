package net.likelion.bebc25.prodflow.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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
            name = "virtual_account_id",
            nullable = false
    )
    private VirtualAccount virtualAccount;

    @Column(unique = true)
    private String transactionId;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @CreationTimestamp
    @Column(
            nullable = false,
            updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private PaymentHistory(
            Payment payment,
            VirtualAccount virtualAccount,
            String transactionId,
            LocalDateTime receivedAt
    ) {
        this.payment = payment;
        this.virtualAccount = virtualAccount;
        this.transactionId = transactionId;
        this.receivedAt = receivedAt;
    }
}
