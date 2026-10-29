package net.likelion.bebc25.prodflow.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 잘못된 요청값 또는 비즈니스 규칙 위반
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {

        ErrorCode errorCode = ErrorCode.BUSINESS_RULE_VIOLATION;

        Map<String, String> body = Map.of(
                "code", errorCode.getCode(),
                "message", ex.getMessage()
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(body);
    }

    // 처리하지 못한 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        log.error("처리되지 않은 예외가 발생했습니다.", ex);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        Map<String, String> body = Map.of(
                "code", errorCode.getCode(),
                "message", errorCode.getMessage()
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(body);
    }

}
