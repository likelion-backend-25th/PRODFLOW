package net.likelion.bebc25.prodflow.user.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.auth.exception.AuthException;
import net.likelion.bebc25.prodflow.auth.repository.RefreshTokenRepository;
import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.user.dto.UserDto;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

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

        // 임시 비밀번호 확인 및 검증
        String temporaryPassword = request.temporaryPassword();

        if (temporaryPassword != null) {
            validateTemporaryPassword(temporaryPassword);
        }

        // 인증 관련 정보 변경 여부 계산
        boolean securityInfoChanged =
                !user.getEmail().equals(request.email())
                        || user.getRole() != request.role()
                        || user.getStatus() != request.status()
                        || temporaryPassword != null;

        // 직원 정보 수정
        user.update(
                request.name(),
                request.email(),
                request.phone(),
                request.role(),
                request.status()
        );

        // 임시 비밀번호를 발급한 경우에만 변경
        if (temporaryPassword != null) {
            user.updatePassword(
                    passwordEncoder.encode(temporaryPassword)
            );
        }

        // 인증 관련 정보 변경 시 Refresh Token 폐기
        if (securityInfoChanged) {
            refreshTokenRepository.deleteByUserId(id);
        }

        return UserDto.Response.from(user);
    }

    // 직원 소프트 삭제
    @Transactional
    public UserDto.Response softDeleteUser(Long id) {

        // 삭제 대상 직원 조회
        User user = findUserByIdOrThrow(id);

        // 이미 삭제된 직원인지 확인
        validateNotDeleted(user);

        // 계정 상태를 DELETED로 변경하고 삭제 일시 기록
        user.softDelete();

        // 삭제된 계정의 Refresh Token 제거
        refreshTokenRepository.deleteByUserId(id);

        return UserDto.Response.from(user);
    }

    // 내 정보 조회
    public UserDto.MeResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.unauthorized("사용자를 찾을 수 없습니다."));
        requireActive(user);
        return UserDto.MeResponse.from(user);
    }

    // 직원 목록 페이징 조회
    public PageResponse<UserDto.Response> getUsers(
            String keyword,
            UserStatus status,
            Pageable pageable
    ) {
        // 삭제 계정 검색 차단
        if (status == UserStatus.DELETED) {
            throw new IllegalArgumentException(
                    "ACTIVE 또는 INACTIVE만 조회할 수 있습니다."
            );
        }

        // 검색어 공백 제거 및 소문자 변환
        String normalizedKeyword =
                (keyword == null || keyword.isBlank())
                        ? null
                        : keyword.trim().toLowerCase(Locale.ROOT);

        if (normalizedKeyword != null
                && normalizedKeyword.length() > 100) {
            throw new IllegalArgumentException(
                    "검색어는 100자 이내로 입력해주세요."
            );
        }

        // DB에서 검색 + 필터 + 페이징
        Page<User> users = userRepository.searchUsers(
                normalizedKeyword,
                status,
                UserStatus.DELETED,
                pageable
        );

        // 기존 공통 PageResponse 재사용
        return PageResponse.from(
                users.map(UserDto.Response::from)
        );
    }

    // 통계 조회
    public UserDto.StatsResponse getUserStats() {

        long active =
                userRepository.countByStatus(UserStatus.ACTIVE);

        long inactive =
                userRepository.countByStatus(UserStatus.INACTIVE);

        return new UserDto.StatsResponse(
                active + inactive,
                active,
                inactive
        );
    }

    public List<UserDto.Response> findByOrderManager() {
        return userRepository.findByRole(UserRole.ORDER_MANAGER).stream()
                .map(UserDto.Response::from)
                .toList();
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

    // 직원 ID로 조회하고 존재하지 않으면 예외 발생
    private User findUserByIdOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 직원입니다."
                        )
                );
    }
}
