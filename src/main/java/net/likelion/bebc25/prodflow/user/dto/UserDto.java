package net.likelion.bebc25.prodflow.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;

import java.time.LocalDateTime;

public class UserDto {
    //직원 등록 요청
    @Schema(description = "직원 등록 요청 DTO")
    public record CreateRequest(
            @Schema(description = "직원 이메일", example = "employee@prodflow.local")
            @Email(message = "이메일 형식이 아닙니다.")
            @NotBlank(message = "이메일을 입력해주세요.")
            String email,
            @Schema(description = "프론트엔드에서 발급한 12자리 임시 비밀번호. 대문자·소문자·숫자·특수문자를 포함해야 합니다.",
                    format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
            @NotBlank(message = "임시비밀번호를 발급받아 주세요.")
            String temporaryPassword,
            @Schema(description = "직원 이름", example = "김철수")
            @NotBlank(message = "이름을 입력해주세요.")
            String name,
            @Schema(description = "직원 전화번호", example = "010-1234-5678")
            @NotBlank(message = "전화번호를 입력해주세요.")
            String phone,
            @Schema(description = "직원 권한", example = "ORDER_MANAGER")
            @NotNull(message = "권한을 설정해주세요.")
            UserRole role
    ) {
        public User toEntity(String encodedPassword) {
            return User.builder()
                    .password(encodedPassword)
                    .name(name)
                    .email(email)
                    .phone(phone)
                    .role(role)
                    .build();
        }
    }

    // 직원 정보 수정 요청
    @Schema(description = "직원 정보 수정 요청 DTO")
    public record UpdateRequest(
            @Schema(description = "수정할 직원 이름", example = "김철수")
            @NotBlank(message = "이름을 입력해주세요.")
            String name,
            @Schema(description = "수정할 직원 이메일", example = "employee@prodflow.local")
            @NotBlank(message = "이메일을 입력해주세요.")
            @Email(message = "이메일 형식이 아닙니다")
            String email,
            @Schema(description = "수정할 전화번호", example = "010-1234-5678")
            @NotBlank(message = "전화번호를 입력해주세요.")
            String phone,
            @Schema(description = "수정할 직원 권한", example = "ORDER_MANAGER")
            @NotNull(message = "권한을 설정해주세요.")
            UserRole role,
            @Schema(description = "수정할 계정 상태. ACTIVE 또는 INACTIVE만 허용합니다.",
                    allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE")
            @NotNull(message = "계정 상태를 설정해주세요.")
            UserStatus status,
            // 임시 비밀번호를 발급하지 않았다면 null
            @Schema(description = "선택 입력. 임시 비밀번호를 재발급할 때만 전달하고, 변경하지 않으면 null로 둡니다.",
                    format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
            String temporaryPassword

    ) {
    }

    // 직원 비밀번호 변경 요청
    @Schema(description = "최초 비밀번호 변경 요청 DTO")
    public record ChangePasswordRequest(
            @Schema(description = "현재 사용 중인 임시 비밀번호", format = "password",
                    accessMode = Schema.AccessMode.WRITE_ONLY)
            @NotBlank(message = "임시 비밀번호를 입력해주세요.")
            String temporaryPassword,
            @Schema(description = "변경할 새 비밀번호(8~20자)", format = "password",
                    minLength = 8, maxLength = 20, accessMode = Schema.AccessMode.WRITE_ONLY)
            @NotBlank(message = "새 비밀번호를 입력해주세요.")
            @NotBlank(message = "새 비밀번호를 입력해주세요.")
            @Size(min = 8, max = 20,
                    message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.")
            String newPassword,
            @Schema(description = "새 비밀번호 확인(새 비밀번호와 일치해야 함)", format = "password",
                    accessMode = Schema.AccessMode.WRITE_ONLY)
            @NotBlank(message = "비밀번호 확인을 입력해주세요.")
            @NotBlank(message = "비밀번호 확인을 입력해주세요.")
            String confirmPassword
    ) {
    }

    // 직원 조회 응답
    @Schema(description = "직원 정보 조회 응답 DTO")
    public record Response(
            @Schema(description = "직원 ID", example = "1")
            Long id,
            @Schema(description = "직원 이름", example = "김철수")
            String name,
            @Schema(description = "직원 이메일", example = "employee@prodflow.local")
            String email,
            @Schema(description = "전화번호", example = "010-1234-5678")
            String phone,
            @Schema(description = "직원 권한", example = "ORDER_MANAGER")
            UserRole role,
            @Schema(description = "계정 상태", example = "ACTIVE")
            UserStatus status,
            @Schema(description = "마지막 로그인 일시. 로그인 이력이 없으면 null일 수 있습니다.",
                    example = "2026-11-05T09:00:00")
            LocalDateTime lastLoginAt,
            @Schema(description = "직원 등록 일시", example = "2026-11-01T10:00:00")
            LocalDateTime createdAt,
            @Schema(description = "직원 정보 최종 수정 일시", example = "2026-11-05T14:00:00")
            LocalDateTime updatedAt,
            @Schema(description = "직원 삭제 일시. 삭제되지 않은 경우 null입니다.")
            LocalDateTime deletedAt,
            @Schema(description = "최초 비밀번호 변경 필요 여부", example = "true")
            boolean mustChangePassword
    ) {
        public static Response from(User user) {
            return new Response(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getRole(),
                    user.getStatus(),
                    user.getLastLoginAt(),
                    user.getCreatedAt(),
                    user.getUpdatedAt(),
                    user.getDeletedAt(),
                    user.isMustChangePassword()
            );
        }
    }

    // 계정관리 통계 (DELETED 제외)
    @Schema(description = "직원 계정 통계 응답 DTO")
    public record StatsResponse(
            @Schema(description = "전체 계정 수(ACTIVE + INACTIVE)", example = "13")
            long totalAccounts,

            @Schema(description = "활성 계정 수", example = "12")
            long activeAccounts,

            @Schema(description = "비활성 계정 수", example = "1")
            long inactiveAccounts
    ) {
    }

    // 내 정보 조회
    public record MeResponse(
            @Schema(description = "직원 ID", example = "1")
            Long userId,
            @Schema(description = "직원 이름", example = "김철수")
            String name,
            @Schema(description = "연락처", example = "010-1111-2222")
            String phone,
            @Schema(description = "직원 이메일", example = "employee@prodflow.local")
            String email,
            @Schema(description = "직원 권한", example = "ORDER_MANAGER")
            UserRole role,
            @Schema(description = "계정 상태", example = "ACTIVE")
            UserStatus status,
            @Schema(description = "최초 비밀번호 변경 필요 여부", example = "false")
            boolean mustChangePassword
    ) {
        public static MeResponse from(User user) {
            return new MeResponse(user.getId(), user.getName(), user.getPhone(), user.getEmail(), user.getRole(),
                    user.getStatus(), user.isMustChangePassword());
        }
    }

}
