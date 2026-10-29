package net.likelion.bebc25.prodflow.user.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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

    // 직원 등록
    @Transactional
    public UserDto.Response createUser(UserDto.CreateRequest request) {
        // 이메일 중복 검사
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "이미 사용중인 이메일입니다."
            );
        }

        // 임시 비밀번호 유효성 검사
        validateTemporaryPassword(request.temporaryPassword());

        // 임시 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.temporaryPassword());

        // 새로 생성한 비영속 상태라 save()로 영속화
        User user = userRepository.save(request.toEntity(encodedPassword));

        // 응답 Dto를 반환해 추가조회 없이 화면에 갱신가능
        return UserDto.Response.from(user);
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
    public UserDto.Response updateUser(Long id, UserDto.UpdateRequest request) {
        // 직원 조회
        User user = findUserByIdOrThrow(id);

        // 삭제된 직원인지 검증
        validateNotDeleted(user);

        //이메일이 변경된 경우에만 중복 검사
        if (!user.getEmail().equals(request.email())
                && userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "이미 사용 중인 이메일입니다."
            );
        }

        // 임시 비밀번호 가져오기
        String temporaryPassword = request.temporaryPassword();

        // 임시 비밀번호가 전달되었다면 유효성 검사
        if (temporaryPassword != null) {
            validateTemporaryPassword(temporaryPassword);
        }

        // 직원 정보 수정
        user.update(
                request.name(),
                request.email(),
                request.phone(),
                request.role()
        );

        // 임시 비밀번호가 발급된 경우에만 변경
        if (temporaryPassword != null) {

            String encodedPassword =
                    passwordEncoder.encode(temporaryPassword);

            user.updatePassword(encodedPassword);
        }

        // save()가 필요없는 이유 : @Transactional 내부에서 조회한 User는 영속 상태이므로,
        // JPA의 변경 감지(Dirty Checking)를 통해 트랜잭션 커밋 시 DB에 반영

        return UserDto.Response.from(user);
    }

    // 계정 활성화
    @Transactional
    public UserDto.Response activateUser(Long id) {
        return UserDto.Response.from(findUserByIdOrThrow(id));
    }

    // 계정 비활성화
    @Transactional
    public UserDto.Response deactivateUser(Long id) {
        return UserDto.Response.from(findUserByIdOrThrow(id));
    }

    // 직원 소프트 삭제
    @Transactional
    public UserDto.Response softDeleteUser(Long id) {
        return UserDto.Response.from(findUserByIdOrThrow(id));
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
