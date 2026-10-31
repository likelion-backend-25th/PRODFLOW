package net.likelion.bebc25.prodflow.productionline.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.global.security.jwt.JwtProvider;
import net.likelion.bebc25.prodflow.productionline.dto.LineDto;
import net.likelion.bebc25.prodflow.productionline.serivce.LineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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


    @Operation(
            summary = "생산라인 등록",
            description = "최고 관리자, 수주 관리자가 생산라인을 등록한다. 초기 상태는 AVAILABLE이다."
    )
    @ApiResponse(
            responseCode = "201",
            description = "등록 성공",
            content = @Content(schema = @Schema(implementation = LineDto.Response.class))
    )
    @ApiResponse(responseCode = "400", description = "라인 이름 공백, 하루 생산량 1 미만, 이름 중복, 등록한 직원을 찾을 수 없음")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @ApiResponse(responseCode = "403", description = "최고 관리자, 수주 관리자가 아님")
    @PostMapping
    public ResponseEntity<LineDto.Response> create(
            @RequestBody LineDto.CreateRequest request, // 이름, 생산 능력, 도입일
            @Parameter(hidden = true) HttpServletRequest httpRequest // HttpServletRequest가 SWAGGER에서 보이지 않음
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


    @Operation(
            summary = "생산라인 수정",
            description = "최고 관리자, 수주 관리자가 라인 이름과 하루 생산량을 수정한다. 생산량이 바뀔 때만 변경 이력을 남긴다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "수정 성공",
            content = @Content(schema = @Schema(implementation = LineDto.Response.class))
    )
    @ApiResponse(responseCode = "400", description = "라인을 찾을 수 없음, 수정한 직원을 찾을 수 없음")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @ApiResponse(responseCode = "403", description = "최고 관리자, 수주 관리자가 아님")
    @PatchMapping("/{lineId}")
    public ResponseEntity<LineDto.Response> update(
            @PathVariable Long lineId, // 수정할 라인 id
            @RequestBody LineDto.UpdateRequest request, // 이름, 생산량, 변경 사유
            @Parameter(hidden = true) HttpServletRequest httpRequest // HttpServletRequest가 SWAGGER에서 보이지 않음
    ) {
        // Authorization: Bearer {토큰}. 토큰 subject는 User.id다.
        // 이름은 토큰에 없으므로 서비스가 id로 User를 조회한다.
        String header = httpRequest.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        Long userId = jwtProvider.getUserId(header.substring(7));
        return ResponseEntity.ok(lineService.update(lineId, request, userId));
    }

    @Operation(
            summary = "생산라인 상태 변경",
            description = "최고 관리자, 수주 관리자, 생산 현장 관리자가 라인 상태를 변경한다. 변경 내용은 status_history에 남긴다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "상태 변경 성공",
            content = @Content(schema = @Schema(implementation = LineDto.Response.class))
    )
    @ApiResponse(responseCode = "400", description = "라인을 찾을 수 없음, 수정한 직원을 찾을 수 없음, 이미 같은 상태")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @ApiResponse(responseCode = "403", description = "최고 관리자, 수주 관리자, 생산 현장 관리자가 아님")
    @PatchMapping("/{lineId}/status")
    public ResponseEntity<LineDto.Response> changeStatus(
            @PathVariable Long lineId,
            @RequestBody LineDto.UpdateStatusRequest request,
            @Parameter(hidden = true) HttpServletRequest httpRequest // HttpServletRequest가 SWAGGER에서 보이지 않음.
    ) {
        // Authorization: Bearer {토큰}. 토큰 subject는 User.id다.
        String header = httpRequest.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        Long userId = jwtProvider.getUserId(header.substring(7));
        return ResponseEntity.ok(lineService.changeStatus(lineId, request, userId));
    }
}
