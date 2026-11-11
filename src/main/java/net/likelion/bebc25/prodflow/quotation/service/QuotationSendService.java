package net.likelion.bebc25.prodflow.quotation.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.NoSuchElementException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import net.likelion.bebc25.prodflow.client_notification.entity.ClientNotiLog;
import net.likelion.bebc25.prodflow.client_notification.enums.ClientNotiStatus;
import net.likelion.bebc25.prodflow.client_notification.repository.ClientNotiLogRepository;
import net.likelion.bebc25.prodflow.common.storage.StorageClient;
import net.likelion.bebc25.prodflow.mail.service.MailService;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleResult;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleService;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;

@Slf4j
@Service
@Validated
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
@RequiredArgsConstructor
public class QuotationSendService {
    private final QuotationRepository quotationRepository;
    private final UserRepository userRepository;
    private final QuotationService quotationService;
    private final QuotationScheduleService scheduleService;
    private final StorageClient storageClient;
    private final MailService mailService;
    private final ClientNotiLogRepository notificationRepository;
    private final PlatformTransactionManager transactionManager;

    // 견적 확정 후 PDF 첨부 이메일 발송
    public QuotationSendDto.Response send(
            Long quotationId, @NotNull @Valid QuotationSendDto.Request request, Long userId
    ) {
        SendContext context = transaction().execute(status -> confirm(quotationId, request, userId));
        if (context.duplicateResponse() != null) {
            return context.duplicateResponse();
        }

        // 확정 트랜잭션 커밋 이후에만 외부 메일 서버를 호출한다.
        ClientNotiStatus emailStatus = ClientNotiStatus.SENT;
        String failure = null;
        try {
            mailService.sendAttachment(context.recipient(),
                    "[PRODFLOW] 견적서 송부드립니다 — " + context.quotationNo(),
                    createMailBody(context),
                    context.quotationNo() + ".pdf", context.pdf());
        } catch (RuntimeException ex) {
            emailStatus = ClientNotiStatus.FAILED;
            failure = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            log.error("견적 이메일 전송 실패. quotationId={}", quotationId, ex);
        }

        ClientNotiStatus result = emailStatus;
        String errorMessage = failure;
        transaction().executeWithoutResult(status -> notificationRepository.save(ClientNotiLog.quotation(
                quotationRepository.getReferenceById(quotationId), userRepository.getReferenceById(userId),
                context.recipient(), result, errorMessage)));
        return QuotationSendDto.Response.from(quotationId, result, false);
    }

    // 견적 잠금 및 발송 조건 검증
    private SendContext confirm(Long quotationId, QuotationSendDto.Request request, Long userId) {
        validateSender(userId);
        Quotation quotation = quotationRepository.findByIdForUpdate(quotationId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 견적서입니다. ID: " + quotationId));
        if (quotation.getStatus() == QuotationStatus.SENT) {
            var previous = notificationRepository.findFirstByQuotationIdAndKindOrderByIdDesc(quotationId, "QUOTATION");
            return new SendContext(null, null, null, QuotationSendDto.Response.from(
                    quotationId, previous.map(ClientNotiLog::getStatus).orElse(null), true),
                    null, null, null, null);
        }
        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw conflict("초안 상태의 견적만 발송할 수 있습니다.");
        }
        if (quotation.getPdfObjectKey() == null || quotation.getPdfObjectKey().isBlank()) {
            throw conflict("PDF 준비가 완료되지 않았습니다. PDF를 다시 확인해주세요.");
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        if (quotation.getValidUntil().isBefore(today)) {
            throw conflict("견적 유효기간이 지났습니다. 초안을 수정하고 PDF를 다시 확인해주세요.");
        }
        if (quotation.getRequestedDueDate().isBefore(today)) {
            throw conflict("희망 납기가 지났습니다. 납기와 PDF를 다시 확인해주세요.");
        }
        QuotationDto.DetailResponse draft = quotationService.getQuotationById(quotationId);
        validateSchedule(draft, today);

        StorageClient.StoredFile file;
        try {
            file = storageClient.read(quotation.getPdfObjectKey());
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "저장된 PDF를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.", ex);
        }
        if (!QuotationDocumentFingerprint.matches(draft, file)
                || !request.confirmationToken().equals(
                        file.metadata().get(QuotationDocumentFingerprint.METADATA_KEY))) {
            throw conflict("확인한 견적서와 현재 PDF가 다릅니다. PDF를 다시 확인해주세요.");
        }
        quotation.sent();
        return new SendContext(draft.clientEmail(), draft.quotationNo(), file.content(), null,
                draft.clientCompany(), draft.totalAmount(), draft.estimatedDueDate(), draft.validUntil());
    }

    // 견적 안내 이메일 본문 생성
    private String createMailBody(SendContext context) {
        return """
                안녕하세요, %s 담당자님.
                PRODFLOW입니다.

                요청하신 제품의 견적서를 첨부하여 보내드립니다.
                아래 내용과 첨부파일을 확인 부탁드립니다.

                ■ 견적 정보
                · 견적번호: %s
                · 견적금액: %s원 (VAT 포함)
                · 예상 납기일: %s
                · 견적 유효기한: %s까지

                품목별 수량과 단가는 첨부된 견적서를 참고해 주시기 바랍니다.
                예상 납기는 수주 등록 시 생산 상황에 따라 조정될 수 있습니다.

                견적 내용의 변경이나 문의 사항은 본 메일로 회신 부탁드립니다.

                감사합니다.

                PRODFLOW 드림
                """.formatted(context.clientCompany(), context.quotationNo(),
                NumberFormat.getIntegerInstance(Locale.KOREA).format(context.totalAmount()),
                context.estimatedDueDate(), context.validUntil());
    }

    // 발송자 권한·계정 상태 검증
    private void validateSender(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("발송자 계정을 찾을 수 없습니다."));
        if (user.getStatus() != UserStatus.ACTIVE || user.isMustChangePassword()
                || (user.getRole() != UserRole.SUPER_ADMIN && user.getRole() != UserRole.ORDER_MANAGER)) {
            throw new AccessDeniedException("사용 가능한 관리자 계정으로 비밀번호 변경 후 발송해주세요.");
        }
    }

    // 최신 생산 조건과 확인한 납기 비교
    private void validateSchedule(QuotationDto.DetailResponse draft, LocalDate today) {
        QuotationScheduleResult latest;
        try {
            latest = scheduleService.calculate(draft.totalQuantity(), draft.requestedDueDate(), today);
        } catch (IllegalArgumentException ex) {
            throw conflict("현재 생산 조건으로 납기를 지킬 수 없습니다. 납기와 PDF를 다시 확인해주세요.");
        }
        if (!latest.equals(new QuotationScheduleResult(
                draft.estimatedDueDate(), draft.plannedStartDate(), draft.plannedEndDate()))) {
            throw conflict("최신 생산 조건에 따라 납기가 달라졌습니다. 초안 수정 후 PDF를 다시 확인해주세요.");
        }
    }

    // 독립 트랜잭션 템플릿 생성
    private TransactionTemplate transaction() {
        var template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    // 재확인이 필요한 발송 오류 생성
    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record SendContext(String recipient, String quotationNo, byte[] pdf,
                               QuotationSendDto.Response duplicateResponse, String clientCompany, Long totalAmount,
                               LocalDate estimatedDueDate, LocalDate validUntil) {
    }
}
