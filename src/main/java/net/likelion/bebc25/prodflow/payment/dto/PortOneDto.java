package net.likelion.bebc25.prodflow.payment.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

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
            String paymentId,
            String status,
            Long totalAmount,
            String transactionId,
            OffsetDateTime paidAt,
            String methodType,
            String bankCode,
            String accountNumber,
            String accountName,
            OffsetDateTime expiredAt
    ) {
        private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

        public static PaymentSnapshot fromMap(Map<?, ?> body) {
            if (body == null || body.isEmpty()) {
                throw new IllegalStateException("PortOne 결제 응답이 비어 있습니다.");
            }
            Map<?, ?> amount = objectOrEmpty(body.get("amount"));
            Map<?, ?> method = objectOrEmpty(body.get("method"));
            return new PaymentSnapshot(
                    requiredText(body, "id"),
                    requiredText(body, "status"),
                    optionalLong(amount, "total"),
                    optionalText(body, "transactionId"),
                    optionalDate(body, "paidAt"),
                    optionalText(method, "type"),
                    optionalText(method, "bank"),
                    optionalText(method, "accountNumber"),
                    optionalText(method, "remitteeName"),
                    optionalDate(method, "expiredAt")
            );
        }

        public IssueResult toIssueResult() {
            if (!"VIRTUAL_ACCOUNT_ISSUED".equals(status)) {
                throw new IllegalStateException("PortOne 가상계좌 발급 상태가 아닙니다: " + status);
            }
            if (!"PaymentMethodVirtualAccount".equals(methodType)) {
                throw new IllegalStateException("PortOne 응답 결제수단이 가상계좌가 아닙니다.");
            }
            if (bankCode == null || accountNumber == null || expiredAt == null) {
                throw new IllegalStateException("PortOne 발급 응답의 은행/계좌번호/만료일이 누락되었습니다. 결제 단건 조회로 확인해야 합니다.");
            }
            LocalDateTime expiryInKorea = expiredAt.atZoneSameInstant(KOREA).toLocalDateTime();
            return new IssueResult(paymentId, bankCode, accountNumber, accountName, expiryInKorea);
        }

        private static Map<?, ?> objectOrEmpty(Object value) {
            return value instanceof Map<?, ?> map ? map : Map.of();
        }

        private static String requiredText(Map<?, ?> body, String field) {
            String value = optionalText(body, field);
            if (value == null) {
                throw new IllegalStateException("PortOne 결제 응답 필수값 누락: " + field);
            }
            return value;
        }

        private static String optionalText(Map<?, ?> body, String field) {
            Object value = body.get(field);
            return value instanceof String text && !text.isBlank() ? text : null;
        }

        private static Long optionalLong(Map<?, ?> body, String field) {
            Object value = body.get(field);
            if (!(value instanceof Number number)) {
                return null;
            }
            try {
                return new java.math.BigDecimal(number.toString()).longValueExact();
            } catch (ArithmeticException | NumberFormatException e) {
                throw new IllegalStateException("PortOne 결제 금액이 정수가 아닙니다.", e);
            }
        }

        private static OffsetDateTime optionalDate(Map<?, ?> body, String field) {
            String value = optionalText(body, field);
            if (value == null) {
                return null;
            }
            try {
                return OffsetDateTime.parse(value);
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalStateException("PortOne 응답의 날짜 형식이 올바르지 않습니다: " + field, e);
            }
        }
    }


}
