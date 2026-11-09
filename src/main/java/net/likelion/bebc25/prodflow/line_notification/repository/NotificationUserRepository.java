package net.likelion.bebc25.prodflow.line_notification.repository;

import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NotificationUserRepository extends JpaRepository<User, Long> {

    List<User> findByRoleInAndStatus(Collection<UserRole> roles, UserStatus status);
}
