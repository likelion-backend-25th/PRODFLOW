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
    public UserDto.Response create(UserDto.CreateRequest request) {
        // 이메일 중복 검사
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "이미 사용중인 이메일입니다."
            );
        }

        // 임시 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        User user = userRepository.save(request.toEntity(encodedPassword));

        // 응답 Dto를 반환해 추가조회 없이 화면에 갱신가능
        return UserDto.Response.from(user);
    }

    // 직원 목록 조회
    @Transactional(readOnly = true)
    public List<UserDto.Response> getList() {

        return userRepository.findAll()
                .stream()
                .map(UserDto.Response::from)
                .toList();
    }

    // 직원 상세 조회
    @Transactional(readOnly = true)
    public UserDto.Response getDetail(Long id) {

        User user = getUserOrThrow(id);

        return UserDto.Response.from(user);
    }

    // 직원 정보 수정
    public UserDto.Response update(
            Long id,
            UserDto.UpdateRequest request
    ) {
        User user = getUserOrThrow(id);
        validateNotDeleted(user);

        // 이메일이 변경된 경우에만 중복 검사
        if (!user.getEmail().equals(request.email())
                && userRepository.existsByEmail(request.email())) {

            throw new IllegalArgumentException(
                    "이미 사용 중인 이메일입니다."
            );
        }

        // 일반 직원 정보 수정
        user.update(
                request.name(),
                request.email(),
                request.phone(),
                request.role()
        );

        // 임시 비밀번호를 발급한 경우에만 비밀번호 변경
        String password = request.password();

        if (password != null
                && !password.isBlank()) {

            String encodedPassword =
                    passwordEncoder.encode(password);

            user.updatePassword(encodedPassword);
        }

        // 변경 사항을 DB에 반영한 뒤 응답 생성
        userRepository.flush();

        return UserDto.Response.from(user);
    }

    // 계정 활성화
    public UserDto.Response activate(Long id) {

        User user = getUserOrThrow(id);
        validateNotDeleted(user);

        user.activate();

        userRepository.flush();

        return UserDto.Response.from(user);
    }

    // 계정 비활성화
    public UserDto.Response deactivate(Long id) {

        User user = getUserOrThrow(id);
        validateNotDeleted(user);

        user.deactivate();

        userRepository.flush();

        return UserDto.Response.from(user);
    }

    // 직원 소프트 삭제
    public UserDto.Response delete(Long id) {

        User user = getUserOrThrow(id);

        if (user.getStatus() == UserStatus.DELETED) {
            throw new IllegalStateException(
                    "이미 삭제된 직원입니다."
            );
        }

        user.delete();

        userRepository.flush();

        return UserDto.Response.from(user);
    }

    // ID로 직원 조회
    private User getUserOrThrow(Long id) {

        return userRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "직원을 찾을 수 없습니다: id=" + id
                        )
                );
    }

    // 삭제된 직원의 상태 변경 및 정보 수정 방지
    private void validateNotDeleted(User user) {

        if (user.getStatus() == UserStatus.DELETED) {
            throw new IllegalStateException(
                    "삭제된 직원은 수정할 수 없습니다."
            );
        }
    }
}
