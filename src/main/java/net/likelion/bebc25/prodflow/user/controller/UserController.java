package net.likelion.bebc25.prodflow.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.user.dto.UserDto;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.service.UserService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

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

    // 직원 검색 + 사용/비활성 필터 + 페이징 처리
    @Operation(
            summary = "직원 목록 검색 및 페이징 조회",
            description = "SUPER_ADMIN 전용. keyword는 이름·이메일·전화번호에서 검색하고 "
                    + "status는 ACTIVE/INACTIVE 중 선택합니다. 삭제 계정은 제외합니다. "
                    + "두 필터는 선택이며 page는 0부터 시작합니다."
    )
    @GetMapping
    public ResponseEntity<PageResponse<UserDto.Response>> getUsers(
            @Parameter(description = "이름, 이메일, 전화번호 검색어 (선택)", example = "김민준")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "계정 상태: ACTIVE 또는 INACTIVE. 미지정 시 전체", example = "ACTIVE")
            @RequestParam(required = false) UserStatus status,

            @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "id",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        return ResponseEntity.ok(userService.getUsers(keyword, status, pageable));
    }

    // 전체 직원 통계 (검색/필터와 관계없이 ACTIVE + INACTIVE 기준)
    @Operation(
            summary = "계정관리 상단 통계 조회",
            description = "SUPER_ADMIN 전용. 삭제된 계정을 제외한 전체·활성·비활성 직원 수를 반환합니다."
    )
    @GetMapping("/stats")
    public ResponseEntity<UserDto.StatsResponse> getUserStats() {
        return ResponseEntity.ok(userService.getUserStats());
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
    public ResponseEntity<UserDto.MeResponse> me(
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

    // 수주 관리자 리스트 조회
    @Operation(
            summary = "수주 관리자 리스트 조회",
            description = "ORDER_MANAGER 권한을 갖고 있는 User들을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/order")
    public ResponseEntity<List<User>> findByOrderManager() {
        return ResponseEntity.ok(userService.findByOrderManager());
    }
}
