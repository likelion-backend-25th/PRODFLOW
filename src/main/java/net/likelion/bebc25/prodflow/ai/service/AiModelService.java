package net.likelion.bebc25.prodflow.ai.service;

import net.likelion.bebc25.prodflow.ai.dto.AiDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AiModelService {

    private final ChatClient geminiChatClient;
    private final ChatClient groqChatClient;

    public AiModelService(
            GoogleGenAiChatModel geminiModel,
            @Qualifier("groqChatClient") ChatClient groqChatClient
    ) {
        // Spring AI가 자동 등록한 Gemini 모델로 ChatClient 생성
        this.geminiChatClient = ChatClient.create(geminiModel);

        // AiModelConfig에서 직접 등록한 Groq ChatClient 주입
        this.groqChatClient = groqChatClient;
    }

    // 요청 DTO 처리
    public AiDto.AskResponse ask(AiDto.AskRequest request) {

        String model = request.model();
        String message = request.message();

        String answer = switch (model) {

            // Gemini 2개 모델
            case "gemini-3.1-flash-lite",
                 "gemini-2.5-flash-lite" -> geminiChatClient.prompt()
                    .user(message)
                    .options(
                            GoogleGenAiChatOptions.builder()
                                    .model(model))
                    .call()
                    .content();

            // Groq 모델
            case "openai/gpt-oss-20b" -> groqChatClient.prompt()
                    .user(message)
                    .call()
                    .content();

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 AI 모델입니다: " + model
            );
        };
        return new AiDto.AskResponse(model, answer);
    }
}