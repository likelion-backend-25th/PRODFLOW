package net.likelion.bebc25.prodflow.user.repository;

import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // 이메일로 직원 조회 없을시 null 반환
    Optional<User> findByEmail(String emaiil);

    // 이메일 중복 검증
    boolean existsByEmail(String email);

    // 직원 검색: 삭제된 계정 제외, 이름/이메일/전화번호 검색, 상태 필터, 페이징
    @Query("""
            SELECT u
            FROM User u
            WHERE u.status <> :excludedStatus
              AND (:status IS NULL OR u.status = :status)
              AND (
                    :keyword IS NULL
                    OR LOWER(u.name) LIKE CONCAT('%', :keyword, '%')
                    OR LOWER(u.email) LIKE CONCAT('%', :keyword, '%')
                    OR u.phone LIKE CONCAT('%', :keyword, '%')
              )
            """)
    Page<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("status") UserStatus status,
            @Param("excludedStatus") UserStatus excludedStatus,
            Pageable pageable
    );

    // 상단 통계에서 상태별 직원 수 집계
    long countByStatus(UserStatus status);

}
