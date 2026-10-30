package net.likelion.bebc25.prodflow.auth.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.auth.dto.AuthDto;
import net.likelion.bebc25.prodflow.auth.entity.RefreshToken;
import net.likelion.bebc25.prodflow.auth.exception.AuthException;
import net.likelion.bebc25.prodflow.auth.repository.RefreshTokenRepository;
import net.likelion.bebc25.prodflow.global.security.jwt.JwtProvider;
import net.likelion.bebc25.prodflow.user.dto.UserDto;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public record LoginResult(AuthDto.LoginResponse response, String refreshToken) {
    }

    public record RefreshResult(AuthDto.RefreshResponse response, String refreshToken) {
    }

    @Transactional
    public LoginResult login(AuthDto.LoginRequest request) {
        // 존재하지 않는 이메일과 비밀번호 불일치에 같은 에러를 반환하여 계정 열거를 방지한다.
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> AuthException.unauthorized("이메일 또는 비밀번호가 올바르지 않습니다."));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw AuthException.unauthorized("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        requireActive(user);

        String accessToken = issueAccessToken(user);
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        saveOrRotateRefreshToken(user, refreshToken);
        user.updateLastLoginAt();
        return new LoginResult(AuthDto.LoginResponse.of(accessToken, user), refreshToken);
    }

    // 토큰 재사용/만료 시 삭제 후 예외가 발생하더라도 삭제가 롤백되지 않도록 한다.
    @Transactional(noRollbackFor = AuthException.class)
    public RefreshResult refresh(String refreshToken) {
        Long userId = validatedRefreshUserId(refreshToken);
        // 비활성/삭제/탈퇴된 계정은 만료 여부와 관계없이 재발급 불가.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.unauthorized("재로그인이 필요합니다."));
        requireActive(user);

        RefreshToken stored = refreshTokenRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> AuthException.unauthorized("재로그인이 필요합니다."));
        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw AuthException.unauthorized("Refresh Token이 만료되었습니다.");
        }
        if (!MessageDigest.isEqual(stored.getTokenHash().getBytes(StandardCharsets.UTF_8),
                hash(refreshToken).getBytes(StandardCharsets.UTF_8))) {
            // 토큰 재사용이 의심되는 경우 현재 세션도 폐기.
            refreshTokenRepository.delete(stored);
            throw AuthException.unauthorized("재로그인이 필요합니다.");
        }
        String nextRefreshToken = jwtProvider.createRefreshToken(userId);
        stored.rotate(hash(nextRefreshToken), refreshExpiry());
        String nextAccessToken = issueAccessToken(user);
        return new RefreshResult(new AuthDto.RefreshResponse(nextAccessToken, "Bearer",
                user.isMustChangePassword()), nextRefreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || !jwtProvider.validateToken(refreshToken)
                || !jwtProvider.isRefreshToken(refreshToken)) {
            return; // 로그아웃은 멱등: 존재하지 않는 세션도 정상 종료
        }
        Long userId = jwtProvider.getUserId(refreshToken);
        refreshTokenRepository.findByUserIdForUpdate(userId).ifPresent(stored -> {
            if (MessageDigest.isEqual(stored.getTokenHash().getBytes(StandardCharsets.UTF_8),
                    hash(refreshToken).getBytes(StandardCharsets.UTF_8))) {
                refreshTokenRepository.delete(stored);
            }
        });
    }

    @Transactional
    public void changePassword(Long userId, UserDto.ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.unauthorized("사용자를 찾을 수 없습니다."));
        requireActive(user);
        if (!passwordEncoder.matches(request.temporaryPassword(), user.getPassword())) {
            throw AuthException.badRequest("현재 비밀번호가 올바르지 않습니다.");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw AuthException.badRequest("새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
        }
        if (request.newPassword().equals(request.temporaryPassword())) {
            throw AuthException.badRequest("기존 비밀번호와 다른 비밀번호를 사용해주세요.");
        }
        if (request.newPassword().length() < 8 || request.newPassword().length() > 20) {
            throw AuthException.badRequest("비밀번호는 8자 이상 20자 이하로 입력해주세요.");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.deleteByUserId(userId);
        // 기존 Access Token은 만료 전까지 유효할 수 있으므로 운영 시 짧은 만료시간을 사용해야 한다.
    }

    public AuthDto.MeResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.unauthorized("사용자를 찾을 수 없습니다."));
        requireActive(user);
        return AuthDto.MeResponse.from(user);
    }

    private void saveOrRotateRefreshToken(User user, String rawToken) {
        String tokenHash = hash(rawToken);
        LocalDateTime expiresAt = refreshExpiry();
        refreshTokenRepository.findByUserIdForUpdate(user.getId())
                .ifPresentOrElse(stored -> stored.rotate(tokenHash, expiresAt),
                        () -> refreshTokenRepository.save(RefreshToken.builder()
                                .user(user).tokenHash(tokenHash).expiresAt(expiresAt).build()));
    }

    private Long validatedRefreshUserId(String rawToken) {
        if (rawToken == null || !jwtProvider.validateToken(rawToken)
                || !jwtProvider.isRefreshToken(rawToken)) {
            throw AuthException.unauthorized("Refresh Token이 유효하지 않습니다.");
        }
        return jwtProvider.getUserId(rawToken);
    }

    private LocalDateTime refreshExpiry() {
        return LocalDateTime.now().plus(Duration.ofMillis(jwtProvider.getRefreshTokenExpiration()));
    }

    private String issueAccessToken(User user) {
        return jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
    }

    private void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw AuthException.forbidden("사용할 수 없는 계정입니다. 관리자에게 문의해주세요.");
        }
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
