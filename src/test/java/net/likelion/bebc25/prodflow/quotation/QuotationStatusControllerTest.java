package net.likelion.bebc25.prodflow.quotation;

import java.util.NoSuchElementException;

import net.likelion.bebc25.prodflow.global.exception.GlobalExceptionHandler;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.quotation.controller.QuotationController;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationStatusDto;
import net.likelion.bebc25.prodflow.quotation.service.QuotationStatusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuotationStatusControllerTest {
    private final QuotationStatusService service = mock(QuotationStatusService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new QuotationController(null, null, null, null, null, service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(7L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deletesDraftUsingAuthenticatedOperator() throws Exception {
        mvc.perform(delete("/api/v1/quotations/123")).andExpect(status().isNoContent());
        verify(service).deleteDraft(123L, 7L);
    }

    @Test
    void draftDeletionReturnsConflictAndNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "초안만 삭제 가능"))
                .when(service).deleteDraft(123L, 7L);
        mvc.perform(delete("/api/v1/quotations/123")).andExpect(status().isConflict());
        org.mockito.Mockito.doThrow(new NoSuchElementException("존재하지 않는 견적"))
                .when(service).deleteDraft(456L, 7L);
        mvc.perform(delete("/api/v1/quotations/456")).andExpect(status().isNotFound());
    }

    @Test
    void approvalUsesAuthenticatedOperatorAndReturnsChangedDetail() throws Exception {
        var detail = QuotationPdfServiceTest.draft();
        when(detail.status()).thenReturn(QuotationStatus.APPROVED);
        when(service.approve(123L, 7L)).thenReturn(detail);
        mvc.perform(patch("/api/v1/quotations/123/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        verify(service).approve(123L, 7L);
    }

    @Test
    void approvalReturnsConflictAndNotFound() throws Exception {
        when(service.approve(123L, 7L)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "승인 불가 상태"));
        mvc.perform(patch("/api/v1/quotations/123/approve")).andExpect(status().isConflict());
        when(service.approve(456L, 7L)).thenThrow(new NoSuchElementException("존재하지 않는 견적"));
        mvc.perform(patch("/api/v1/quotations/456/approve")).andExpect(status().isNotFound());
    }

    @Test
    void rejectionUsesAuthenticatedOperatorAndReturnsChangedDetail() throws Exception {
        var detail = QuotationPdfServiceTest.draft();
        when(detail.status()).thenReturn(QuotationStatus.REJECTED);
        when(service.reject(123L, 7L)).thenReturn(detail);
        mvc.perform(patch("/api/v1/quotations/123/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        verify(service).reject(123L, 7L);
    }

    @Test
    void cancellationUsesAuthenticatedOperatorAndReturnsChangedDetail() throws Exception {
        var detail = QuotationPdfServiceTest.draft();
        when(detail.status()).thenReturn(QuotationStatus.CANCELLED);
        when(service.cancel(123L, 7L)).thenReturn(detail);
        mvc.perform(patch("/api/v1/quotations/123/cancel"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(service).cancel(123L, 7L);
    }

    @Test
    void invalidStateAndUnknownQuotationUseExistingErrorResponses() throws Exception {
        when(service.reject(123L, 7L)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "처리 불가 상태"));
        mvc.perform(patch("/api/v1/quotations/123/reject"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("처리 불가 상태"));
        when(service.cancel(123L, 7L)).thenThrow(new NoSuchElementException("존재하지 않는 견적"));
        mvc.perform(patch("/api/v1/quotations/123/cancel"))
                .andExpect(status().isNotFound());
    }

    @Test
    void manualExpirationUsesAuthenticatedOperatorAndReturnsChangedCount() throws Exception {
        when(service.expireQuotations(7L)).thenReturn(QuotationStatusDto.ExpirationResponse.from(3));
        mvc.perform(post("/api/v1/quotations/expire"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiredCount").value(3));
        verify(service).expireQuotations(7L);
    }

    @Test
    void manualExpirationWithNoCandidatesReturnsZero() throws Exception {
        when(service.expireQuotations(7L)).thenReturn(QuotationStatusDto.ExpirationResponse.from(0));
        mvc.perform(post("/api/v1/quotations/expire"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiredCount").value(0));
    }
}
