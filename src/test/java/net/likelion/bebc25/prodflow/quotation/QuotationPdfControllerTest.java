package net.likelion.bebc25.prodflow.quotation;

import net.likelion.bebc25.prodflow.global.exception.GlobalExceptionHandler;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.quotation.controller.QuotationController;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationItemDto;
import net.likelion.bebc25.prodflow.quotation.service.QuotationPdfService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
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
    private final QuotationPdfService pdfs = mock(QuotationPdfService.class);
    private final QuotationController controller = new QuotationController(quotations, null, null, pdfs);

    @Test
    void createReturnsInlinePdfAndSavedDraftLocation() {
        var draft = QuotationPdfServiceTest.draft();
        var principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(7L);
        when(quotations.create(any(), eq(7L))).thenReturn(draft);
        when(pdfs.generate(draft)).thenReturn(new byte[]{1, 2, 3});

        var response = controller.createPdf(principal, request(), new MockHttpServletResponse());

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/v1/quotations/123");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("QT-20261104-123.pdf");
        assertThat(response.getHeaders().getFirst("X-Quotation-Id")).isEqualTo("123");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).containsExactly(1, 2, 3);
    }

    @Test
    void generationFailureStillExposesSavedDraftIdForRetry() {
        var draft = QuotationPdfServiceTest.draft();
        var principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(7L);
        when(quotations.create(any(), eq(7L))).thenReturn(draft);
        when(pdfs.generate(draft)).thenThrow(new IllegalArgumentException("한 페이지 초과"));
        var servletResponse = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.createPdf(principal, request(), servletResponse))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(servletResponse.getHeader("X-Quotation-Id")).isEqualTo("123");
        assertThat(servletResponse.getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/v1/quotations/123");
    }

    @Test
    void getRegeneratesExistingDraftAndDoesNotCreateAnotherQuotation() throws Exception {
        var draft = QuotationPdfServiceTest.draft();
        when(quotations.getQuotationById(123L)).thenReturn(draft);
        when(pdfs.generate(draft)).thenReturn(new byte[]{1, 2, 3});
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/v1/quotations/123/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verify(quotations).getQuotationById(123L);
    }

    @Test
    void cannotGeneratePdfWhenDraftSaveFails() {
        var principal = mock(CustomUserDetails.class);
        when(quotations.create(any(), any())).thenThrow(new IllegalArgumentException("잘못된 견적"));
        assertThatThrownBy(() -> controller.createPdf(principal, request(), new MockHttpServletResponse()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(pdfs);
    }

    @Test
    void updateReturnsPdfForSameQuotationAndPassesIdAndEditor() {
        var draft = QuotationPdfServiceTest.draft();
        var principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(7L);
        var request = request();
        when(quotations.updateDraft(123L, request, 7L)).thenReturn(draft);
        when(pdfs.generate(draft)).thenReturn(new byte[]{1, 2, 3});

        var response = controller.updatePdf(123L, principal, request, new MockHttpServletResponse());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst("X-Quotation-Id")).isEqualTo("123");
        assertThat(response.getBody()).containsExactly(1, 2, 3);
        verify(quotations).updateDraft(123L, request, 7L);
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
        when(quotations.create(any(), eq(7L))).thenReturn(draft);
        when(pdfs.generate(draft)).thenReturn(new byte[]{1, 2, 3});
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
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF));
            var captured = org.mockito.ArgumentCaptor.forClass(QuotationDto.CreateRequest.class);
            verify(quotations).create(captured.capture(), eq(7L));
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
            org.mockito.Mockito.verifyNoMoreInteractions(quotations);
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

    private QuotationDto.CreateRequest request() {
        return new QuotationDto.CreateRequest(1L, LocalDate.of(2026, 12, 10), null, false,
                List.of(new QuotationItemDto.CreateRequest(1L, 100, 1000L)), null);
    }
}
