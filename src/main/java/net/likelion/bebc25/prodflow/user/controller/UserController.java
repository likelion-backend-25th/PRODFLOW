package net.likelion.bebc25.prodflow.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.user.dto.UserDto;
import net.likelion.bebc25.prodflow.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    // 직원 등록
    @PostMapping
    public ResponseEntity<UserDto.Response> createUser(
            @Valid @RequestBody UserDto.CreateRequest request
    ) {
        UserDto.Response response = userService.createUser(request);

        return ResponseEntity.created(URI.create("/api/v1/users/" + response.id())).body(response);
    }

    // 직원 목록 조회
    @GetMapping
    public ResponseEntity<List<UserDto.Response>> getAllUsers() {

        // HTTP 상태 코드: 200 OK
        //Body: 조회된 직원 목록
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // 직원 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<UserDto.Response> getUserById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // 직원 정보 수정
    @PutMapping("/{id}")
    public ResponseEntity<UserDto.Response> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

}
