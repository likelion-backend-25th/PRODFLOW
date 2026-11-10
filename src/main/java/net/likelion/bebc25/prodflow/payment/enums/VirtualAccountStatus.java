package net.likelion.bebc25.prodflow.payment.enums;

public enum VirtualAccountStatus {

    ACTIVE,     // 입금 가능
    USED,       // 정상 입금으로 사용 완료
    EXPIRED,    // 입금 기한 만료
    CANCELLED   // 사용 취소
}
