package net.likelion.bebc25.prodflow.ai.service;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
        String result = delegate.call(input);
        capture(result);
        return result;
    }

    @Override
    public String call(String input, ToolContext context) {
        String result = delegate.call(input, context);
        capture(result);
        return result;
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
