package net.likelion.bebc25.prodflow.global.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import net.likelion.bebc25.prodflow.auth.exception.AuthException;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationPdfDto;
import net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(QuotationPdfPreparationException.class)
    public ResponseEntity<QuotationPdfDto.FailureResponse> handlePdfFailure(QuotationPdfPreparationException ex) {
        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Quotation-Id", ex.getQuotationId().toString())
                .location(URI.create("/api/v1/quotations/" + ex.getQuotationId()))
                .body(QuotationPdfDto.FailureResponse.from(ex));
    }

    // 요청 파라미터 타입 변환 실패 시 호출됨 (400 Bad Request 응답)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = ex.getName() + " 파라미터 형식을 확인해주세요.";
        Class<?> requiredType = ex.getRequiredType();
        if (requiredType != null && requiredType.isEnum()) {
            String allowedValues = Arrays.stream(requiredType.getEnumConstants())
                    .map(value -> ((Enum<?>) value).name())
                    .collect(Collectors.joining(", "));
            message = ex.getName() + " 값은 " + allowedValues + " 중 하나여야 합니다.";
        }
        ApiErrorResponse response = ApiErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, message);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus()).body(response);
    }

    // 서비스 메서드 입력 검증 실패 시 400 응답
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
                .body(ApiErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE));
    }

    // ----------------- AuthException에 대한 정리 필요 (지금 GlobalExceptionHandler와 반환 형식이 다름)
    // 인증 관련 업무 예외 발생 시 지정된 상태 코드와 오류 메시지 반환
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuthException(AuthException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("code", ex.getCode(), "message", ex.getMessage()));
    }

    // @Valid 유효성 검증 실패할 경우에 호출됨 (400 Bad Request 응답)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        ApiErrorResponse response = ex.getMessage().isEmpty()
                ? ApiErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE)
                : ApiErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, ex.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus()).body(response);
    }

    // 비즈니스 업무 규칙 위반시 호출됨 (400 Bad Request 응답)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        ApiErrorResponse response = ex.getMessage().isEmpty()
                ? ApiErrorResponse.of(ErrorCode.BUSINESS_RULE_VIOLATION)
                : ApiErrorResponse.of(ErrorCode.BUSINESS_RULE_VIOLATION, ex.getMessage());
        return ResponseEntity.status(ErrorCode.BUSINESS_RULE_VIOLATION.getHttpStatus()).body(response);
    }

    // 인증 실패 또는 유효하지 않은 자격 증명 예외 처리 (401 Unauthorized 응답)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        ApiErrorResponse response = ex.getMessage().isEmpty()
                ? ApiErrorResponse.of(ErrorCode.UNAUTHORIZED_ACCESS)
                : ApiErrorResponse.of(ErrorCode.UNAUTHORIZED_ACCESS, ex.getMessage());
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED_ACCESS.getHttpStatus()).body(response);
    }

    // 유효하지 않은 자격에 대한 예외 처리 (401 Unauthorized 응답)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentialsException(BadCredentialsException ex) {
        ApiErrorResponse response = ex.getMessage().isEmpty()
                ? ApiErrorResponse.of(ErrorCode.UNAUTHORIZED_ACCESS)
                : ApiErrorResponse.of(ErrorCode.UNAUTHORIZED_ACCESS, ex.getMessage());
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED_ACCESS.getHttpStatus()).body(response);
    }

    // 권한이 없는 리소스 접근시 호출됨 (403 Forbidden 응답)
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthorizationDeniedException(AuthorizationDeniedException ex) {
        ApiErrorResponse response = ex.getMessage().isEmpty()
                ? ApiErrorResponse.of(ErrorCode.FORBIDDEN_OPERATION)
                : ApiErrorResponse.of(ErrorCode.FORBIDDEN_OPERATION, ex.getMessage());
        return ResponseEntity.status(ErrorCode.FORBIDDEN_OPERATION.getHttpStatus()).body(response);
    }

    // 권한이 없는 리소스 접근시 호출됨 (403 Forbidden 응답)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(ErrorCode.FORBIDDEN_OPERATION, ex.getMessage());
        return ResponseEntity.status(ErrorCode.FORBIDDEN_OPERATION.getHttpStatus()).body(response);
    }

    // 로직 안에서 객체 상태가 메소드를 실행하기 적절하지 않을 때 (400 BAD_REQUEST 응답)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalStateException(IllegalStateException ex) {
        ApiErrorResponse response = ex.getMessage().isEmpty()
                ? ApiErrorResponse.of(ErrorCode.WRONG_OBJECT_STATE)
                : ApiErrorResponse.of(ErrorCode.WRONG_OBJECT_STATE, ex.getMessage());
        return ResponseEntity.status(ErrorCode.WRONG_OBJECT_STATE.getHttpStatus()).body(response);
    }

    // 요청한 자원이 없을 때 (404 Not Found 응답)
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNoSuchElementException(NoSuchElementException ex) {
        ApiErrorResponse response = ex.getMessage().isEmpty()
                ? ApiErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND)
                : ApiErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage());
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus()).body(response);
    }

    // 서버 내부 오류가 발생했을 때 (500 Internal Server Error 응답)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneralException(Exception ex) {
        log.error(ex.getMessage());
        ApiErrorResponse response = ex.getMessage().isEmpty()
                ? ApiErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR)
                : ApiErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, ex.getMessage());
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus()).body(response);
    }

    // 외부 API 호출 또는 응답 처리 실패 시 호출됨 (502 Bad Gateway 응답)
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiErrorResponse> handleExternalApiException(ExternalApiException ex) {
        log.error("외부 API 연동 실패: {}", ex.getMessage(), ex);
        ApiErrorResponse response = ex.getMessage().isEmpty()
                ? ApiErrorResponse.of(ErrorCode.EXTERNAL_API_ERROR)
                : ApiErrorResponse.of(ErrorCode.EXTERNAL_API_ERROR, ex.getMessage());
        return ResponseEntity.status(ErrorCode.EXTERNAL_API_ERROR.getHttpStatus()).body(response);
    }

    // RestClient를 통한 외부 API 호출 실패 시 호출됨 (502 Bad Gateway 응답)
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiErrorResponse> handleRestClientException(RestClientException ex) {
        log.error("RestClient 외부 API 연동 실패: {}", ex.getMessage(), ex);
        ApiErrorResponse response = ex.getMessage().isEmpty()
                ? ApiErrorResponse.of(ErrorCode.EXTERNAL_API_ERROR)
                : ApiErrorResponse.of(ErrorCode.EXTERNAL_API_ERROR, ex.getMessage());
        return ResponseEntity.status(ErrorCode.EXTERNAL_API_ERROR.getHttpStatus()).body(response);
    }

    // RestClient를 통한 외부 API 호출 시 실패한 응답을 받을 경우 호출됨 (해당 응답에 대한 errorcode 호출)
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleRestClientResponseException(RestClientResponseException ex) {
        log.error("RestClient 외부 API 실패 응답: {}", ex.getMessage(), ex);
        ApiErrorResponse response
                = ApiErrorResponse.of("REST_CLIENT_RESPONSE_ERROR", ex.getMessage(), ex.getStatusCode().value());
        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "code", "HTTP_" + ex.getStatusCode().value(),
                "message", ex.getReason() == null ? "요청을 처리할 수 없습니다." : ex.getReason()));
    }

    // 요청 본문을 읽거나 변환할 수 없을 때 호출됨 (400 Bad Request 응답)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBody(HttpMessageNotReadableException ex) {
        ApiErrorResponse response = ex.getMessage().isEmpty()
                ? ApiErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE)
                : ApiErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, ex.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus()).body(response);
    }
}