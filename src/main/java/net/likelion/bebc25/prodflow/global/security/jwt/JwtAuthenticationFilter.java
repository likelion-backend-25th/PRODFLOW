package net.likelion.bebc25.prodflow.global.security.jwt;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.global.security.service.CustomUserDetailsService;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // 로그인/재발급/로그아웃은 Refresh Cookie로 처리하며 Bearer 헤더가 필수는 아니다.
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || ("POST".equalsIgnoreCase(request.getMethod()) && (
                path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/refresh")
                        || path.equals("/api/v1/auth/logout")))
                || path.equals("/swagger-ui.html") || path.startsWith("/swagger-ui/")
                || path.equals("/v3/api-docs") || path.startsWith("/v3/api-docs/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!header.startsWith("Bearer ") || header.substring(7).isBlank()) {
            unauthorized(response);
            return;
        }
        String token = header.substring(7);
        try {
            if (!jwtProvider.validateToken(token) || !jwtProvider.isAccessToken(token)) {
                unauthorized(response);
                return;
            }
            CustomUserDetails details = userDetailsService.loadUserById(jwtProvider.getUserId(token));
            if (!details.isEnabled()) {
                unauthorized(response);
                return;
            }
            // 최초/재발급 임시비밀번호 사용자는 일반 업무 API에 접근할 수 없다.
            if (details.isMustChangePassword() && !isPasswordChangeAllowed(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"code\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"비밀번호 변경 후 이용할 수 있습니다.\"}");
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException | JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            unauthorized(response);
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isPasswordChangeAllowed(HttpServletRequest request) {
        String path = request.getServletPath();
        return (path.equals("/api/v1/auth/change-password") && "POST".equalsIgnoreCase(request.getMethod()))
                || (path.equals("/api/v1/auth/me") && "GET".equalsIgnoreCase(request.getMethod()));
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"UNAUTHORIZED_ACCESS\",\"message\":\"유효한 로그인이 필요합니다.\"}");
    }
}
