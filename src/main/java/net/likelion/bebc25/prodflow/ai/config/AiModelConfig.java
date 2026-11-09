package net.likelion.bebc25.prodflow.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiModelConfig {
    /**
     * AI 모델 연결 설정
     * <p>
     * Gemini:
     * - spring-ai-starter-model-google-genai 의존성을 통해 자동 구성됨.
     * - application.yml의 spring.ai.google.genai 설정을 사용함.
     * - 별도의 ChatModel Bean 생성 없이 자동 등록된 모델을 사용함.
     * <p>
     * Groq:
     * - OpenAI 호환 API를 사용하므로 직접 설정이 필요함.
     * - application.yml의 groq.api-key를 사용함.
     * - OpenAiChatModel을 생성하여 ChatClient Bean으로 등록함.
     * <p>
     * 실제 AI 모델 호출은 AiService에서 수행함.
     */
    @Bean("groqChatClient")
    public ChatClient groqChatClient(
            @Value("${groq.api-key}") String apiKey
    ) {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .baseUrl("https://api.groq.com/openai/v1")
                        .apiKey(apiKey)
                        .model("openai/gpt-oss-20b")
                        .build())
                .build();

        return ChatClient.create(model);
    }
}
