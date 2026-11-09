package net.likelion.bebc25.prodflow.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.likelion.bebc25.prodflow.common.entity.BaseTimeEntity;
import net.likelion.bebc25.prodflow.payment.enums.Bank;
import net.likelion.bebc25.prodflow.payment.enums.VirtualAccountStatus;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VirtualAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 결제 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    // 은행 정보
    @Column(nullable = false)
    private Bank bank;

    // 가상계좌 번호
    @Column(nullable = false)
    private String accountNumber;

    // 가상계좌 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VirtualAccountStatus status = VirtualAccountStatus.ACTIVE;

    // 예금주
    @Column(name = "account_name")
    private String accountName;

    // 가상계좌 만료 일시
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

    // 가상계좌가 ACTIVE 상태인지 확인
    public boolean isActive() {
        return this.status == VirtualAccountStatus.ACTIVE;
    }

    // 실제 시간 기준 만료 여부 확인
    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }

    // 가상계좌 번호 변경
    public void updateAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    // 가상계좌 만료 일시 변경
    public void updateExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
