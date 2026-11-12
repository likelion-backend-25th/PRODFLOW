package net.likelion.bebc25.prodflow.ai.service;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.function.Supplier;

public class AiMcpResultCapture implements ToolCallback {

    private final ToolCallback delegate;
    private final JsonMapper jsonMapper;


    // 한 번의 질문에서 마지막으로 성공한 SQL 결과
    private volatile SqlResult lastResult;

    public AiMcpResultCapture(
            ToolCallback delegate,
            JsonMapper jsonMapper
    ) {
        this.delegate = delegate;
        this.jsonMapper = jsonMapper;
    }

    public record SqlResult(
            String generatedSql,
            String resultJson
    ) {
    }

    public SqlResult getLastResult() {
        return lastResult;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }


    @Override
    public String call(String input) {
        return executeSafely(() -> delegate.call(input));
    }

    @Override
    public String call(String input, ToolContext context) {
        return executeSafely(() -> delegate.call(input, context));
    }

    // MCP 실행 오류가 Gemini까지 전파되지 않도록 처리
    private String executeSafely(Supplier<String> action) {
        try {
            String result = action.get();

            // 기존 SQL 실행 결과 저장 기능 유지
            capture(result);

            // Gemini에 유효한 JSON 전달
            return normalizeJson(result);

        } catch (RuntimeException e) {
            System.err.println("MCP Tool 실행 실패: " + e.getMessage());

            boolean unsafeSql = e.getMessage() != null
                    && e.getMessage().contains("안전하지 않은 SQL");

            return toJson(Map.of(
                    "success", false,
                    "errorCode", unsafeSql ? "UNSAFE_SQL" : "MCP_TOOL_ERROR",
                    "message", unsafeSql
                            ? "SQL 안전성 검사에서 거부되었습니다. 조회 전용 SQL로 다시 시도하세요."
                            : "MCP 도구 실행 중 오류가 발생했습니다. 조회 결과를 추측하지 마세요."
            ));
        }
    }

    // JSON 객체 또는 배열인지 확인
    private String normalizeJson(String result) {
        if (result != null) {
            try {
                JsonNode node = jsonMapper.readTree(result);

                if (node != null && (node.isObject() || node.isArray())) {
                    return result;
                }
            } catch (Exception ignored) {
                // JSON이 아닌 결과는 아래에서 오류 객체로 변환
            }
        }

        return toJson(Map.of(
                "success", false,
                "errorCode", "INVALID_MCP_RESPONSE",
                "message", "MCP 조회 결과가 유효한 JSON이 아닙니다."
        ));
    }

    // 예외 상황에서도 JSON 문자열 반환
    private String toJson(Map<String, Object> value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"MCP 결과 변환 실패\"}";
        }
    }

    // MCP 실행 결과에서 QueryResult JSON 추출
    private void capture(String rawResult) {

        try {
            JsonNode root = jsonMapper.readTree(rawResult);

            if (root.isArray()) {

                for (JsonNode item : root) {
                    JsonNode text = item.path("text");

                    if (text.isTextual()) {
                        saveResult(
                                jsonMapper.readTree(text.asText())
                        );
                    }
                }

            } else {
                saveResult(root);
            }

        } catch (Exception e) {
            System.err.println(
                    "MCP 결과 JSON 변환 실패: " + e.getMessage()
            );
        }
    }

    private void saveResult(JsonNode node) {

        if (!node.hasNonNull("executedSql")
                || !node.path("rows").isArray()) {
            return;
        }

        lastResult = new SqlResult(
                node.path("executedSql").asText(),
                node.toString()
        );

        System.out.println("===== MCP 결과 수집 완료 =====");
    }
}
