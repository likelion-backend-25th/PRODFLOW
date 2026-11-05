package net.likelion.bebc25.prodflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;

public final class AuthDto {
    private AuthDto() {
    }

    // 로그인 요청
    @Schema(description = "로그인 요청 DTO")
    public record LoginRequest(
            @Schema(description = "로그인 이메일", example = "employee@prodflow.local", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "이메일을 입력해주세요.")
            @Email(message = "올바른 이메일 형식이 아닙니다.") String email,
            @Schema(description = "로그인 비밀번호", example = "ExamplePassword123!", format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "비밀번호를 입력해주세요.") String password
    ) {
    }

    // Refresh Token은 응답 본문에 담지 않고 HttpOnly Cookie로만 전달한다.
    @Schema(description = "로그인 성공 응답 DTO. Refresh Token은 HttpOnly 쿠키로 전달됩니다.")
    public record LoginResponse(
            @Schema(description = "JWT Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
            String accessToken,
            @Schema(description = "토큰 타입", example = "Bearer")
            String tokenType,
            @Schema(description = "로그인한 직원 ID", example = "1")
            Long userId,
            @Schema(description = "직원 이름", example = "김철수")
            String name,
            @Schema(description = "직원 이메일", example = "employee@prodflow.local")
            String email,
            @Schema(description = "직원 권한", example = "ORDER_MANAGER")
            UserRole role,
            @Schema(description = "최초 비밀번호 변경 필요 여부", example = "true")
            boolean mustChangePassword
    ) {
        public static LoginResponse of(String accessToken, User user) {
            return new LoginResponse(accessToken, "Bearer", user.getId(), user.getName(),
                    user.getEmail(), user.getRole(), user.isMustChangePassword());
        }
    }

    @Schema(description = "JWT 재발급 성공 응답 DTO. 새 Refresh Token은 HttpOnly 쿠키로 전달됩니다.")
    public record RefreshResponse(
            @Schema(description = "새로 발급한 JWT Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
            String accessToken,
            @Schema(description = "토큰 타입", example = "Bearer")
            String tokenType,
            @Schema(description = "최초 비밀번호 변경 필요 여부", example = "false")
            boolean mustChangePassword
    ) {
    }

}
