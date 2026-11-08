package net.likelion.bebc25.prodflow.quotation;

import net.likelion.bebc25.prodflow.global.exception.GlobalExceptionHandler;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.quotation.controller.QuotationController;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto;
import net.likelion.bebc25.prodflow.quotation.service.QuotationSendService;
import net.likelion.bebc25.prodflow.client_notification.enums.ClientNotiStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuotationSendControllerTest {
    private final QuotationSendService service = mock(QuotationSendService.class);
    private final String token = "a".repeat(64) + "." + "b".repeat(64);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new QuotationController(null, null, null, null, service, null))
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
    void sendsConfirmedDocumentAndReturnsSeparateEmailResult() throws Exception {
        when(service.send(eq(123L), any(), eq(7L)))
                .thenReturn(QuotationSendDto.Response.from(123L, ClientNotiStatus.SENT, false));
        mvc.perform(post("/api/v1/quotations/123/send").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.quotationId").value(123))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.emailStatus").value("SENT"))
                .andExpect(jsonPath("$.duplicateRequest").value(false));
        verify(service).send(123L, new QuotationSendDto.Request(token), 7L);
    }

    @Test
    void missingOrMalformedConfirmationDoesNotCallService() throws Exception {
        for (String body : new String[]{"{}", "{\"confirmationToken\":\"\"}",
                "{\"confirmationToken\":\"invalid\"}"}) {
            mvc.perform(post("/api/v1/quotations/123/send").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void reconfirmationConflictIsReturnedWithoutSendingAnotherDocument() throws Exception {
        when(service.send(eq(123L), any(), eq(7L)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "PDF를 다시 확인해주세요."));
        mvc.perform(post("/api/v1/quotations/123/send").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationToken\":\"" + token + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PDF를 다시 확인해주세요."));
    }
}
