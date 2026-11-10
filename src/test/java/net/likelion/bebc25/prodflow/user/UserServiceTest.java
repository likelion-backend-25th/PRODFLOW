package net.likelion.bebc25.prodflow.user;

import net.likelion.bebc25.prodflow.auth.repository.RefreshTokenRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import net.likelion.bebc25.prodflow.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("주문 담당자 역할로 조회하고 조회된 사용자 목록을 반환한다")
    void findByOrderManager() {
        User firstManager = User.builder().name("이서준").role(UserRole.ORDER_MANAGER).build();
        User secondManager = User.builder().name("오지훈").role(UserRole.ORDER_MANAGER).build();
        given(userRepository.findByRole(UserRole.ORDER_MANAGER)).willReturn(List.of(firstManager, secondManager));

        List<User> result = userService.findByOrderManager();

        assertThat(result).containsExactly(firstManager, secondManager);
        verify(userRepository).findByRole(UserRole.ORDER_MANAGER);
    }

    @Test
    @DisplayName("조회된 주문 담당자가 없으면 빈 목록을 반환한다")
    void findByOrderManagerReturnsEmptyList() {
        given(userRepository.findByRole(UserRole.ORDER_MANAGER)).willReturn(List.of());

        List<User> result = userService.findByOrderManager();

        assertThat(result).isEmpty();
        verify(userRepository).findByRole(UserRole.ORDER_MANAGER);
    }
}
