package net.likelion.bebc25.prodflow.global.config;

import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware(UserRepository userRepository) {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken) {
                return Optional.of("system");
            }
            if (authentication.getPrincipal() instanceof CustomUserDetails details) {
                return userRepository.findById(details.getUserId())
                        .map(user -> user.getName())
                        .or(() -> Optional.ofNullable(authentication.getName()));
            }
            return Optional.ofNullable(authentication.getName()).or(() -> Optional.of("system"));
        };
    }
}
