package net.likelion.bebc25.prodflow.quotation.dto;

public class QuotationStatusDto {
    public record ExpirationResponse(int expiredCount) {
        // 만료 처리 결과 변환
        public static ExpirationResponse from(int expiredCount) {
            return new ExpirationResponse(expiredCount);
        }
    }
}
