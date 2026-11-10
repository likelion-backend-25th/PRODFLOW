package net.likelion.bebc25.prodflow.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.ai.dto.AiDto;
import net.likelion.bebc25.prodflow.ai.service.AiChatService;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/conversations")
@Tag(name = "AI 대화방", description = "AI 대화방 생성 및 관리 API")
public class AiChatController {

    private final AiChatService aiChatService;

    @Operation(
            summary = "AI 대화방 생성",
            description = "로그인한 사용자의 AI 대화방을 생성한다."
    )
    @ApiResponse(responseCode = "201", description = "대화방 생성 성공")
    @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    @PostMapping
    public ResponseEntity<Long> createConversation(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();

        Long conversationId =
                aiChatService.createConversation(userId);

        return ResponseEntity.status(201).body(conversationId);
    }

    @Operation(
            summary = "AI 대화방 종료",
            description = "로그인한 사용자가 본인의 AI 대화방을 종료한다. "
                    + "종료된 대화방은 기존 대화 기록을 유지하지만 "
                    + "새로운 질문을 전송할 수 없다."
    )
    @ApiResponse(responseCode = "204", description = "대화방 종료 성공")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @ApiResponse(responseCode = "404", description = "대화방이 존재하지 않거나 본인 대화방이 아님")
    @ApiResponse(responseCode = "409", description = "이미 종료된 대화방")
    @PatchMapping("/{conversationId}/close")
    public ResponseEntity<Void> closeConversation(
            @Parameter(description = "대화방 ID", example = "1")
            @PathVariable Long conversationId,

            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        aiChatService.closeConversation(
                conversationId,
                userDetails.getUserId()
        );

        return ResponseEntity.noContent().build();
    }

    // 본인 대화방 목록 조회
    @Operation(
            summary = "AI 대화방 목록 조회",
            description = "로그인한 사용자가 생성한 대화방 목록을 최신순으로 조회한다. "
                    + "다른 사용자의 대화방은 조회되지 않는다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "대화방 목록 조회 성공",
            content = @Content(
                    array = @ArraySchema(
                            schema = @Schema(implementation = AiDto.ConversationResponse.class)
                    )
            )
    )
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @GetMapping
    public ResponseEntity<List<AiDto.ConversationResponse>> getMyConversations(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return ResponseEntity.ok(
                aiChatService.getMyConversations(userId)
        );
    }

    // 대화방 상세 및 이전 대화 기록 조회
    @Operation(
            summary = "AI 대화방 상세 조회",
            description = "로그인한 사용자의 대화방과 이전 메시지 기록을 조회한다. "
                    + "메시지는 순서번호 오름차순으로 반환하며, 종료된 대화방도 조회할 수 있다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "대화방 상세 조회 성공",
            content = @Content(
                    schema = @Schema(implementation = AiDto.ConversationDetailResponse.class)
            )
    )
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @ApiResponse(responseCode = "404", description = "대화방이 존재하지 않거나 본인 대화방이 아님")
    @GetMapping("/{conversationId}")
    public ResponseEntity<AiDto.ConversationDetailResponse> getConversationDetail(
            @Parameter(description = "대화방 ID", example = "1")
            @PathVariable Long conversationId,

            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return ResponseEntity.ok(
                aiChatService.getConversationDetail(conversationId, userId)
        );
    }

    // 대화방 제목 변경
    @Operation(
            summary = "AI 대화방 제목 변경",
            description = "로그인한 사용자가 본인 대화방의 제목을 변경한다. "
                    + "변경된 제목은 자동으로 덮어쓰지 않는다."
    )
    @ApiResponse(responseCode = "204", description = "대화방 제목 변경 성공")
    @ApiResponse(responseCode = "400", description = "제목 입력값이 올바르지 않음")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @ApiResponse(responseCode = "404", description = "대화방을 찾을 수 없음")
    @PatchMapping("/{conversationId}/title")
    public ResponseEntity<Void> changeTitle(
            @Parameter(description = "대화방 ID", example = "1")
            @PathVariable Long conversationId,

            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "userId") Long userId,

            @Valid @RequestBody AiDto.TitleUpdateRequest request
    ) {
        aiChatService.changeConversationTitle(
                conversationId,
                userId,
                request.title()
        );

        return ResponseEntity.noContent().build();
    }
}
