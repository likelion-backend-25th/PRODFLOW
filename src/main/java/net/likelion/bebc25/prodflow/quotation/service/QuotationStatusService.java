package net.likelion.bebc25.prodflow.quotation.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationStatusDto;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationItemRepository;
import net.likelion.bebc25.prodflow.common.storage.StorageClient;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class QuotationStatusService {
    private final QuotationRepository quotationRepository;
    private final QuotationService quotationService;
    private final UserRepository userRepository;
    private final QuotationItemRepository quotationItemRepository;
    private final StorageClient storageClient;

    // 견적 초안 및 품목 삭제
    @Transactional
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    public void deleteDraft(Long quotationId, Long userId) {
        validateOperator(userId);
        var quotation = quotationRepository.findByIdForUpdate(quotationId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 견적서입니다. ID: " + quotationId));
        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "초안 상태의 견적만 삭제할 수 있습니다.");
        }
        String objectKey = quotation.getPdfObjectKey() == null
                ? "quotations/" + quotation.getQuotationNo() + ".pdf" : quotation.getPdfObjectKey();
        quotationItemRepository.deleteAll(quotationItemRepository.findByQuotationIdOrderByIdAsc(quotationId));
        quotationItemRepository.flush();
        quotationRepository.delete(quotation);
        // DB 삭제가 확정된 뒤 PDF 정리
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageClient.delete(objectKey);
                } catch (RuntimeException ex) {
                    log.warn("삭제된 견적 초안의 PDF 정리 실패. quotationId={}, objectKey={}", quotationId, objectKey, ex);
                }
            }
        });
    }

    // 거래처 승인 반영
    @Transactional
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    public QuotationDto.DetailResponse approve(Long quotationId, Long userId) {
        return changeStatus(quotationId, userId, QuotationStatus.APPROVED);
    }

    // 거래처 거절 반영
    @Transactional
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    public QuotationDto.DetailResponse reject(Long quotationId, Long userId) {
        return changeStatus(quotationId, userId, QuotationStatus.REJECTED);
    }

    // 발송 견적 직접 취소
    @Transactional
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    public QuotationDto.DetailResponse cancel(Long quotationId, Long userId) {
        return changeStatus(quotationId, userId, QuotationStatus.CANCELLED);
    }

    // 관리자 요청으로 만료 처리 실행
    @Transactional
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    public QuotationStatusDto.ExpirationResponse expireQuotations(Long userId) {
        validateOperator(userId);
        return QuotationStatusDto.ExpirationResponse.from(expireQuotations());
    }

    // 유효기간이 지난 발송 견적 일괄 만료
    @Transactional
    public int expireQuotations() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        var quotations = quotationRepository.findByStatusAndValidUntilBeforeOrderByIdAsc(QuotationStatus.SENT, today);
        for (var quotation : quotations) {
            quotation.expired(today);
        }
        return quotations.size();
    }

    // 현재 상태·유효기간 검증 및 전환
    private QuotationDto.DetailResponse changeStatus(Long quotationId, Long userId, QuotationStatus target) {
        validateOperator(userId);
        var quotation = quotationRepository.findByIdForUpdate(quotationId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 견적서입니다. ID: " + quotationId));
        if (quotation.getStatus() == target) {
            return quotationService.getQuotationById(quotationId);
        }
        if (quotation.getStatus() != QuotationStatus.SENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "발송완료 상태의 견적만 승인·거절·취소할 수 있습니다.");
        }
        if (quotation.getValidUntil().isBefore(LocalDate.now(ZoneId.of("Asia/Seoul")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "유효기간이 지난 견적입니다. 자동 만료 처리 대상입니다.");
        }
        switch (target) {
            case APPROVED -> quotation.approve();
            case REJECTED -> quotation.reject();
            case CANCELLED -> quotation.cancel();
            default -> throw new IllegalArgumentException("지원하지 않는 견적 상태입니다.");
        }
        quotationRepository.flush();
        return quotationService.getQuotationById(quotationId);
    }

    // 처리자 현재 권한·계정 상태 검증
    private void validateOperator(Long userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("처리자 계정을 찾을 수 없습니다."));
        if (user.getStatus() != UserStatus.ACTIVE || user.isMustChangePassword()
                || (user.getRole() != UserRole.SUPER_ADMIN && user.getRole() != UserRole.ORDER_MANAGER)) {
            throw new AccessDeniedException("사용 가능한 최고 관리자 또는 수주 관리자 계정으로 처리해주세요.");
        }
    }
}
