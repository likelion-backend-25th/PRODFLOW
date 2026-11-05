package net.likelion.bebc25.prodflow.quotation.enums;

public enum QuotationStatus {
    DRAFT, // 발송 전 확인 중인 초안
    SENT, // 발송 완료
    APPROVED,  // 승인
    REJECTED,  // 거절
    CANCELLED,  // 취소
    EXPIRED // 만료
}
