package net.likelion.bebc25.prodflow.payment.entity;

public enum PaymentStatus {

    READY,      // 결제건 생성, 가상계좌 미발급
    WAITING,    // 가상계좌 발급 완료, 입금 대기
    PAID,       // 입금 완료
    FAILED,     // 결제 처리 실패
    CANCELLED   // 결제건 취소
}
