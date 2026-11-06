package net.likelion.bebc25.prodflow.global.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ApiErrorResponse(
        String code,                  // 시스템 내부 비즈니스 예외 코드
        String message,               // 클라이언트 및 사용자용 에러 설명 메시지
        int status,                   // HTTP 응답 상태 코드 (예: 400, 403, 404, 500)
        LocalDateTime timestamp,      // 예외 발생 일시
        List<FieldErrorDetail> errors // Bean Validation 유효성 검증 실패 상세 목록
) {
    // 필드별 유효성 검증 실패 정보 저장 Record
    public record FieldErrorDetail(
            String field,             // 검증 실패 대상 필드명
            String rejectedValue,     // 클라이언트가 전송하여 거부된 입력값
            String reason             // 검증 실패 사유
    ) {
    }

    // ErrorCode 기반 정적 팩토리 메서드
    public static ApiErrorResponse of(ErrorCode errorCode) {
        return new ApiErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                errorCode.getHttpStatus().value(),
                LocalDateTime.now(),
                List.of()
        );
    }

    // ErrorCode 및 예외 세부 메시지 기반 정적 팩토리 메서드
    public static ApiErrorResponse of(ErrorCode errorCode, String message) {
        return new ApiErrorResponse(
                errorCode.getCode(),
                message,
                errorCode.getHttpStatus().value(),
                LocalDateTime.now(),
                List.of()
        );
    }

    // ErrorCode 및 필드 유효성 검증 실패 목록 기반 정적 팩토리 메서드
    public static ApiErrorResponse of(ErrorCode errorCode, List<FieldErrorDetail> errors) {
        return new ApiErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                errorCode.getHttpStatus().value(),
                LocalDateTime.now(),
                errors
        );
    }

    // code, message, status를 각각 받는 정적 팩토리 메소드
    public static ApiErrorResponse of(String code, String message, int status) {
        return new ApiErrorResponse(
                code,
                message,
                status,
                LocalDateTime.now(),
                List.of()
        );
    }
}
