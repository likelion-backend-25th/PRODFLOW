package net.likelion.bebc25.prodflow.quotation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.likelion.bebc25.prodflow.common.storage.StorageClient;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationPdfDto;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Map;

@Slf4j
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
@RequiredArgsConstructor
public class QuotationPdfStorageService {
    private static final Duration PREVIEW_EXPIRATION = Duration.ofMinutes(10);

    private final QuotationRepository quotationRepository;
    private final QuotationService quotationService;
    private final QuotationPdfService quotationPdfService;
    private final StorageClient storageClient;
    private final PlatformTransactionManager transactionManager;

    // 초안 저장 후 PDF 준비
    public QuotationPdfDto.PreviewResponse create(QuotationDto.CreateRequest request, Long userId) {
        QuotationDto.DetailResponse draft = quotationService.create(request, userId);
        return prepareSavedDraft(draft.id());
    }

    // 초안 수정 후 PDF 준비
    public QuotationPdfDto.PreviewResponse update(
            Long quotationId, QuotationDto.CreateRequest request, Long userId
    ) {
        QuotationDto.DetailResponse draft = quotationService.updateDraft(quotationId, request, userId);
        return prepareSavedDraft(draft.id());
    }

    // 저장된 초안 PDF 준비 및 실패 변환
    private QuotationPdfDto.PreviewResponse prepareSavedDraft(Long quotationId) {
        try {
            return prepare(quotationId);
        } catch (QuotationPdfPreparationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new QuotationPdfPreparationException(quotationId, ex);
        }
    }

    // PDF 경로 무효화 및 준비 실행
    public QuotationPdfDto.PreviewResponse prepare(Long quotationId) {
        transaction().executeWithoutResult(status -> {
            Quotation quotation = lockQuotation(quotationId);
            requireDraft(quotation);
            quotation.clearPdfObjectKey();
        });
        PreparationResult result;
        try {
            result = transaction().execute(status -> prepareLocked(quotationId));
        } catch (RuntimeException ex) {
            throw new QuotationPdfPreparationException(quotationId, ex);
        }
        if (result.failure() != null) {
            if (result.failure() instanceof QuotationPdfPreparationException failure) {
                throw failure;
            }
            throw new QuotationPdfPreparationException(quotationId, result.failure());
        }
        return result.response();
    }

    // 견적 잠금 상태에서 PDF 생성·저장
    private PreparationResult prepareLocked(Long quotationId) {
        Quotation quotation = lockQuotation(quotationId);
        requireDraft(quotation);
        quotation.clearPdfObjectKey();
        try {
            QuotationDto.DetailResponse draft = quotationService.getQuotationById(quotationId);
            byte[] pdf;
            try {
                pdf = quotationPdfService.generate(draft);
            } catch (IllegalArgumentException ex) {
                throw new QuotationPdfPreparationException(
                        quotationId, HttpStatus.BAD_REQUEST, "PDF_GENERATION_FAILED", ex.getMessage(), ex);
            }
            String key = "quotations/" + quotation.getQuotationNo() + ".pdf";
            try {
                storageClient.delete(key);
            } catch (RuntimeException ex) {
                log.warn("견적 PDF 삭제 실패, 같은 키에 업로드합니다. quotationId={}", quotationId, ex);
            }
            String token = QuotationDocumentFingerprint.create(draft, pdf);
            storageClient.upload(key, new ByteArrayInputStream(pdf), "application/pdf", pdf.length,
                    Map.of(QuotationDocumentFingerprint.METADATA_KEY, token));
            QuotationPdfDto.PreviewResponse response = previewResponse(draft, key, token);
            quotation.changePdfObjectKey(key);
            return new PreparationResult(response, null);
        } catch (RuntimeException ex) {
            log.error("견적 PDF 준비 실패. quotationId={}", quotationId, ex);
            // 예외를 트랜잭션 밖에서 던져 무효화한 경로가 되살아나지 않게 한다.
            return new PreparationResult(null, ex);
        }
    }

    // 저장된 PDF 미리보기 URL 조회
    public QuotationPdfDto.PreviewResponse getPreview(Long quotationId) {
        return transaction().execute(status -> {
            Quotation quotation = lockQuotation(quotationId);
            if (quotation.getPdfObjectKey() == null) {
                throw new QuotationPdfPreparationException(quotationId, HttpStatus.CONFLICT, "PDF_NOT_READY",
                        "PDF 준비가 완료되지 않았습니다. 재생성 후 확인해주세요.", null);
            }
            try {
                var draft = quotationService.getQuotationById(quotationId);
                var file = storageClient.read(quotation.getPdfObjectKey());
                boolean valid = quotation.getStatus() == QuotationStatus.DRAFT
                        ? QuotationDocumentFingerprint.matches(draft, file)
                        : QuotationDocumentFingerprint.hasValidContent(file);
                if (!valid) {
                    throw new QuotationPdfPreparationException(quotationId, HttpStatus.CONFLICT, "PDF_CHANGED",
                            "견적 내용과 PDF가 일치하지 않습니다. PDF를 재생성해주세요.", null);
                }
                return previewResponse(draft, quotation.getPdfObjectKey(),
                        file.metadata().get(QuotationDocumentFingerprint.METADATA_KEY));
            } catch (QuotationPdfPreparationException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new QuotationPdfPreparationException(quotationId, ex);
            }
        });
    }

    // 독립 트랜잭션 템플릿 생성
    private TransactionTemplate transaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    // 견적 조회 및 쓰기 잠금
    private Quotation lockQuotation(Long quotationId) {
        return quotationRepository.findByIdForUpdate(quotationId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 견적서입니다. ID: " + quotationId));
    }

    // 견적 초안 상태 검증
    private void requireDraft(Quotation quotation) {
        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new IllegalArgumentException("초안 상태의 견적만 PDF를 생성할 수 있습니다.");
        }
    }

    // 조회 URL 발급 및 미리보기 응답 변환
    private QuotationPdfDto.PreviewResponse previewResponse(
            QuotationDto.DetailResponse draft, String key, String token
    ) {
        Instant expiresAt = Instant.now().plus(PREVIEW_EXPIRATION);
        String url = storageClient.createPresignedGetUrl(key, PREVIEW_EXPIRATION);
        return QuotationPdfDto.PreviewResponse.from(draft, url, expiresAt, token);
    }

    private record PreparationResult(QuotationPdfDto.PreviewResponse response, RuntimeException failure) {
    }
}
