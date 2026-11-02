package net.likelion.bebc25.prodflow.global.exception;

import lombok.extern.slf4j.Slf4j;
import net.likelion.bebc25.prodflow.auth.exception.AuthException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuthException(AuthException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("code", ex.getCode(), "message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage() == null ? "입력값을 확인해주세요."
                        : error.getDefaultMessage()).orElse("입력값을 확인해주세요.");
        return ResponseEntity.badRequest()
                .body(Map.of("code", ErrorCode.INVALID_INPUT_VALUE.getCode(), "message", message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "code", "HTTP_" + ex.getStatusCode().value(),
                "message", ex.getReason() == null ? "요청을 처리할 수 없습니다." : ex.getReason()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleInvalidBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST", "message", "요청 본문 형식을 확인해주세요."));
    }

    // 잘못된 요청값 또는 비즈니스 규칙 위반
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {

        ErrorCode errorCode = ErrorCode.BUSINESS_RULE_VIOLATION;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Map.of("code", errorCode.getCode(), "message", ex.getMessage()));
    }

    // 처리하지 못한 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        log.error("처리되지 않은 예외가 발생했습니다.", ex);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Map.of("code", errorCode.getCode(), "message", errorCode.getMessage()));
    }

}
