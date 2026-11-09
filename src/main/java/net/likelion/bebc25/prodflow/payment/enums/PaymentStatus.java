package net.likelion.bebc25.prodflow.payment.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    READY("결제 준비"),          // 가상계좌 미발급
    WAITING("결제 입금 대기"),    // 가상계좌 발급 완료
    PAID("입금 완료"),
    FAILED("결제 실패"),
    CANCELLED("결제 취소"),
    REQUESTED("발급 요청 중"),
    ISSUE_UNCERTAIN("발급 확인 필요"),
    PENDING("처리 대기"),
    PAY_PENDING("승인 대기"),
    VIRTUAL_ACCOUNT_ISSUED("입금 대기"),
    PARTIAL_CANCELLED("부분 취소");

    private final String label;
}
