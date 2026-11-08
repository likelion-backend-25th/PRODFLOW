package net.likelion.bebc25.prodflow.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.ToString;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@ToString
public class PortOneDto {
    // 수기 결제 시 포트원 API에 전달할 정보, PaymentService에서 정보를 취합해서 전달해야 함
    public record IssueRequest(
            // 결제 id (임의 생성)
            String paymentId,

            // 주문명 (예: 갤럭시 케이스 외 1)
            String orderName,

            // 수주 금액
            Long amount,

            // 수주 회사 명
            String companyName,

            // 수주 회사 메일
            String email,

            // 수주 회사 연락처
            String phone,

            // 수주 회사 도로명 주소
            String roadAddress,

            // 수주 회사 상세 주소
            String detailAddress,

            // 가상계좌 발급 은행
            String bank,

            // 가상계좌 만료일 (시간대 오프셋 포함, 예: 2026-11-09T16:16:26+09:00)
            OffsetDateTime dueDate,

            // 멱등키 (임의 생성)
            String idempotencyKey
    ) {
    }

    public record IssueResponse(
            String pgTxId,
            String paidAt
    ) {
    }

    public record IssueResult(
            String paymentId,
            String bankCode,
            String accountNumber,
            String accountName,
            LocalDateTime expiresAt
    ) {
    }

    public record PaymentSnapshot(
            String id,
            String pgTxId,
            String status,
            String transactionId,
            Customer customer,
            String currency,
            Amount amount,
            Method method,
            Channel channel,
            OffsetDateTime paidAt,
            OffsetDateTime cancelledAt,
            OffsetDateTime failedAt
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Amount(Long total, Long vat) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Method(
                String type,
                String bank,
                String accountNumber,
                OffsetDateTime expiredAt
        ) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Customer(String name) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Channel(String key, String pgProvider) {
        }
    }
}