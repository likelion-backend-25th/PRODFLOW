package net.likelion.bebc25.prodflow.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.likelion.bebc25.prodflow.payment.enums.VirtualAccountStatus;

import java.time.LocalDateTime;

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
    private String bankCode;

    @Column(
            nullable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VirtualAccountStatus status = VirtualAccountStatus.ACTIVE;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    private VirtualAccount(
            Payment payment,
            String bankCode,
            String accountNumber,
            String accountName,
            LocalDateTime expiresAt
    ) {
        this.payment = payment;
        this.bankCode = bankCode;
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
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
