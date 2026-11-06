package net.likelion.bebc25.prodflow.payment.client.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;

public class PortOneDto {
    public record IssueRequest(
            String paymentId,
            String orderName,
            long amount,
            String customerName,
            String customerEmail,
            String customerPhone,
            String bank,
            OffsetDateTime expiresAt,
            String idempotencyKey
    ) {
        public IssueRequest {
            requireText(paymentId, "PortOne 결제 ID");
            if (!paymentId.matches("[A-Za-z0-9_-]{1,40}")) {
                throw new IllegalArgumentException("PortOne 결제 ID는 40자 이하의 영문/숫자/_/-만 허용합니다.");
            }
            requireText(orderName, "주문명");
            if (amount <= 0) {
                throw new IllegalArgumentException("결제 금액은 0원보다 커야 합니다.");
            }
            requireText(customerName, "구매자 이름");
            requireText(customerEmail, "구매자 이메일");
            requireText(customerPhone, "구매자 전화번호");
            requireText(bank, "PortOne 은행 코드");
            Objects.requireNonNull(expiresAt, "만료 시각은 필수입니다.");
            if (!expiresAt.isAfter(OffsetDateTime.now())) {
                throw new IllegalArgumentException("가상계좌 만료 시각은 미래여야 합니다.");
            }
            requireText(idempotencyKey, "멱등 키");
            if (!idempotencyKey.matches("[A-Za-z0-9_-]{16,256}")) {
                throw new IllegalArgumentException("멱등 키는 영문/숫자/_/-로 구성된 16~256자여야 합니다.");
            }
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + "은(는) 필수입니다.");
            }
        }
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
            if (!(value instanceof Number number)) return null;
            try {
                return new java.math.BigDecimal(number.toString()).longValueExact();
            } catch (ArithmeticException | NumberFormatException e) {
                throw new IllegalStateException("PortOne 결제 금액이 정수가 아닙니다.", e);
            }
        }

        private static OffsetDateTime optionalDate(Map<?, ?> body, String field) {
            String value = optionalText(body, field);
            if (value == null) return null;
            try {
                return OffsetDateTime.parse(value);
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalStateException("PortOne 응답의 날짜 형식이 올바르지 않습니다: " + field, e);
            }
        }
    }


}
