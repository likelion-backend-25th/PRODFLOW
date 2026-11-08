package net.likelion.bebc25.prodflow.quotation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import net.likelion.bebc25.prodflow.client_notification.enums.ClientNotiStatus;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;

public class QuotationSendDto {
    public record Request(
            @NotBlank(message = "미리보기 확인값을 입력해주세요.")
            @Pattern(regexp = "[a-f0-9]{64}\\.[a-f0-9]{64}", message = "미리보기 확인값 형식이 올바르지 않습니다.")
            String confirmationToken
    ) {
    }

    public record Response(
            Long quotationId,
            QuotationStatus status,
            String emailStatus,
            boolean duplicateRequest,
            String message
    ) {
        // 견적 확정과 이메일 결과 응답 변환
        public static Response from(Long quotationId, ClientNotiStatus emailStatus, boolean duplicateRequest) {
            String message;
            if (emailStatus == null) {
                message = "이미 발송 확정된 견적입니다. 이메일 처리 결과는 아직 확인되지 않았습니다.";
            } else if (emailStatus == ClientNotiStatus.FAILED) {
                message = "견적은 발송 확정되었으나 이메일 전송에 실패했습니다. 자동으로 재발송하지 않습니다.";
            } else {
                message = "견적 발송이 완료되었습니다.";
            }
            return new Response(quotationId, QuotationStatus.SENT,
                    emailStatus == null ? "UNKNOWN" : emailStatus.name(), duplicateRequest, message);
        }
    }
}
