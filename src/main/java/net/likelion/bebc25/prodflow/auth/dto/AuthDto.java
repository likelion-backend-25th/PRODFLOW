package net.likelion.bebc25.prodflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;

public final class AuthDto {
    private AuthDto() {
    }

    public record LoginRequest(
            @NotBlank(message = "이메일을 입력해주세요.")
            @Email(message = "올바른 이메일 형식이 아닙니다.") String email,
            @NotBlank(message = "비밀번호를 입력해주세요.") String password
    ) {
    }

    // Refresh Token은 응답 본문에 담지 않고 HttpOnly Cookie로만 전달한다.
    public record LoginResponse(
            String accessToken,
            String tokenType,
            Long userId,
            String name,
            String email,
            UserRole role,
            boolean mustChangePassword
    ) {
        public static LoginResponse of(String accessToken, User user) {
            return new LoginResponse(accessToken, "Bearer", user.getId(), user.getName(),
                    user.getEmail(), user.getRole(), user.isMustChangePassword());
        }
    }

    public record RefreshResponse(
            String accessToken,
            String tokenType,
            boolean mustChangePassword
    ) {
    }

    public record MeResponse(
            Long userId,
            String name,
            String email,
            UserRole role,
            UserStatus status,
            boolean mustChangePassword
    ) {
        public static MeResponse from(User user) {
            return new MeResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                    user.getStatus(), user.isMustChangePassword());
        }
    }
}
