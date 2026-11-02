package net.likelion.bebc25.prodflow.auth.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.auth.dto.AuthDto;
import net.likelion.bebc25.prodflow.auth.exception.AuthException;
import net.likelion.bebc25.prodflow.auth.service.AuthService;
import net.likelion.bebc25.prodflow.global.security.jwt.JwtProvider;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.user.dto.UserDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    // Refresh Token을 저장할 쿠키 이름
    private static final String REFRESH_COOKIE = "refreshToken";

    // Refresh Token 쿠키가 전송될 URL 경로
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final JwtProvider jwtProvider;

    // HTTPS 연결에서만 쿠키를 전송할지 설정
    @Value("${auth.refresh-cookie-secure:false}")
    private boolean secureCookie;

    // 쿠키의 교차 사이트 전송 정책 설정
    @Value("${auth.refresh-cookie-same-site:Lax}")
    private String sameSite;

    // 인증 관련 요청을 허용할 프론트엔드 주소
    @Value("${auth.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<AuthDto.LoginResponse> login(
            @Valid @RequestBody AuthDto.LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        // 요청을 보낸 Origin이 허용된 주소인지 확인
        verifyOrigin(httpRequest);

        // 이메일 및 비밀번호 검증 후 JWT 발급
        AuthService.LoginResult result = authService.login(request);

        // Refresh Token은 Cookie에 저장하도록 전달
        // Access Token은 응답 Body에 담아 반환
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookie(result.refreshToken()).toString()
                )
                .body(result.response());
    }

    // 토큰 재발급
    @PostMapping("/refresh")
    public ResponseEntity<AuthDto.RefreshResponse> refresh(
            HttpServletRequest request
    ) {
        // 요청 출처 검증
        verifyOrigin(request);

        // Cookie에서 Refresh Token을 추출하고 검증하여
        // 새로운 Access Token 및 Refresh Token 발급
        AuthService.RefreshResult result =
                authService.refresh(readRefreshCookie(request));

        // 새로운 Refresh Token을 Cookie에 설정
        // 새로운 Access Token을 응답 Body로 반환
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookie(result.refreshToken()).toString()
                )
                .body(result.response());
    }

    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        // 요청 출처 검증
        verifyOrigin(request);

        // Cookie에서 Refresh Token을 읽어 로그아웃 처리
        authService.logout(readRefreshCookie(request));

        // Refresh Token 쿠키 삭제 및 204 응답
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        clearRefreshCookie().toString()
                )
                .build();
    }

    // 비밀번호 변경
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody UserDto.ChangePasswordRequest request
    ) {
        // 현재 로그인한 사용자의 ID로 비밀번호 변경
        authService.changePassword(principal.getUserId(), request);

        // 기존 Refresh Token 쿠키 삭제
        // 비밀번호 변경 성공 시 204 응답
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        clearRefreshCookie().toString()
                )
                .build();
    }

    // 현재 로그인한 사용자 정보 조회
    @GetMapping("/me")
    public ResponseEntity<AuthDto.MeResponse> me(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        // 인증된 사용자 ID로 사용자 정보 조회
        return ResponseEntity.ok(
                authService.me(principal.getUserId())
        );
    }

    // 쿠키에서 리프레쉬토큰 찾기
    private String readRefreshCookie(HttpServletRequest request) {

        // 쿠키가 없는 경우 null 반환
        if (request.getCookies() == null) {
            return null;
        }

        // 요청에 포함된 쿠키를 순회하며 Refresh Token 검색
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        // Refresh Token 쿠키가 없다면 null 반환
        return null;
    }

    // 리프레쉬 토큰 생성
    private ResponseCookie refreshCookie(String token) {

        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path(COOKIE_PATH)
                .maxAge(
                        Duration.ofMillis(
                                jwtProvider.getRefreshTokenExpiration()
                        )
                )
                .build();
    }

    // 리프레쉬 토큰 삭제
    private ResponseCookie clearRefreshCookie() {

        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    // 요청 출처 검증
    private void verifyOrigin(HttpServletRequest request) {

        // 브라우저에서 요청을 보낸 출처
        String origin = request.getHeader("Origin");

        // 브라우저가 제공하는 요청의 사이트 관계 정보
        String fetchSite = request.getHeader("Sec-Fetch-Site");

        // Origin은 없지만 cross-site 요청으로 표시된 경우 차단
        if ((origin == null || origin.isBlank())
                && "cross-site".equalsIgnoreCase(fetchSite)) {
            throw AuthException.forbidden(
                    "허용되지 않은 요청 출처입니다."
            );
        }

        // Origin 헤더가 없는 요청은 통과
        if (origin == null || origin.isBlank()) {
            return;
        }

        // application.yaml에 설정된 허용 Origin 목록 생성
        Set<String> allowed = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        // 현재 백엔드 서버 자신의 Origin 생성
        String self = request.getScheme()
                + "://" + request.getServerName()
                + (request.getServerPort() == 80
                || request.getServerPort() == 443
                ? ""
                : ":" + request.getServerPort());

        // 백엔드 자신 또는 허용된 프론트엔드 Origin이 아니면 차단
        if (!self.equals(origin) && !allowed.contains(origin)) {
            throw AuthException.forbidden(
                    "허용되지 않은 요청 출처입니다."
            );
        }
    }
}
