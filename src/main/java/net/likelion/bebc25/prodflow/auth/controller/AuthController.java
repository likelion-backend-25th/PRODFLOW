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
    private static final String REFRESH_COOKIE = "refreshToken";
    private static final String COOKIE_PATH = "/api/v1/auth";
    private final AuthService authService;
    private final JwtProvider jwtProvider;

    @Value("${auth.refresh-cookie-secure:false}")
    private boolean secureCookie;
    @Value("${auth.refresh-cookie-same-site:Lax}")
    private String sameSite;
    @Value("${auth.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @PostMapping("/login")
    public ResponseEntity<AuthDto.LoginResponse> login(@Valid @RequestBody AuthDto.LoginRequest request,
                                                       HttpServletRequest httpRequest) {
        verifyOrigin(httpRequest);
        AuthService.LoginResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(result.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthDto.RefreshResponse> refresh(HttpServletRequest request) {
        verifyOrigin(request);
        AuthService.RefreshResult result = authService.refresh(readRefreshCookie(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        verifyOrigin(request);
        authService.logout(readRefreshCookie(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString()).build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal CustomUserDetails principal,
                                               @Valid @RequestBody UserDto.ChangePasswordRequest request) {
        authService.changePassword(principal.getUserId(), request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString()).build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthDto.MeResponse> me(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(authService.me(principal.getUserId()));
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_COOKIE.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private ResponseCookie refreshCookie(String token) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true).secure(secureCookie).sameSite(sameSite)
                .path(COOKIE_PATH)
                .maxAge(Duration.ofMillis(jwtProvider.getRefreshTokenExpiration())).build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(secureCookie).sameSite(sameSite)
                .path(COOKIE_PATH).maxAge(Duration.ZERO).build();
    }

    // Cookie 기반 로그인/재발급/로그아웃 요청에 대해 브라우저의 교차 출처 요청을 제한한다.
    // Origin이 없는 CLI/Bruno 호출은 허용한다. 운영에서 allowed-origins를 정확하게 설정해야 한다.
    private void verifyOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if ((origin == null || origin.isBlank()) && "cross-site".equalsIgnoreCase(fetchSite)) {
            throw AuthException.forbidden("허용되지 않은 요청 출처입니다.");
        }
        if (origin == null || origin.isBlank()) return;
        Set<String> allowed = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        String self = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort());
        if (!self.equals(origin) && !allowed.contains(origin)) {
            throw AuthException.forbidden("허용되지 않은 요청 출처입니다.");
        }
    }
}
