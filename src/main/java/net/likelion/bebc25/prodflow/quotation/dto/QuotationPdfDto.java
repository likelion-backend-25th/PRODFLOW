package net.likelion.bebc25.prodflow.quotation.dto;

import java.time.Instant;

import net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException;

public class QuotationPdfDto {
    public record PreviewResponse(
            Long quotationId,
            String quotationNo,
            String clientCompany,
            String clientEmail,
            String previewUrl,
            Instant expiresAt
    ) {
        // PDF 미리보기 응답 변환
        public static PreviewResponse from(QuotationDto.DetailResponse draft, String previewUrl, Instant expiresAt) {
            return new PreviewResponse(draft.id(), draft.quotationNo(), draft.clientCompany(),
                    draft.clientEmail(), previewUrl, expiresAt);
        }
    }

    public record FailureResponse(
            String code,
            String message,
            Long quotationId,
            String retryUrl
    ) {
        // PDF 준비 실패 응답 변환
        public static FailureResponse from(QuotationPdfPreparationException exception) {
            return new FailureResponse(exception.getCode(), exception.getMessage(), exception.getQuotationId(),
                    "/api/v1/quotations/" + exception.getQuotationId() + "/pdf/regenerate");
        }
    }
}
