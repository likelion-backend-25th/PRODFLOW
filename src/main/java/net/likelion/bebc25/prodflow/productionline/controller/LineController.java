package net.likelion.bebc25.prodflow.productionline.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.global.security.jwt.JwtProvider;
import net.likelion.bebc25.prodflow.productionline.dto.LineDto;
import net.likelion.bebc25.prodflow.productionline.serivce.LineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("api/v1/lines")
@RequiredArgsConstructor // final 받는 필드를 자동 생성
public class LineController {

    private final LineService lineService; // 등록 로직은 서비스
    private final JwtProvider jwtProvider; // 토큰 subject에서 직원 id를 꺼낸다

    
    @PostMapping
    public ResponseEntity<LineDto.Response> create(
            @RequestBody LineDto.CreateRequest request, // 이름, 생산 능력, 도입일
            HttpServletRequest httpRequest
    ) {
        // Authorization: Bearer {토큰}. 토큰 subject는 User.id다.
        // 이름은 토큰에 없으므로 서비스가 id로 User를 조회한다.
        String header = httpRequest.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        Long userId = jwtProvider.getUserId(header.substring(7));
        LineDto.Response response = lineService.create(request, userId);
        return ResponseEntity.created(URI.create("api/v1/lines" + response.id())).body(response);
    }

}
