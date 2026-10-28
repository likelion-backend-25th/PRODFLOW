package net.likelion.bebc25.prodflow.user.dto;

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
    public record CreateRequest(
            @Email(message = "이메일 형식이 아닙니다.")
            @NotBlank(message = "이메일을 입력해주세요.")
            String email,
            @NotBlank(message = "임시비밀번호를 발급받아 주세요.")
            String temporaryPassword,
            @NotBlank(message = "이름을 입력해주세요.")
            String name,
            @NotBlank(message = "전화번호를 입력해주세요.")
            String phone,
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
    public record UpdateRequest(
            @NotBlank(message = "이름을 입력해주세요.")
            String name,
            @NotBlank(message = "이메일을 입력해주세요.")
            @Email(message = "이메일 형식이 아닙니다")
            String email,
            @NotBlank(message = "전화번호를 입력해주세요.")
            String phone,
            @NotNull(message = "권한을 설정해주세요.")
            UserRole role,

            // 임시 비밀번호를 발급하지 않았다면 null
            String temporaryPassword

    ) {
    }

    // 직원 비밀번호 변경 요청
    public record ChangePasswordRequest(
            @NotBlank(message = "임시 비밀번호를 입력해주세요.")
            String temporaryPassword,
            @NotBlank(message = "새 비밀번호를 입력해주세요.")
            @Size(min = 8, max = 20,
                    message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.")
            String newPassword,
            @NotBlank(message = "비밀번호 확인을 입력해주세요.")
            String confirmPassword
    ) {
    }

    // 직원 응답
    public record Response(
            Long id,
            String name,
            String email,
            String phone,
            UserRole role,
            UserStatus status,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
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

}
