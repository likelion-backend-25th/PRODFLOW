package net.likelion.bebc25.prodflow.global.security.config;

import jakarta.servlet.DispatcherType;
import net.likelion.bebc25.prodflow.global.security.handler.CustomAccessDeniedHandler;
import net.likelion.bebc25.prodflow.global.security.handler.CustomAuthenticationEntryPoint;
import net.likelion.bebc25.prodflow.global.security.jwt.JwtAuthenticationFilter;
import net.likelion.bebc25.prodflow.global.security.jwt.JwtProvider;
import net.likelion.bebc25.prodflow.global.security.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 보안 설정 클래스.
 *
 * <p>주요 역할:
 * 1. JWT 기반 인증 및 세션 정책 설정
 * 2. 인증 실패 및 접근 거부 예외 처리
 * 3. API 엔드포인트별 접근 권한 관리
 * 4. 프론트엔드와의 CORS 통신 설정
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * CORS 요청을 허용할 프론트엔드 Origin 목록.
     *
     * <p>기본적으로 로컬 개발 환경과 운영 프론트엔드를 허용한다.
     * application.yml 또는 환경변수에 auth.allowed-origins가
     * 정의되어 있으면 해당 설정값이 기본값보다 우선 적용된다.
     */
    @Value("${auth.allowed-origins}")
    private String allowedOrigins;

    /**
     * Spring Security 필터 체인을 구성한다.
     *
     * <p>JWT 인증, CORS, 세션 정책, 예외 처리 및
     * API별 접근 권한을 설정한다.
     *
     * @param http                      Spring Security HTTP 보안 설정 객체
     * @param jwtProvider               JWT 생성 및 검증을 담당하는 객체
     * @param userDetailsService        사용자 정보 조회 서비스
     * @param customAccessDeniedHandler 접근 권한 부족 시 예외 처리 객체
     * @return 구성된 SecurityFilterChain
     * @throws Exception 보안 설정 구성 중 오류가 발생한 경우
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtProvider jwtProvider,
            CustomUserDetailsService userDetailsService,
            CustomAuthenticationEntryPoint customAuthenticationEntryPoint,
            CustomAccessDeniedHandler customAccessDeniedHandler
    ) throws Exception {

        http
                // CSRF 보호 비활성화
                // Refresh Token 쿠키를 사용하는 엔드포인트는 별도의 CSRF 방어가 필요하다.
                .csrf(AbstractHttpConfigurer::disable)

                // CORS 활성화 및 허용 Origin, Method, Header 설정 적용
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // JWT 기반 무상태(Stateless) 인증 사용
                // 서버에서 HTTP 세션을 생성하거나 유지하지 않는다.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 인증 및 인가 실패 시 예외 처리
                .exceptionHandling(ex -> ex
                        // 인증되지 않은 사용자에 대한 처리
                        .authenticationEntryPoint(customAuthenticationEntryPoint)

                        // 인증은 되었으나 권한이 부족한 경우 처리
                        .accessDeniedHandler(customAccessDeniedHandler)
                )

                // JWT 인증 필터 등록
                // UsernamePasswordAuthenticationFilter보다 먼저 실행하여
                // 요청의 JWT를 검증하고 인증 정보를 설정하도록 구성한다.
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class
                )

                // HTTP Basic 인증 비활성화
                .httpBasic(AbstractHttpConfigurer::disable)

                // Spring Security 기본 폼 로그인 비활성화
                .formLogin(AbstractHttpConfigurer::disable)

                // Spring Security 기본 로그아웃 처리 비활성화
                .logout(AbstractHttpConfigurer::disable)

                // API 요청 경로 및 HTTP 메서드에 따른 접근 권한 설정
                .authorizeHttpRequests(auth -> auth

                        // 오류 응답 처리 경로만 허용
                        .requestMatchers(request ->
                                request.getDispatcherType() == DispatcherType.ERROR
                                        && "/error".equals(request.getServletPath())
                        ).permitAll()

                        // MCP 최초 요청은 JWT 인증 필수
                        .requestMatchers("/mcp").authenticated()

                        // Swagger UI 및 OpenAPI 문서 접근 허용
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // 로그인, 토큰 갱신, 로그아웃 API는 인증 없이 접근 허용
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout"
                        ).permitAll()

                        // PortOne 서버 -> 공개 콜백. 서명은 PaymentController에서 별도로 검증한다.
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()

                        // 최초 비밀번호 변경 API
                        // 로그인한 직원만 접근 가능
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/change-password"
                        ).authenticated()

                        // 내 정보 조회 API
                        // 로그인한 모든 직원이 자신의 정보 조회 가능
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/users/me"
                        ).authenticated()

                        // 직원 관리 API
                        // 최고 관리자만 직원 목록 조회, 등록, 수정, 삭제 가능
                        .requestMatchers(
                                "/api/v1/users",
                                "/api/v1/users/**"
                        ).hasRole("SUPER_ADMIN")

                        // 생산 라인 등록은 최고 관리자 또는 수주 관리자만 허용
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/lines"
                        ).hasAnyRole("SUPER_ADMIN", "ORDER_MANAGER")

                        // 생산 라인 상태는 최고 관리자, 수주 관리자, 생산 현장 관리자가 허용
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/lines/*/status"
                        ).hasAnyRole("SUPER_ADMIN", "ORDER_MANAGER", "PRODUCTION_MANAGER")

                        // 생산 라인 수정은 최고 관리자 또는 수주 관리자만 허용. 이름 변경은 서비스에서 최고 관리자만 받는다.
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/lines/*"
                        ).hasAnyRole("SUPER_ADMIN", "ORDER_MANAGER")

                        // 작업 상태 변경은 최고 관리자 또는 생산 현장 관리자만 허용. 배정된 담당자인지는 서비스에서 본다.
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/work-orders/*/status"
                        ).hasAnyRole("SUPER_ADMIN", "PRODUCTION_MANAGER")

                        // 라인 변경과 담당자 변경은 역할이 다르다. 어느 항목인지는 서비스에서 가른다.
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/work-orders/*"
                        ).hasAnyRole("SUPER_ADMIN", "ORDER_MANAGER", "PRODUCTION_MANAGER")

                        // 거래처 등록은 최고 관리자 또는 발주 관리자만 허용
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/clients/", "/api/v1/clients/**"
                        ).hasAnyRole("SUPER_ADMIN", "ORDER_MANAGER")

                        // 공휴일 데이터 가져오기는 최고 관리자만 허용
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/holidays/import"
                        ).hasRole("SUPER_ADMIN")

                        // 견적서 등록은 최고 관리자 또는 발주 관리자만 허용
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/quotations"
                        ).hasAnyRole("SUPER_ADMIN", "ORDER_MANAGER")

                        // 생산품목 등록·도면 업로드는 최고 관리자 또는 수주 관리자만 허용
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/products",
                                "/api/v1/products/*/drawings"
                        ).hasAnyRole("SUPER_ADMIN", "ORDER_MANAGER")

                        // 생산품목 도면 삭제는 최고 관리자 또는 수주 관리자만 허용
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/products/*/drawings/*"
                        ).hasAnyRole("SUPER_ADMIN", "ORDER_MANAGER")

                        // 생산품목 수정은 최고 관리자 또는 수주 관리자만 허용
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/products/*"
                        ).hasAnyRole("SUPER_ADMIN", "ORDER_MANAGER")

                        // 생산품목 상태 변경은 최고 관리자 또는 수주 관리자만 허용
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/products/*/status"
                        ).hasAnyRole("SUPER_ADMIN", "ORDER_MANAGER")

                        // 위 규칙에 해당하지 않는 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    /**
     * 프론트엔드와 백엔드 간 CORS 정책을 설정한다.
     *
     * <p>AWS Amplify에 배포된 프론트엔드와 로컬 개발 환경에서
     * Spring Boot API를 호출할 수 있도록 허용 Origin을 지정한다.
     *
     * <p>Refresh Token 쿠키를 사용하는 요청을 위해
     * Credentials 허용 설정을 유지한다.
     *
     * @return API 전체 경로에 적용할 CorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 쉼표로 구분된 허용 Origin 문자열을 목록으로 변환
        // 예: localhost:5173, www.prodflow.site
        config.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toList()
        );

        // 프론트엔드에서 사용할 수 있는 HTTP 메서드 허용
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT",
                "PATCH", "DELETE", "OPTIONS"
        ));

        // Authorization: JWT Access Token 전달
        // Content-Type: JSON 요청 본문 전송
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type"
        ));

        // Refresh Token 등의 쿠키를 포함한 교차 출처 요청 허용
        // 허용 Origin은 "*"가 아닌 구체적인 주소로 제한한다.
        config.setAllowCredentials(true);

        // CORS Preflight 응답을 최대 1시간 동안 캐시하도록 설정
        config.setMaxAge(3600L);

        // 설정한 CORS 정책을 모든 API 경로에 적용
        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }

    /**
     * 인증 실패 시 JSON 형태의 오류 응답을 작성한다.
     *
     * @param res     HTTP 응답 객체
     * @param status  HTTP 상태 코드
     * @param code    애플리케이션 오류 코드
     * @param message 사용자에게 전달할 오류 메시지
     * @throws IOException 응답 작성 중 입출력 오류가 발생한 경우
     */
    private static void writeError(
            jakarta.servlet.http.HttpServletResponse res,
            int status,
            String code,
            String message
    ) throws IOException {

        // HTTP 상태 코드 설정
        res.setStatus(status);

        // 한글 오류 메시지 처리를 위한 UTF-8 설정
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // JSON 응답 형식 지정
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // 클라이언트에 오류 코드와 메시지 전달
        res.getWriter().write(
                "{\"code\":\"" + code
                        + "\",\"message\":\"" + message + "\"}"
        );
    }
}
