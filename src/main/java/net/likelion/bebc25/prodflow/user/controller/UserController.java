package net.likelion.bebc25.prodflow.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.auth.dto.AuthDto;
import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.user.dto.UserDto;
import net.likelion.bebc25.prodflow.user.service.UserService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Tag(name = "직원 관리", description = "직원 계정 등록·조회·수정 및 본인 정보 조회 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    // 직원 등록
    @Operation(summary = "직원 등록", description = "최고 관리자가 신규 직원을 등록합니다. 임시 비밀번호는 프론트엔드에서 생성하여 전달하며, 등록된 직원은 최초 로그인 후 비밀번호를 변경해야 합니다.")
    @PostMapping
    public ResponseEntity<UserDto.Response> createUser(
            @Valid @RequestBody UserDto.CreateRequest request
    ) {
        UserDto.Response response = userService.createUser(request);

        return ResponseEntity.created(URI.create("/api/v1/users/" + response.id())).body(response);
    }

    // 직원 목록 조회 (페이징)
    @Operation(
            summary = "직원 목록 조회",
            description = "직원 목록을 페이지 단위로 조회합니다. "
                    + "SUPER_ADMIN 권한이 필요합니다."
    )
    @GetMapping
    public ResponseEntity<PageResponse<UserDto.Response>> getUsers(

            @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "id",
                    direction = Sort.Direction.DESC
            ) Pageable pageable

    ) {
        return ResponseEntity.ok(
                userService.getUsers(pageable)
        );
    }

    // 직원 상세 조회
    @Operation(summary = "직원 상세 조회", description = "최고 관리자가 직원 ID로 특정 직원의 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<UserDto.Response> getUserById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // 직원 정보 수정
    @Operation(summary = "직원 정보 수정", description = "최고 관리자가 직원의 이름, 이메일, 전화번호, 권한, 계정 상태를 수정합니다. 임시 비밀번호는 전달했을 때만 재발급됩니다.")
    @PutMapping("/{id}")
    public ResponseEntity<UserDto.Response> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    // 현재 로그인한 사용자 정보 조회
    @Operation(summary = "내 정보 조회", description = "로그인한 직원이 JWT 인증 정보의 사용자 ID를 기준으로 본인 정보를 조회합니다. 다른 직원 ID를 직접 지정할 수 없습니다.")
    @GetMapping("/me")
    public ResponseEntity<AuthDto.MeResponse> me(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        // 인증된 사용자 ID로 사용자 정보 조회
        return ResponseEntity.ok(
                userService.me(principal.getUserId())
        );
    }

    // 직원 소프트 삭제
    @Operation(
            summary = "직원 삭제",
            description = "SUPER_ADMIN 전용. 직원 계정을 물리적으로 삭제하지 않고 DELETED 상태로 변경합니다."
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<UserDto.Response> deleteUser(
            @Parameter(description = "삭제할 직원 ID", example = "1")
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(userService.softDeleteUser(id));
    }
}
