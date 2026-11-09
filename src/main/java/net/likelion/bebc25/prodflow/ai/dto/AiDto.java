package net.likelion.bebc25.prodflow.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import net.likelion.bebc25.prodflow.ai.enums.AiMessageRole;

import java.time.LocalDateTime;
import java.util.List;

public class AiDto {

    // AI 모델 호출 요청
    public record AskRequest(

            @NotBlank(message = "모델을 선택해 주세요")
            String model,

            @NotBlank(message = "질문을 입력해 주세요")
            String message
    ) {
    }

    // AI 모델 호출 응답
    public record AskResponse(
            String model,
            String answer
    ) {
    }

    // 대화방 목록 조회 응답
    public record ConversationResponse(
            Long conversationId,
            String title,
            Boolean isClosed,
            LocalDateTime createdAt
    ) {
    }

    // 대화 메시지 조회 응답
    public record MessageResponse(
            Long messageId,
            int sequenceNo,
            AiMessageRole role,
            String content,
            String resultJson,
            LocalDateTime createdAt
    ) {
    }

    // 대화방 상세 및 대화 기록 응답
    public record ConversationDetailResponse(
            Long conversationId,
            String title,
            Boolean isClosed,
            List<MessageResponse> messages
    ) {
    }

    // 대화방 제목 변경 요청
    public record TitleUpdateRequest(
            @NotBlank(message = "제목을  입력해주세요.")
            @Size(max = 20, message = "제목은 20자 이하여야 합니다.")
            String title
    ) {
    }
}
