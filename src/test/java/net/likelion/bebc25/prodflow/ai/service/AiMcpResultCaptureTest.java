package net.likelion.bebc25.prodflow.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AiMcpResultCaptureTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("조회 결과 JSON에서 실행한 SQL을 남긴다")
    void captureQueryResult() {
        AiMcpResultCapture capture = new AiMcpResultCapture(callback("""
                {"executedSql":"SELECT work_no FROM work_order","rowCount":1,"rows":[{"work_no":"WO-2026-006"}]}
                """), jsonMapper);

        capture.call("ignored");

        assertThat(capture.getLastResult().generatedSql()).isEqualTo("SELECT work_no FROM work_order");
        assertThat(capture.getLastResult().resultJson()).contains("WO-2026-006");
    }

    @Test
    @DisplayName("MCP 텍스트 배열 안의 조회 결과도 남긴다")
    void captureWrappedQueryResult() {
        String payload = """
                [{"text":"{\\"executedSql\\":\\"SELECT code FROM common_code\\",\\"rows\\":[]}"}]
                """;
        AiMcpResultCapture capture = new AiMcpResultCapture(callback(payload), jsonMapper);

        capture.call("ignored");

        assertThat(capture.getLastResult().generatedSql()).isEqualTo("SELECT code FROM common_code");
    }

    @Test
    @DisplayName("조회 결과가 아닌 응답은 마지막 SQL로 남기지 않는다")
    void ignoreNonQueryPayload() {
        AiMcpResultCapture capture = new AiMcpResultCapture(callback("스키마 설명"), jsonMapper);

        capture.call("ignored");

        assertThat(capture.getLastResult()).isNull();
    }

    private ToolCallback callback(String result) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("execute_readonly_query")
                        .description("조회")
                        .inputSchema("{}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return result;
            }
        };
    }
}
