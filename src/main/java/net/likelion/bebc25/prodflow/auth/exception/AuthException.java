package net.likelion.bebc25.prodflow.auth.exception;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public AuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static AuthException unauthorized(String message) {
        return new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED_ACCESS", message);
    }

    public static AuthException forbidden(String message) {
        return new AuthException(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", message);
    }

    public static AuthException badRequest(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_INPUT_VALUE", message);
    }
}
