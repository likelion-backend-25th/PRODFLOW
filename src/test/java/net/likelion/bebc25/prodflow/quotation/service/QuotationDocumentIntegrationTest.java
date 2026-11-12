package net.likelion.bebc25.prodflow.quotation.service;

import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.quotation.controller.QuotationController;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationRescheduleDto;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("견적 PDF·이메일 연동")
class QuotationDocumentIntegrationTest extends QuotationIntegrationTestSupport {

    @Test
    void sendsStoredPdfAfterCommitAndRecordsActualRecipient() {
        var preview = pdfStorageService.create(request(List.of(item(100, 1000L))), userId);
        var confirmed = service.getQuotationById(preview.quotationId());
        byte[] expected = files.get("quotations/" + preview.quotationNo() + ".pdf").content();
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(quotationRepository.findById(preview.quotationId()).orElseThrow().getStatus())
                    .isEqualTo(QuotationStatus.SENT);
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()).isFalse();
            assertThat(invocation.getArgument(0, String.class)).isEqualTo("client@example.com");
            assertThat(invocation.getArgument(1, String.class))
                    .isEqualTo("[PRODFLOW] 견적서 송부드립니다 — " + preview.quotationNo());
            assertThat(invocation.getArgument(2, String.class)).contains(
                    "안녕하세요, 기존 회사 담당자님.",
                    "견적번호: " + preview.quotationNo(),
                    "견적금액: 110,000원 (VAT 포함)",
                    "예상 납기일: " + confirmed.estimatedDueDate(),
                    "견적 유효기한: " + confirmed.validUntil() + "까지",
                    "본 메일로 회신 부탁드립니다.",
                    "예상 납기는 수주 등록 시 생산 상황에 따라 조정될 수 있습니다.",
                    "PRODFLOW 드림");
            assertThat(invocation.getArgument(4, byte[].class)).containsExactly(expected);
            return null;
        }).when(mailService).sendAttachment(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());

        var response = sendService.send(preview.quotationId(),
                new net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto.Request(preview.confirmationToken()), userId);
        assertThat(response.status()).isEqualTo(QuotationStatus.SENT);
        assertThat(response.emailStatus()).isEqualTo("SENT");
        assertThat(response.duplicateRequest()).isFalse();
        var recorded = notificationRepository.findAll().getFirst();
        assertThat(recorded.getRecipient()).isEqualTo("client@example.com");
        assertThat(recorded.getKind()).isEqualTo("QUOTATION");
        assertThat(recorded.getChannel()).isEqualTo("EMAIL");
        assertThat(recorded.getErrorMessage()).isNull();
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(pdfStorageService.getPreview(preview.quotationId()).confirmationToken())
                .isEqualTo(preview.confirmationToken());
        assertThatThrownBy(() -> pdfStorageService.prepare(preview.quotationId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mailFailureKeepsSentAndPdfAndDuplicateDoesNotResend() {
        var preview = pdfStorageService.create(request(List.of(item(100, 1000L))), userId);
        org.mockito.Mockito.doThrow(new RuntimeException("메일 실패".repeat(100))).when(mailService)
                .sendAttachment(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
        var request = new net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto.Request(preview.confirmationToken());
        var response = sendService.send(preview.quotationId(), request, userId);
        assertThat(response.emailStatus()).isEqualTo("FAILED");
        assertThat(quotationRepository.findById(preview.quotationId()).orElseThrow().getStatus())
                .isEqualTo(QuotationStatus.SENT);
        assertThat(quotationRepository.findById(preview.quotationId()).orElseThrow().getPdfObjectKey()).isNotNull();
        assertThat(notificationRepository.findAll().getFirst().getErrorMessage()).hasSize(255);
        var duplicate = sendService.send(preview.quotationId(), request, userId);
        assertThat(duplicate.duplicateRequest()).isTrue();
        assertThat(duplicate.emailStatus()).isEqualTo("FAILED");
        org.mockito.Mockito.verify(mailService, org.mockito.Mockito.times(1)).sendAttachment(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void staleConfirmationAndChangedClientCompanyCannotSendOldPdf() {
        var preview = pdfStorageService.create(request(List.of(item(100, 1000L))), userId);
        var stale = new net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto.Request(
                "a".repeat(64) + "." + "b".repeat(64));
        assertSendConflict(preview.quotationId(), stale);
        tx().executeWithoutResult(status -> ReflectionTestUtils.setField(
                em.find(Client.class, clientId), "company", "변경된 회사"));
        assertSendConflict(preview.quotationId(), new net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto.Request(
                preview.confirmationToken()));
        assertThatThrownBy(() -> pdfStorageService.getPreview(preview.quotationId()))
                .isInstanceOf(net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException.class);
        org.mockito.Mockito.verifyNoInteractions(mailService);
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void draftEditInvalidatesOldPdfAndLatestPreviewCanBeSent() {
        var preview = pdfStorageService.create(request(List.of(item(100, 1000L))), userId);
        service.updateDraft(preview.quotationId(), request(List.of(item(200, 2000L))), userId);
        var old = new net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto.Request(preview.confirmationToken());
        assertSendConflict(preview.quotationId(), old);
        var latest = pdfStorageService.prepare(preview.quotationId());
        assertSendConflict(preview.quotationId(), old);
        var response = sendService.send(preview.quotationId(),
                new net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto.Request(latest.confirmationToken()), userId);
        assertThat(response.emailStatus()).isEqualTo("SENT");
    }

    @Test
    void expiredQuotationAndChangedCapacityRequireReconfirmation() {
        var preview = pdfStorageService.create(request(List.of(item(1500, 1000L))), userId);
        var request = new net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto.Request(preview.confirmationToken());
        tx().executeWithoutResult(status -> ReflectionTestUtils.setField(
                em.find(Quotation.class, preview.quotationId()), "validUntil", today().minusDays(1)));
        assertSendConflict(preview.quotationId(), request);
        tx().executeWithoutResult(status -> {
            ReflectionTestUtils.setField(em.find(Quotation.class, preview.quotationId()), "validUntil", today().plusDays(14));
            for (Line line : em.createQuery("select l from Line l", Line.class).getResultList()) {
                line.editProduction(100);
            }
        });
        assertSendConflict(preview.quotationId(), request);
        org.mockito.Mockito.verifyNoInteractions(mailService);
    }

    @Test
    void s3ReadFailureAndTamperedPdfDoNotConfirmOrSend() {
        var preview = pdfStorageService.create(request(List.of(item(100, 1000L))), userId);
        String key = "quotations/" + preview.quotationNo() + ".pdf";
        var file = files.remove(key);
        var request = new net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto.Request(preview.confirmationToken());
        assertThatThrownBy(() -> sendService.send(preview.quotationId(), request, userId))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(502));
        files.put(key, new net.likelion.bebc25.prodflow.common.storage.StorageClient.StoredFile(
                new byte[]{1, 2, 3}, file.metadata()));
        assertSendConflict(preview.quotationId(), request);
        assertThat(notificationRepository.count()).isZero();
        org.mockito.Mockito.verifyNoInteractions(mailService);
    }

    @Test
    void sendRechecksCurrentAccountAndMethodPermission() {
        var preview = pdfStorageService.create(request(List.of(item(100, 1000L))), userId);
        var request = new net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto.Request(preview.confirmationToken());
        authenticateAs(UserRole.INQUIRY_MANAGER);
        assertThatThrownBy(() -> sendService.send(preview.quotationId(), request, userId))
                .isInstanceOf(AccessDeniedException.class);
        authenticateAs(UserRole.ORDER_MANAGER);
        tx().executeWithoutResult(status -> em.find(User.class, userId).deactivate());
        assertThatThrownBy(() -> sendService.send(preview.quotationId(), request, userId))
                .isInstanceOf(AccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(mailService);
    }

    @Test
    void duplicateWhileEmailIsRunningDoesNotStartAnotherSend() throws Exception {
        var preview = pdfStorageService.create(request(List.of(item(100, 1000L))), userId);
        var request = new net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto.Request(preview.confirmationToken());
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(mailService).sendAttachment(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                try {
                    return sendService.send(preview.quotationId(), request, userId);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            try {
                assertThat(entered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var duplicate = sendService.send(preview.quotationId(), request, userId);
                assertThat(duplicate.duplicateRequest()).isTrue();
                assertThat(duplicate.emailStatus()).isEqualTo("UNKNOWN");
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS).emailStatus()).isEqualTo("SENT");
        }
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void createsDraftAndGeneratesPdfWithoutChangingStatusOrReservingProduction() throws Exception {
        var created = service.create(request(List.of(item(100, 1000L))), userId);
        byte[] pdf = pdfService.generate(created);
        try (var reader = new org.openpdf.text.pdf.PdfReader(pdf)) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
        }
        assertThat(quotationRepository.findById(created.id()).orElseThrow().getStatus())
                .isEqualTo(QuotationStatus.DRAFT);
        assertThat(quotationRepository.count()).isEqualTo(1);
        Long workOrderCount = tx().execute(status -> em.createQuery("select count(w) from WorkOrder w", Long.class)
                .getSingleResult());
        Long planCount = tx().execute(status -> em.createQuery("select count(p) from ProductionPlan p", Long.class)
                .getSingleResult());
        assertThat(workOrderCount).isZero();
        assertThat(planCount).isZero();
        assertThatThrownBy(() -> rescheduleService.previewSchedule(created.id(),
                new QuotationRescheduleDto.PreviewRequest(null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("발송완료");
    }

    @Test
    void pdfFailureAfterControllerSaveDoesNotRollBackDraftOrNewClient() {
        var failingPdf = org.mockito.Mockito.mock(QuotationPdfService.class);
        org.mockito.Mockito.when(failingPdf.generate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("PDF 생성 실패"));
        var failingStorage = new net.likelion.bebc25.prodflow.quotation.service.QuotationPdfStorageService(
                quotationRepository, service, failingPdf, storageClient, transactionManager);
        var controller = new QuotationController(service, null, null, failingStorage, null, null);
        var principal = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        var failure = org.junit.jupiter.api.Assertions.assertThrows(
                net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException.class,
                () -> controller.createPdf(principal,
                        newClientRequest("PDF 실패 거래처", List.of(item(100, 1000L)))));
        Long draftId = failure.getQuotationId();
        assertThat(quotationRepository.findById(draftId).orElseThrow().getStatus()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(itemRepository.findByQuotationIdOrderByIdAsc(draftId)).hasSize(1);
        assertThat(clientRepository.count()).isEqualTo(2);
        assertThat(pdfService.generate(service.getQuotationById(draftId))).isNotEmpty();
        assertThat(quotationRepository.count()).isEqualTo(1);
    }

    @Test
    void otherRolesCannotGenerateDraftPdf() {
        var draft = service.create(request(List.of(item(100, 1000L))), userId);
        for (UserRole role : List.of(UserRole.PRODUCTION_MANAGER, UserRole.INQUIRY_MANAGER)) {
            authenticateAs(role);
            assertThatThrownBy(() -> pdfService.generate(draft)).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void uploadsPdfAndStoresKeyOnlyAfterSuccess() {
        var draft = service.create(request(List.of(item(100, 1000L))), userId);
        String key = "quotations/" + draft.quotationNo() + ".pdf";
        tx().executeWithoutResult(status -> em.find(Quotation.class, draft.id()).changePdfObjectKey(key));
        org.mockito.Mockito.doAnswer(invocation -> {
            var independent = new TransactionTemplate(transactionManager);
            independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            String persistedKey = independent.execute(status ->
                    quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey());
            assertThat(persistedKey).isNull();
            return storePdf(invocation);
        }).when(storageClient).upload(org.mockito.ArgumentMatchers.eq(key), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyMap());
        org.mockito.Mockito.when(storageClient.createPresignedGetUrl(
                        org.mockito.ArgumentMatchers.eq(key), org.mockito.ArgumentMatchers.any()))
                .thenReturn("https://example.com/preview.pdf");
        var preview = pdfStorageService.prepare(draft.id());
        assertThat(preview.quotationId()).isEqualTo(draft.id());
        assertThat(preview.previewUrl()).isEqualTo("https://example.com/preview.pdf");
        assertThat(quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey()).isEqualTo(key);
        var order = org.mockito.Mockito.inOrder(storageClient);
        order.verify(storageClient).delete(key);
        order.verify(storageClient).upload(org.mockito.ArgumentMatchers.eq(key), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("application/pdf"), org.mockito.ArgumentMatchers.longThat(length -> length > 0), org.mockito.ArgumentMatchers.anyMap());
        org.mockito.Mockito.clearInvocations(storageClient);
        assertThat(pdfStorageService.getPreview(draft.id()).previewUrl()).isEqualTo(preview.previewUrl());
        org.mockito.Mockito.verify(storageClient, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(storageClient, org.mockito.Mockito.never()).upload(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyMap());
        service.updateDraft(draft.id(), request(List.of(item(200, 1000L))), userId);
        assertThat(quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey()).isNull();
        assertThatThrownBy(() -> pdfStorageService.getPreview(draft.id()))
                .isInstanceOf(net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException.class);
    }

    @Test
    void deletionAndUploadFailureClearPreviouslyStoredKeyAndRetryKeepsSameDraft() {
        var draft = service.create(request(List.of(item(100, 1000L))), userId);
        String key = "quotations/" + draft.quotationNo() + ".pdf";
        tx().executeWithoutResult(status -> em.find(Quotation.class, draft.id()).changePdfObjectKey(key));
        org.mockito.Mockito.doThrow(new RuntimeException("삭제 실패")).when(storageClient).delete(key);
        org.mockito.Mockito.doThrow(new RuntimeException("업로드 실패")).when(storageClient).upload(
                org.mockito.ArgumentMatchers.eq(key), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyMap());
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> pdfStorageService.prepare(draft.id()))
                    .isInstanceOf(net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException.class);
            assertThat(quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey()).isNull();
            assertThatThrownBy(() -> pdfStorageService.getPreview(draft.id()))
                    .isInstanceOf(net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException.class);
        }
        org.mockito.Mockito.doAnswer(this::storePdf).when(storageClient).upload(org.mockito.ArgumentMatchers.eq(key),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyMap());
        org.mockito.Mockito.when(storageClient.createPresignedGetUrl(
                org.mockito.ArgumentMatchers.eq(key), org.mockito.ArgumentMatchers.any())).thenReturn("https://example.com/retry.pdf");
        assertThat(pdfStorageService.prepare(draft.id()).previewUrl()).isEqualTo("https://example.com/retry.pdf");
        assertThat(quotationRepository.count()).isEqualTo(1);
        assertThat(quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey()).isEqualTo(key);
    }

    @Test
    void pdfGenerationFailureCommitsNullKeyWithoutDeletingStoredFile() {
        var draft = service.create(request(List.of(item(100, 1000L))), userId);
        tx().executeWithoutResult(status -> em.find(Quotation.class, draft.id())
                .changePdfObjectKey("quotations/old.pdf"));
        var failingPdf = org.mockito.Mockito.mock(QuotationPdfService.class);
        org.mockito.Mockito.when(failingPdf.generate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("한 페이지 초과"));
        var failingService = new net.likelion.bebc25.prodflow.quotation.service.QuotationPdfStorageService(
                quotationRepository, service, failingPdf, storageClient, transactionManager);
        assertThatThrownBy(() -> failingService.prepare(draft.id()))
                .isInstanceOfSatisfying(net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException.class,
                        failure -> assertThat(failure.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));
        assertThat(quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey()).isNull();
        org.mockito.Mockito.verifyNoInteractions(storageClient);
    }

    @Test
    void urlSigningFailureKeepsKeyNullEvenAfterUpload() {
        var draft = service.create(request(List.of(item(100, 1000L))), userId);
        org.mockito.Mockito.when(storageClient.createPresignedGetUrl(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("URL 서명 실패"));
        assertThatThrownBy(() -> pdfStorageService.prepare(draft.id()))
                .isInstanceOf(net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException.class);
        assertThat(quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey()).isNull();
        assertThat(quotationRepository.count()).isEqualTo(1);
    }

    @Test
    void storageOperationsRequirePermissionAndCannotOverwriteSentPdf() {
        var draft = service.create(request(List.of(item(100, 1000L))), userId);
        authenticateAs(UserRole.INQUIRY_MANAGER);
        assertThatThrownBy(() -> pdfStorageService.prepare(draft.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> pdfStorageService.getPreview(draft.id())).isInstanceOf(AccessDeniedException.class);
        authenticateAs(UserRole.ORDER_MANAGER);
        tx().executeWithoutResult(status -> {
            var quotation = em.find(Quotation.class, draft.id());
            quotation.changePdfObjectKey("quotations/sent.pdf");
            quotation.sent();
        });
        assertThatThrownBy(() -> pdfStorageService.prepare(draft.id())).isInstanceOf(IllegalArgumentException.class);
        assertThat(quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey()).isEqualTo("quotations/sent.pdf");
        org.mockito.Mockito.verifyNoInteractions(storageClient);
    }


    @Test
    void supplierContactKeepsCreatedByAfterWriterNameChanges() throws Exception {
        // 견적 생성 당시 작성자 정보
        var created = service.create(
                request(List.of(item(100, 1000L))),
                userId
        );

        String originalCreatedBy = created.createdBy();

        assertThat(originalCreatedBy)
                .isEqualTo("writer@example.com");

        // 견적 생성 후 사용자 이름 변경
        tx().executeWithoutResult(status ->
                ReflectionTestUtils.setField(
                        em.find(User.class, userId),
                        "name",
                        "변경된 이름"
                )
        );

        // 실제 사용자 이름이 변경되었는지 확인
        String changedUserName = tx().execute(status ->
                em.find(User.class, userId).getName()
        );

        assertThat(changedUserName)
                .isEqualTo("변경된 이름");

        // 견적 작성자 정보는 생성 당시 값 유지
        var detail = service.getQuotationById(created.id());

        assertThat(detail.createdBy())
                .isEqualTo(originalCreatedBy);

        // PDF에도 생성 당시 작성자 정보 표시
        try (var reader = new org.openpdf.text.pdf.PdfReader(
                pdfService.generate(detail)
        )) {
            String pdfText =
                    new org.openpdf.text.pdf.parser.PdfTextExtractor(reader)
                            .getTextFromPage(1);

            assertThat(pdfText)
                    .contains("담당자: " + originalCreatedBy)
                    .doesNotContain("변경된 이름");
        }
    }

}
