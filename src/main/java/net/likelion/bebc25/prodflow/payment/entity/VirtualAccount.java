package net.likelion.bebc25.prodflow.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.likelion.bebc25.prodflow.payment.enums.Bank;
import net.likelion.bebc25.prodflow.payment.enums.VirtualAccountStatus;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VirtualAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "payment_id",
            nullable = false)
    private Payment payment;

    @Column(
            nullable = false)
    private Bank bank;

    @Column(
            nullable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VirtualAccountStatus status = VirtualAccountStatus.ACTIVE;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Builder
    private VirtualAccount(
            Payment payment,
            Bank bank,
            String accountNumber,
            String accountName,
            OffsetDateTime expiresAt
    ) {
        this.payment = payment;
        this.bank = bank;
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.expiresAt = expiresAt;
        this.status = VirtualAccountStatus.ACTIVE;
    }

    // 가상계좌 만료
    public void expire() {
        this.status = VirtualAccountStatus.EXPIRED;
    }

    // 가상계좌 사용 취소
    public void cancel() {
        this.status = VirtualAccountStatus.CANCELLED;
    }

    // 현재 입금 가능한 계좌인지 확인
    public boolean isActive() {
        return this.status == VirtualAccountStatus.ACTIVE;
    }

    // 실제 시간 기준 만료 여부 확인
    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }

    public void updateAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public void updateExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
