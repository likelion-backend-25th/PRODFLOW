package net.likelion.bebc25.prodflow.global.exception;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void pdfExceptionsUseGlobalHandlerAndPreserveRetryContract() throws Exception {
        for (HttpStatus httpStatus : new HttpStatus[]{
                HttpStatus.BAD_REQUEST, HttpStatus.CONFLICT, HttpStatus.BAD_GATEWAY}) {
            QuotationPdfPreparationException exception = new QuotationPdfPreparationException(
                    123L, httpStatus, "PDF_TEST_ERROR", "PDF 준비 실패",
                    new RuntimeException("외부 서비스 내부 상세"));
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new ExceptionController(exception))
                    .setControllerAdvice(handler).build();
            mvc.perform(get("/test/exception"))
                    .andExpect(status().is(httpStatus.value()))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("X-Quotation-Id", "123"))
                    .andExpect(header().string("Location", "/api/v1/quotations/123"))
                    .andExpect(jsonPath("$.code").value("PDF_TEST_ERROR"))
                    .andExpect(jsonPath("$.message").value("PDF 준비 실패"))
                    .andExpect(jsonPath("$.quotationId").value(123))
                    .andExpect(jsonPath("$.retryUrl").value("/api/v1/quotations/123/pdf/regenerate"))
                    .andExpect(jsonPath("$.previewUrl").doesNotExist());
        }
    }

    @Test
    void externalApiFailureReturns502WithoutExposingInternalDetails() {
        ResponseEntity<ApiErrorResponse> response = handler.handleExternalApiException(
                new ExternalApiException("공휴일 API 내부 상세", new RuntimeException("원인")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().code()).isEqualTo("EXTERNAL_API_ERROR");
        assertThat(response.getBody().message()).isEqualTo("공휴일 API 내부 상세");
        assertThat(response.getBody().status()).isEqualTo(502);
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().errors()).isEmpty();
    }

    @Test
    void authenticationExceptionsUse401HandlerAndPreserveMessage() throws Exception {
        for (RuntimeException exception : new RuntimeException[]{
                new AuthenticationCredentialsNotFoundException("작성자 계정을 찾을 수 없습니다."),
                new BadCredentialsException("자격 증명이 올바르지 않습니다.")}) {
            assertHandled(exception, 401, "UNAUTHORIZED_ACCESS");
        }
    }

    @Test
    void accessDeniedExceptionsUse403HandlerAndPreserveMessage() throws Exception {
        for (RuntimeException exception : new RuntimeException[]{
                new AccessDeniedException("사용 중인 계정만 견적을 작성할 수 있습니다."),
                new AccessDeniedException("비밀번호를 먼저 변경해주세요."),
                new AuthorizationDeniedException("접근 권한이 없습니다.")}) {
            assertHandled(exception, 403, "FORBIDDEN_OPERATION");
        }
    }

    private void assertHandled(RuntimeException exception, int statusCode, String code) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ExceptionController(exception))
                .setControllerAdvice(handler).build();

        mvc.perform(get("/test/exception"))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(exception.getMessage()))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void responseStatusExceptionsUseStandardErrorBody() throws Exception {
        ResponseStatusException exception = new ResponseStatusException(
                HttpStatus.CONFLICT, "취소할 수 없는 수주입니다.");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ExceptionController(exception))
                .setControllerAdvice(handler).build();
        mvc.perform(get("/test/exception"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HTTP_409"))
                .andExpect(jsonPath("$.message").value("취소할 수 없는 수주입니다."))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void responseStatusWithoutReasonUsesDefaultMessage() {
        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_REQUEST));
        assertThat(response.getBody().message()).isEqualTo("요청을 처리할 수 없습니다.");
        assertThat(response.getBody().status()).isEqualTo(400);
    }

    @RestController
    static class ExceptionController {
        private final RuntimeException exception;

        ExceptionController(RuntimeException exception) {
            this.exception = exception;
        }

        @GetMapping("/test/exception")
        public void fail() {
            throw exception;
        }
    }
}
