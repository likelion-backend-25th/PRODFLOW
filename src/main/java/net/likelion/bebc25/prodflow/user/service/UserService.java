package net.likelion.bebc25.prodflow.user.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.auth.dto.AuthDto;
import net.likelion.bebc25.prodflow.auth.exception.AuthException;
import net.likelion.bebc25.prodflow.auth.repository.RefreshTokenRepository;
import net.likelion.bebc25.prodflow.user.dto.UserDto;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    // 직원 등록
    @Transactional
    public UserDto.Response createUser(UserDto.CreateRequest request) {

        String temporaryPassword = request.temporaryPassword();
        // 임시 비밀번호 유효성 검사
        validateTemporaryPassword(temporaryPassword);

        // 이메일 중복 검사
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "이미 사용중인 이메일입니다."
            );
        }

        // 임시 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.temporaryPassword());

        // 새로 생성한 비영속 상태라 save()로 영속화
        User newUser = request.toEntity(encodedPassword);
        User saveUser = userRepository.save(newUser);

        // 응답 Dto를 반환해 추가조회 없이 화면에 갱신가능
        return UserDto.Response.from(saveUser);
    }


    // 직원 목록 조회
    public List<UserDto.Response> getAllUsers() {

        return userRepository.findAll()
                .stream()
                .map(UserDto.Response::from) // 람다식 .map(user -> UserDto.Response.from(user))
                .toList();
    }

    // 직원 상세 조회
    public UserDto.Response getUserById(Long id) {

        User user = findUserByIdOrThrow(id);

        return UserDto.Response.from(user);
    }

    // 직원 정보 수정
    @Transactional
    public UserDto.Response updateUser(
            Long id,
            UserDto.UpdateRequest request

    ) {
        // 직원 조회
        User user = findUserByIdOrThrow(id);

        // 삭제된 직원인지 검증
        validateNotDeleted(user);

        // 수정 API에서는 ACTIVE와 INACTIVE만 허용
        if (request.status() != UserStatus.ACTIVE
                && request.status() != UserStatus.INACTIVE) {
            throw new IllegalArgumentException(
                    "계정 상태는 ACTIVE 또는 INACTIVE만 지정할 수 있습니다."
            );
        }

        //이메일이 변경된 경우에만 중복 검사
        if (!user.getEmail().equals(request.email())
                && userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "이미 사용 중인 이메일입니다."
            );
        }

        // 인증 관련 정보 변경 여부 확인
        boolean securityInfoChanged =
                //이메일
                !user.getEmail().equals(request.email())
                        // 계정 권한
                        || user.getRole() != request.role()
                        // 계정 상태
                        || user.getStatus() != request.status()
                        // 비밀번호 변경
                        || request.temporaryPassword() != null;

        // 직원 정보 수정
        user.update(
                request.name(),
                request.email(),
                request.phone(),
                request.role(),
                request.status()
        );

        // 임시 비밀번호 가져오기
        String temporaryPassword = request.temporaryPassword();
        // 임시 비밀번호가 발급된 경우에만 변경
        if (temporaryPassword != null) {
            // 유효성 검사
            validateTemporaryPassword(temporaryPassword);

            String encodedPassword =
                    passwordEncoder.encode(temporaryPassword);

            user.updatePassword(encodedPassword);
        }
        // 계정 상태, 권한, 이메일, 비밀번호 변경 시 Refresh Token 폐기
        if (securityInfoChanged) {
            refreshTokenRepository.deleteByUserId(id);
        }

        // save()가 필요없는 이유 : @Transactional 내부에서 조회한 User는 영속 상태이므로,
        // JPA의 변경 감지(Dirty Checking)를 통해 트랜잭션 커밋 시 DB에 반영

        return UserDto.Response.from(user);
    }

    // 직원 소프트 삭제
    @Transactional
    public UserDto.Response softDeleteUser(Long id) {

        return UserDto.Response.from(findUserByIdOrThrow(id));
    }

    // 내 정보 조회
    public AuthDto.MeResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.unauthorized("사용자를 찾을 수 없습니다."));
        requireActive(user);
        return AuthDto.MeResponse.from(user);
    }

    // ID로 직원 조회
    // 공통으로 사용하므로 getUserById()에서 작성하지 않고 분리
    private User findUserByIdOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("직원을 찾을 수 없습니다. id: " + id)
                );
    }

    // 삭제된 직원 검증
    private void validateNotDeleted(User user) {
        if (user.getStatus() == UserStatus.DELETED) {
            throw new IllegalStateException("삭제된 직원은 수정할 수 없습니다.");
        }
    }

    // 사용/비활성 사용자 검증
    private void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw AuthException.forbidden("사용할 수 없는 계정입니다. 관리자에게 문의해주세요.");
        }
    }

    // 임시 비밀번호 유효성 검사
    private void validateTemporaryPassword(String temporaryPassword) {

        if (temporaryPassword == null ||
                !temporaryPassword.matches(
                        "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%&*?])[A-Za-z\\d!@#$%&*?]{12}$"
                )) {
            throw new IllegalArgumentException(
                    "임시 비밀번호 형식이 올바르지 않습니다."
            );
        }
    }
}
