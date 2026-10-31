package net.likelion.bebc25.prodflow.global.exception;

import lombok.extern.slf4j.Slf4j;
import net.likelion.bebc25.prodflow.auth.exception.AuthException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        ErrorCode errorCode = ErrorCode.BUSINESS_RULE_VIOLATION;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Map.of("code", errorCode.getCode(), "message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        log.error("처리되지 않은 예외가 발생했습니다.", ex);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Map.of("code", errorCode.getCode(), "message", errorCode.getMessage()));
    }
}
