package net.likelion.bebc25.prodflow.payment.dto;

import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;

import java.time.LocalDateTime;

public class PaymentHistoryDto {

    // 입금내역 조회 응답
    public record Response(

            // 입금내역 ID
            Long paymentHistoryId,

            // 연결된 결제 ID
            Long paymentId,

            // 연결된 가상계좌 ID
            Long virtualAccountId,

            // 외부 거래 식별값
            String transactionId,

            // 실제 입금일시
            LocalDateTime receivedAt,

            // 입금내역 생성일시
            LocalDateTime createdAt
    ) {

        // Entity를 DTO로 변환
        public static Response from(PaymentHistory history) {
            return new Response(
                    history.getId(),
                    history.getPayment().getId(),
                    history.getVirtualAccount() != null
                            ? history.getVirtualAccount().getId()
                            : null,
                    history.getTransactionId(),
                    history.getReceivedAt(),
                    history.getCreatedAt()
            );
        }
    }
}