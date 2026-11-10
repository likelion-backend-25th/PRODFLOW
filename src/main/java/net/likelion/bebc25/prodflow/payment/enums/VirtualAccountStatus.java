package net.likelion.bebc25.prodflow.payment.enums;

public enum VirtualAccountStatus {
    ACTIVE,      // 입금 가능(만료일 전)
    EXPIRED,     // 입금기한 지남
    CANCELLED,   // 말소/취소된 계좌
    USED         // 실제 입금 완료된 계좌
}
