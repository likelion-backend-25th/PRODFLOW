package net.likelion.bebc25.prodflow.user.repository;

import net.likelion.bebc25.prodflow.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // 이메일로 직원 조회 없을시 null 반환
    Optional<User> findByEmail(String emaiil);

    // 이메일 중복 검증
    boolean existsByEmail(String email);

}
