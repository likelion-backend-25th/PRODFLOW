package net.likelion.bebc25.prodflow.global.config;

import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

// JPA Auditing에서 사용할 작성자, 수정자 조회 설정
@Configuration
public class JpaAuditConfiguration {

    // 작성자,수정자를 문자열로 제공하는 객체를 스프링 빈으로 등록
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            // 현재 실행 중인 스레드의 SecurityContext에서 인증 정보를 가져옴.
            var authentication = SecurityContextHolder.getContext().getAuthentication();

            // 인증 정보가 없거나 인증되지 않은 경우 SYSTEM을 반환
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of("SYSTEM");
            }

            // 인증된 사용자 정보(principal)가 프로젝트의 CustomUserDetails인지 확인
            if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
                // 현재 CustomUserDetails.getUsername()은 User.name으로 직원 이름을 작성자,수정자로 기록
                return Optional.of(userDetails.getUsername());
            }

            // 익명 사용자 또는 CustomUserDetails가 아닌 인증 정보는 SYSTEM으로 기록
            return Optional.of("SYSTEM");
        };
    }
}