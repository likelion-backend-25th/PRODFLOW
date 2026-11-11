package net.likelion.bebc25.prodflow.payment.dto;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import net.likelion.bebc25.prodflow.client_notification.entity.PaymentNotificationRequest;
import net.likelion.bebc25.prodflow.client_notification.enums.PaymentNotificationStatus;

public class PaymentNotificationDto {
    public record Request(
            @NotBlank(message = "발송 요청 식별자를 입력해주세요.")
            @Pattern(
                    regexp = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}",
                    message = "발송 요청 식별자는 UUID 형식이어야 합니다.")
            String requestId,
            @NotBlank(message = "수신 이메일을 입력해주세요.")
            @Email(message = "수신 이메일 형식이 올바르지 않습니다.")
            @Size(max = 100, message = "수신 이메일은 100자 이내로 입력해주세요.")
            String recipient,
            @NotNull(message = "수주 총액을 입력해주세요.")
            @Positive(message = "수주 총액은 0보다 커야 합니다.")
            Long totalAmount,
            @NotNull(message = "공급가액 합계를 입력해주세요.")
            @PositiveOrZero(message = "공급가액 합계는 0 이상이어야 합니다.")
            Long supplyAmount,
            @NotNull(message = "부가세를 입력해주세요.")
            @PositiveOrZero(message = "부가세는 0 이상이어야 합니다.")
            Long vatAmount,
            @NotNull(message = "계약금 청구액을 입력해주세요.")
            @Positive(message = "계약금 청구액은 0보다 커야 합니다.")
            Long depositAmount,
            @NotNull(message = "부가세 포함 여부를 입력해주세요.")
            Boolean vatIncluded,
            @NotNull(message = "계약금 마감일을 입력해주세요.")
            LocalDate depositDueDate,
            @NotEmpty(message = "확인한 수주 품목을 입력해주세요.")
            List<@NotNull(message = "수주 품목은 비어 있을 수 없습니다.") @Valid Item> items
    ) {
    }

    public record Item(
            @NotNull(message = "수주 품목 ID를 입력해주세요.")
            @Positive(message = "수주 품목 ID는 0보다 커야 합니다.")
            Long id,
            @NotBlank(message = "품목명을 입력해주세요.")
            String productName,
            @NotNull(message = "품목 수량을 입력해주세요.")
            @Positive(message = "품목 수량은 0보다 커야 합니다.")
            Integer quantity,
            @NotNull(message = "품목 단가를 입력해주세요.")
            @Positive(message = "품목 단가는 0보다 커야 합니다.")
            Long unitPrice,
            @NotNull(message = "품목 공급가액을 입력해주세요.")
            @PositiveOrZero(message = "품목 공급가액은 0 이상이어야 합니다.")
            Long supplyAmount
    ) {
    }

    public record Response(String requestId, PaymentNotificationStatus status, String message) {
        public static Response from(PaymentNotificationRequest request) {

            return new Response(request.getId(), request.getStatus(), request.getMessage());
        }
    }
}
