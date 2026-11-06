package net.likelion.bebc25.prodflow.quotation;

import net.likelion.bebc25.prodflow.global.exception.GlobalExceptionHandler;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.quotation.controller.QuotationController;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationItemDto;
import net.likelion.bebc25.prodflow.quotation.service.QuotationPdfStorageService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuotationPdfControllerTest {
    private final QuotationService quotations = mock(QuotationService.class);
    private final QuotationPdfStorageService pdfs = mock(QuotationPdfStorageService.class);
    private final QuotationController controller = new QuotationController(quotations, null, null, pdfs, null, null);

    @Test
    void draftSaveEndpointsReturnDetailWithoutPreparingPdf() throws Exception {
        var draft = QuotationPdfServiceTest.draft();
        var principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(7L);
        when(quotations.create(any(), eq(7L))).thenReturn(draft);
        when(quotations.updateDraft(eq(draft.id()), any(), eq(7L))).thenReturn(draft);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null));
        String body = """
                {"clientId":1,"requestedDueDate":"2027-06-30","vatIncluded":false,
                 "items":[{"productId":1,"quantity":100,"unitPrice":1000}]}
                """;
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/v1/quotations").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/quotations/" + draft.id()))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.id").value(draft.id()))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.status").value("DRAFT"));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/v1/quotations/" + draft.id())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.id").value(draft.id()));
            verify(quotations).create(any(), eq(7L));
            verify(quotations).updateDraft(eq(draft.id()), any(), eq(7L));
            verifyNoInteractions(pdfs);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createReturnsPreviewJsonAndSavedDraftLocation() {
        var draft = QuotationPdfServiceTest.draft();
        var principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(7L);
        when(pdfs.create(any(), eq(7L))).thenReturn(preview());

        var response = controller.createPdf(principal, request());

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().previewUrl()).isEqualTo("https://example.com/signed.pdf");
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/v1/quotations/123");

        assertThat(response.getHeaders().getFirst("X-Quotation-Id")).isEqualTo("123");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().quotationId()).isEqualTo(123L);
    }

    @Test
    void generationFailureStillExposesSavedDraftIdForRetry() throws Exception {
        var principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(7L);
        when(pdfs.create(any(), eq(7L))).thenThrow(
                new net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException(
                        123L, org.springframework.http.HttpStatus.BAD_REQUEST,
                        "PDF_GENERATION_FAILED", "한 페이지 초과", null));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null));
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/v1/quotations/pdf").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"clientId":1,"requestedDueDate":"2027-06-30","vatIncluded":false,
                                     "items":[{"productId":1,"quantity":100,"unitPrice":1000}]}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("X-Quotation-Id", "123"))
                    .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/quotations/123"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.quotationId").value(123));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void getOnlyRetrievesUploadedPreviewAndDoesNotRegenerate() throws Exception {
        when(pdfs.getPreview(123L)).thenReturn(preview());
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/v1/quotations/123/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.previewUrl").value("https://example.com/signed.pdf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.clientCompany").value("테스트 거래처"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.clientEmail").value("client@example.com"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verify(pdfs).getPreview(123L);
        org.mockito.Mockito.verify(pdfs, org.mockito.Mockito.never()).prepare(any());
        verifyNoInteractions(quotations);
    }

    @Test
    void notReadyPreviewReturnsConflictWithIdAndNoCache() throws Exception {
        when(pdfs.getPreview(123L)).thenThrow(
                new net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException(
                        123L, org.springframework.http.HttpStatus.CONFLICT, "PDF_NOT_READY", "PDF를 재생성해주세요.", null));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/v1/quotations/123/pdf"))
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.quotationId").value(123));
    }

    @Test
    void regenerationReturnsPreviewWithoutCreatingAnotherDraft() throws Exception {
        when(pdfs.prepare(123L)).thenReturn(preview());
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/quotations/123/pdf/regenerate"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.quotationId").value(123));
        verifyNoInteractions(quotations);
    }

    @Test
    void regenerationFailureReturnsIdAndRetryUrlWithoutOldPreview() throws Exception {
        when(pdfs.prepare(123L)).thenThrow(
                new net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException(
                        123L, new RuntimeException("S3 업로드 실패")));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/quotations/123/pdf/regenerate"))
                .andExpect(status().isBadGateway())
                .andExpect(header().string("X-Quotation-Id", "123"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.quotationId").value(123))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.retryUrl").value("/api/v1/quotations/123/pdf/regenerate"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.previewUrl").doesNotExist());
        verifyNoInteractions(quotations);
    }

    @Test
    void cannotGeneratePdfWhenDraftSaveFails() {
        var principal = mock(CustomUserDetails.class);
        when(pdfs.create(any(), any())).thenThrow(new IllegalArgumentException("잘못된 견적"));
        assertThatThrownBy(() -> controller.createPdf(principal, request()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(quotations);
    }

    @Test
    void updateReturnsPdfForSameQuotationAndPassesIdAndEditor() {
        var draft = QuotationPdfServiceTest.draft();
        var principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(7L);
        var request = request();
        when(pdfs.update(123L, request, 7L)).thenReturn(preview());

        var response = controller.updatePdf(123L, principal, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().previewUrl()).isEqualTo("https://example.com/signed.pdf");
        assertThat(response.getHeaders().getFirst("X-Quotation-Id")).isEqualTo("123");
        assertThat(response.getBody().quotationId()).isEqualTo(123L);
        verify(pdfs).update(123L, request, 7L);
    }

    @Test
    void putPdfValidatesBodyBeforeCallingUpdateService() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/quotations/123/pdf").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(quotations, pdfs);
    }

    @Test
    void postPdfAcceptsExistingClientWithoutNameAndRejectsBlankNewClientEmployee() throws Exception {
        var draft = QuotationPdfServiceTest.draft();
        var principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(7L);
        when(pdfs.create(any(), eq(7L))).thenReturn(preview());
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String body = """
                {"clientId":1,"requestedDueDate":"2027-06-30",
                 "vatIncluded":false,"items":[{"productId":1,"quantity":100,"unitPrice":1000}]}
                """;
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null));
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/v1/quotations/pdf").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
            var captured = org.mockito.ArgumentCaptor.forClass(QuotationDto.CreateRequest.class);
            verify(pdfs).create(captured.capture(), eq(7L));
            assertThat(captured.getValue().clientId()).isEqualTo(1L);

            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/v1/quotations/pdf").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"newClient":{"company":"회사","employee":"","email":"client@example.com",
                                      "phone":"010-1111-2222","zipcode":"12345","roadAddress":"서울 테스트로",
                                      "detailAddress":"3층"},"requestedDueDate":"2027-06-30",
                                     "items":[{"productId":1,"quantity":100,"unitPrice":1000}]}
                                    """))
                    .andExpect(status().isBadRequest());
            org.mockito.Mockito.verifyNoMoreInteractions(pdfs);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void detailResponseUsesRenamedClientFields() throws Exception {
        var draft = QuotationPdfServiceTest.draft();
        when(draft.clientAddress()).thenReturn("서울 테스트로 1");
        when(quotations.getQuotationById(123L)).thenReturn(draft);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/v1/quotations/123"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.clientName").value("담당자"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.clientEmail").value("client@example.com"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.clientPhone").value("02-1234-5678"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.clientAddress").value("서울 테스트로 1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.clientEmployee").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.address").doesNotExist());
    }

    private net.likelion.bebc25.prodflow.quotation.dto.QuotationPdfDto.PreviewResponse preview() {
        return new net.likelion.bebc25.prodflow.quotation.dto.QuotationPdfDto.PreviewResponse(
                123L, "QT-20261104-123", "테스트 거래처", "client@example.com",
                "https://example.com/signed.pdf", java.time.Instant.now().plusSeconds(600), "a".repeat(64) + "." + "b".repeat(64));
    }
    private QuotationDto.CreateRequest request() {
        return new QuotationDto.CreateRequest(1L, LocalDate.of(2026, 12, 10), null, false,
                List.of(new QuotationItemDto.CreateRequest(1L, 100, 1000L)), null);
    }
}
