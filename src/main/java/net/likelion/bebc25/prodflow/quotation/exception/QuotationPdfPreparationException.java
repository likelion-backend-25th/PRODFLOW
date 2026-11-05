package net.likelion.bebc25.prodflow.quotation.exception;

import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
public class QuotationPdfPreparationException extends RuntimeException {
    private final Long quotationId;
    private final HttpStatus status;
    private final String code;

    // PDF 준비 실패 예외 생성
    public QuotationPdfPreparationException(Long quotationId, Throwable cause) {
        this(quotationId, HttpStatus.BAD_GATEWAY, "PDF_PREPARATION_FAILED",
                "PDF를 준비하지 못했습니다. 다시 시도해주세요.", cause);
    }

    // 상태·코드를 지정한 PDF 예외 생성
    public QuotationPdfPreparationException(
            Long quotationId, HttpStatus status, String code, String message, Throwable cause
    ) {
        super(message, cause);
        this.quotationId = quotationId;
        this.status = status;
        this.code = code;
    }
}
