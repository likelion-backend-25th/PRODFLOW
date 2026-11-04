package net.likelion.bebc25.prodflow.payment.dto;

import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.VirtualAccountStatus;

import java.time.LocalDateTime;

public class VirtualAccountDto {

    // 가상계좌 정보 조회 응답
    public record Response(

            // 가상계좌 ID
            Long virtualAccountId,

            // 연결된 결제 ID
            Long paymentId,

            // 은행 코드
            String bankCode,

            // 계좌번호
            String accountNumber,

            // 예금주
            String accountName,

            // 입금기한
            LocalDateTime expiresAt,

            // 가상계좌 상태
            VirtualAccountStatus status
    ) {

        // Entity를 DTO로 변환
        public static Response from(VirtualAccount account) {
            return new Response(
                    account.getId(),
                    account.getPayment().getId(),
                    account.getBankCode(),
                    account.getAccountNumber(),
                    account.getAccountName(),
                    account.getExpiresAt(),
                    account.getStatus()
            );
        }
    }
}