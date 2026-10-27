package net.likelion.bebc25.prodflow.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;

import java.time.LocalDateTime;

public class UserDto {
    //직원 등록 요청
    public record CreateRequest(
            @NotBlank String email,
            @NotBlank String password,
            @NotBlank String name,
            @NotBlank String phone,
            @NotNull UserRole role
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
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank String phone,
            @NotNull UserRole role,

            // 임시 비밀번호를 발급하지 않았다면 null 또는 빈 문자열
            String password
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
