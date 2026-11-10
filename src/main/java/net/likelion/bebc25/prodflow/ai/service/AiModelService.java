package net.likelion.bebc25.prodflow.ai.service;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import net.likelion.bebc25.prodflow.ai.dto.AiDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

@Service
public class AiModelService {

    private final ChatClient geminiChatClient;
    private final ChatClient groqChatClient;
    // MCP 실행 결과를 JSON으로 분석
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final String systemPrompt;

    // application.yml에 설정한 MCP 서버 주소
    @Value("${prodflow.mcp.url:http://127.0.0.1:8080}")
    private String mcpUrl;

    public AiModelService(
            GoogleGenAiChatModel geminiModel,
            @Qualifier("groqChatClient") ChatClient groqChatClient,
            @Value("classpath:prompts/prodflow-system.txt") Resource promptResource
    ) {
        // Gemini ChatClient 생성
        this.geminiChatClient = ChatClient.create(geminiModel);

        // 기존 Groq ChatClient 주입
        this.groqChatClient = groqChatClient;

        // 애플리케이션 시작 시 시스템 프롬프트 로드
        try (var inputStream = promptResource.getInputStream()) {
            this.systemPrompt = new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                    "AI 시스템 프롬프트 파일을 읽을 수 없습니다.", e
            );
        }
    }

    // 기존 API 응답 형식 유지
    public AiDto.AskResponse ask(
            AiDto.AskRequest request,
            String accessToken
    ) {
        AiDto.AskExecution result =
                askWithDetails(request, accessToken);

        return new AiDto.AskResponse(
                result.model(),
                result.answer()
        );
    }

    // AI 답변과 MCP SQL 실행 결과 반환
    public AiDto.AskExecution askWithDetails(
            AiDto.AskRequest request,
            String accessToken
    ) {
        String model = request.model();

        // 요청한 AI 모델 선택
        ChatClient selectedClient = switch (model) {
            case "gemini-3.1-flash-lite",
                 "gemini-2.5-flash-lite" -> geminiChatClient;

            case "openai/gpt-oss-20b" -> groqChatClient;

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 AI 모델입니다: " + model
            );
        };

        // MCP 실행 결과까지 반환
        return askWithMcp(
                selectedClient,
                request.message(),
                accessToken,
                model
        );
    }

    // AI 모델이 MCP Tool을 통해 실제 DB 데이터를 조회하도록 연결
    private AiDto.AskExecution askWithMcp(
            ChatClient chatClient,
            String question,
            String accessToken,
            String model
    ) {

        // MCP 호출에 필요한 JWT 확인
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException(
                    "MCP 조회를 위한 인증 정보가 없습니다."
            );
        }

        // 1. MCP 서버 연결 및 JWT 전달
        var transport = HttpClientStreamableHttpTransport
                .builder(mcpUrl)
                .endpoint("/mcp")
                .requestBuilder(
                        HttpRequest.newBuilder()
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                )
                .build();

        // 2. MCP Client 생성 및 초기화
        try (var mcpClient = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build()) {

            mcpClient.initialize();

            // 3. MCP 서버에 등록된 Tool 목록 조회
            var tools = mcpClient.listTools().tools();

            tools.forEach(tool ->
                    System.out.println("MCP Tool: " + tool.name())
            );

            // 필수 MCP Tool 등록 확인
            for (String toolName : new String[]{
                    "get_database_schema",
                    "execute_readonly_query"
            }) {
                boolean registered = tools.stream()
                        .anyMatch(tool -> toolName.equals(tool.name()));

                if (!registered) {
                    throw new IllegalStateException(
                            "MCP 서버에 Tool이 등록되지 않았습니다: " + toolName
                    );
                }
            }

            // 4. MCP Tool을 Spring AI ToolCallback으로 변환
            var mcpTools = SyncMcpToolCallbackProvider.builder()
                    .mcpClients(mcpClient)
                    .build();

            var callbacks = mcpTools.getToolCallbacks();

            var callbackNames = Arrays.stream(callbacks)
                    .map(callback -> callback.getToolDefinition().name())
                    .toList();

            System.out.println("AI에 등록된 Tool: " + callbackNames);

            // AI에 전달할 Tool 등록 확인
            for (String toolName : new String[]{
                    "get_database_schema",
                    "execute_readonly_query"
            }) {
                if (!callbackNames.contains(toolName)) {
                    throw new IllegalStateException(
                            "AI에 Tool이 등록되지 않았습니다: " + toolName
                    );
                }
            }

            // SQL 조회 결과 수집
            AiMcpResultCapture queryCapture = null;

            ToolCallback[] recordingCallbacks =
                    new ToolCallback[callbacks.length];

            for (int i = 0; i < callbacks.length; i++) {

                ToolCallback callback = callbacks[i];

                if ("execute_readonly_query".equals(
                        callback.getToolDefinition().name()
                )) {
                    queryCapture = new AiMcpResultCapture(callback, jsonMapper);
                    recordingCallbacks[i] = queryCapture;
                } else {
                    recordingCallbacks[i] = callback;
                }
            }

            // 5. AI 모델에 시스템 프롬프트와 MCP Tool 제공
            var prompt = chatClient.prompt()
                    .system(systemPrompt)
                    .user(question)
                    .tools(recordingCallbacks);

            // 6. Gemini 모델인 경우 선택한 모델명 적용
            if (model.startsWith("gemini-")) {
                prompt.options(
                        GoogleGenAiChatOptions.builder()
                                .model(model)
                );
            }

            // 7. MCP Tool 실행 후 최종 AI 답변 반환
            String answer = prompt.call().content();
            AiMcpResultCapture.SqlResult sqlResult =
                    queryCapture == null ? null : queryCapture.getLastResult();

            return new AiDto.AskExecution(
                    model,
                    answer,
                    sqlResult == null ? null : sqlResult.generatedSql(),
                    sqlResult == null ? null : sqlResult.resultJson());

        }
    }
}

