package net.likelion.bebc25.prodflow.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.user.dto.UserDto;
import net.likelion.bebc25.prodflow.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    // 직원 등록
    @PostMapping
    public ResponseEntity<UserDto.Response> create(
            @Valid @RequestBody UserDto.CreateRequest request
    ) {
        UserDto.Response response = userService.create(request);

        return ResponseEntity.created(URI.create("/api/v1/users/" + response.id())).body(response);
    }
}
