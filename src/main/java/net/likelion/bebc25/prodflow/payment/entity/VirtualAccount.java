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

    // 은행 정보는 enum 순서가 아닌 명칭으로 DB에 저장한다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Bank bank;

    // 재발급 전 결제 ID도 추적하기 위한 외부 거래 식별자.
    // 과거 데이터는 null일 수 있으므로 nullable로 유지한다.
    @Column
    private String portonePaymentId;

    // 가상계좌 번호
    @Column(nullable = false)
    private String accountNumber;

    // 가상계좌 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VirtualAccountStatus status = VirtualAccountStatus.ACTIVE;

    // 예금주
    @Column
    private String accountName;

    // 가상계좌 만료 일시
    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @Builder
    private VirtualAccount(
            Payment payment,
            String portonePaymentId,
            Bank bank,
            String accountNumber,
            String accountName,
            OffsetDateTime expiresAt
    ) {
        this.payment = payment;
        this.portonePaymentId = portonePaymentId;
        this.bank = bank;
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.expiresAt = expiresAt;
        this.status = VirtualAccountStatus.ACTIVE;
    }

    // 입금 처리 후 더 이상 입금 가능 계좌로 표시하지 않는다.
    public void used() {
        this.status = VirtualAccountStatus.USED;
    }

    // 아직 ACTIVE인 계좌만 만료시킨다. 입금 완료(USED)·말소(CANCELLED)를 덮어쓰지 않는다.
    public void expire() {
        if (this.status == VirtualAccountStatus.ACTIVE && isExpired()) {
            this.status = VirtualAccountStatus.EXPIRED;
        }
    }

    // 스케줄러 실행 전에도 API에서는 만료된 계좌를 즉시 EXPIRED로 보여준다.
    // DB의 상태를 직접 변경하지 않는 조회 전용 메서드이다.
    public VirtualAccountStatus getEffectiveStatus() {
        if (this.status == VirtualAccountStatus.ACTIVE && isExpired()) {
            return VirtualAccountStatus.EXPIRED;
        }
        return this.status;
    }

    // 가상계좌 사용 취소
    public void cancel() {
        this.status = VirtualAccountStatus.CANCELLED;
    }

    // 가상계좌가 ACTIVE 상태인지 확인
    public boolean isActive() {
        return this.status == VirtualAccountStatus.ACTIVE;
    }

    // 만료시각과 정확히 일치하는 순간부터 만료로 판단한다.
    public boolean isExpired() {
        return expiresAt != null && !OffsetDateTime.now().isBefore(expiresAt);
    }

    public void recordOriginalPortonePaymentId(String paymentId) {
        if (portonePaymentId == null) {
            this.portonePaymentId = paymentId;
        } else if (!portonePaymentId.equals(paymentId)) {
            throw new IllegalStateException("기존 가상계좌의 PortOne 결제 ID를 덮어쓸 수 없습니다.");
        }
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
