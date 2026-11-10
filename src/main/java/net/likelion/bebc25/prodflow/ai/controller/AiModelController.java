package net.likelion.bebc25.prodflow.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.ai.dto.AiDto;
import net.likelion.bebc25.prodflow.ai.service.AiChatService;
import net.likelion.bebc25.prodflow.ai.service.AiModelService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai")
@Tag(name = "AI 모델", description = "AI 모델 질문 및 답변 API")
public class AiModelController {

    private final AiModelService aiModelService;
    private final AiChatService aiChatService;

    @Operation(
            summary = "AI 질문 및 답변",
            description = "로그인한 사용자가 자신의 대화방에서 AI 모델에 질문한다. "
                    + "Gemini 3.1 Flash-Lite, Gemini 2.5 Flash-Lite, Groq GPT-OSS 20B를 지원한다. "
                    + "AI 응답을 받은 후 사용자 질문과 AI 답변을 대화 기록에 저장한다. "
                    + "종료된 대화방에는 질문할 수 없다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "AI 응답 성공",
            content = @Content(
                    schema = @Schema(implementation = AiDto.AskResponse.class)
            )
    )
    @ApiResponse(responseCode = "400", description = "요청값 또는 모델 선택이 올바르지 않음")
    @ApiResponse(responseCode = "401", description = "액세스 토큰이 없거나 유효하지 않음")
    @ApiResponse(responseCode = "404", description = "대화방이 존재하지 않거나 접근할 수 없음")
    @ApiResponse(responseCode = "409", description = "종료된 대화방")
    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<AiDto.AskResponse> ask(

            @Parameter(description = "대화방 ID", example = "1")
            @PathVariable Long conversationId,

            // JWT에서 인증된 사용자 ID 추출
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "userId") Long userId,

            // MCP Server에 전달할 JWT
            @Parameter(hidden = true)
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,

            @Valid @RequestBody AiDto.AskRequest request
    ) {
        // 1. 대화방 소유자 및 종료 여부 확인
        aiChatService.validateConversation(conversationId, userId);

        // 2. Authorization 헤더에서 Bearer 토큰 추출
        if (!authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException(
                    "올바르지 않은 인증 토큰 형식입니다."
            );
        }

        String accessToken = authorization.substring(7);

        if (accessToken.isBlank()) {
            throw new IllegalArgumentException(
                    "인증 토큰이 없습니다."
            );
        }

        // 3. AI 모델 호출 및 MCP SQL 실행 결과 수집
        AiDto.AskExecution result = aiModelService.askWithDetails(
                request,
                accessToken
        );

        // 4. 사용자 질문, AI 답변 및 SQL 실행 결과 저장
        aiChatService.saveMessage(
                conversationId,
                userId,
                request.message(),
                result
        );

// 5. 기존 API 응답 형식 유지
        return ResponseEntity.ok(
                new AiDto.AskResponse(
                        result.model(),
                        result.answer()
                )
        );
    }
}
