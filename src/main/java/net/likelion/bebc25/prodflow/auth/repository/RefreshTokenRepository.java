package net.likelion.bebc25.prodflow.auth.repository;

import jakarta.persistence.LockModeType;
import net.likelion.bebc25.prodflow.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByUserId(Long userId);

    // 동시에 들어오는 재발급 요청이 동일 토큰을 여러 번 사용하지 못하도록 행 잠금.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.user.id = :userId")
    Optional<RefreshToken> findByUserIdForUpdate(@Param("userId") Long userId);

    void deleteByUserId(Long userId);
}
