package net.likelion.bebc25.prodflow.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass // 다른 Entity의 부모 클래스로 사용
@EntityListeners(AuditingEntityListener.class) // 아래의 이벤트 감지
public abstract class BaseTimeEntity { // 추상 클래스 new 생성 안됨

    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private String updatedBy;

    // Entity 최초 저장될 때 일시와 사용자의 이름이 존재하지 않으면 자동으로 주입
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (createdBy == null) {
            createdBy = getPrincipalUserName();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (updatedBy == null) {
            updatedBy = getPrincipalUserName();
        }
    }

    // Entity 수정할 때 수정일시와 사용자의 이름을 자동으로 주입
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();

        if (!"SYSTEM".equals(getPrincipalUserName())) {
            updatedBy = getPrincipalUserName();
        }
    }

    private String getPrincipalUserName() {
        // 현재 실행 중인 스레드의 SecurityContext에서 인증 정보를 가져옴.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // 인증 정보가 없거나 인증되지 않은 경우 SYSTEM을 반환
        if (auth == null || !auth.isAuthenticated()) {
            return "SYSTEM";
        }

        // 인증된 사용자 정보(principal)가 프로젝트의 CustomUserDetails인지 확인
        if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            // 현재 CustomUserDetails.getUsername()은 User.name으로 직원 이름을 작성자,수정자로 기록
            return userDetails.getUsername();
        }

        // 익명 사용자 또는 CustomUserDetails가 아닌 인증 정보는 SYSTEM으로 기록
        return "SYSTEM";
    }
}