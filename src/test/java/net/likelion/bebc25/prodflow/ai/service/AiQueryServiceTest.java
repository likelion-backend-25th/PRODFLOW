package net.likelion.bebc25.prodflow.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@Transactional
class AiQueryServiceTest {

    @Autowired
    AiQueryService aiQueryService;

    @Autowired
    AiMcpToolService aiMcpToolService;

    @TestConfiguration
    static class SharedAiDataSource {

        // 조회 검증은 Hibernate가 만든 테스트 DB를 그대로 본다.
        @Bean(name = "aiDataSource", defaultCandidate = false)
        DataSource aiDataSource(DataSource dataSource) {
            return new DelegatingDataSource(dataSource);
        }
    }

    @Test
    @DisplayName("허용된 작업지시 조회는 예정일과 현재 상태 코드를 반환한다")
    void executeAllowedWorkOrderQuery() {
        AiQueryService.QueryResult result = aiQueryService.executeReadOnly("""
                SELECT w.work_no,
                       w.planned_start_date,
                       c.code
                FROM work_order w
                JOIN common_code c ON c.id = w.common_code_id
                WHERE w.work_no = 'WO-2026-006'
                """);

        assertThat(result.rowCount()).isEqualTo(1);
        Map<String, Object> row = result.rows().getFirst();
        assertThat(value(row, "work_no")).isEqualTo("WO-2026-006");
        assertThat(value(row, "planned_start_date")).hasToString("2026-11-06");
        assertThat(value(row, "code")).isEqualTo("PRODUCE");
    }

    @Test
    @DisplayName("상태 변경 이력은 status_history에서 작업지시 단위로 조회한다")
    void executeStatusHistoryQuery() {
        AiQueryService.QueryResult result = aiMcpToolService.executeReadOnlyQuery("""
                SELECT entity, entity_id, from_status, to_status
                FROM status_history
                WHERE entity = 'WORK_ORDER'
                  AND entity_id = 6
                  AND to_status = 'PRODUCE'
                """);

        assertThat(result.rowCount()).isEqualTo(1);
        Map<String, Object> row = result.rows().getFirst();
        assertThat(value(row, "from_status")).isEqualTo("PREPARE");
        assertThat(value(row, "to_status")).isEqualTo("PRODUCE");
    }

    @Test
    @DisplayName("스키마는 허용 테이블만 반환하고 상태 이력의 시각 컬럼을 포함한다")
    void schemaContainsAllowedTablesOnly() {
        var schemas = aiMcpToolService.getDatabaseSchema();

        assertThat(schemas).extracting(schema -> schema.tableName().toLowerCase(Locale.ROOT))
                .contains("work_order", "production_plan", "line", "status_history", "work_order_history",
                        "user", "customer_order")
                .doesNotContain("ai_conversation", "ai_message", "refresh_token");
        assertThat(schemas)
                .filteredOn(schema -> schema.tableName().equalsIgnoreCase("status_history"))
                .singleElement()
                .satisfies(schema -> assertThat(schema.columns())
                        .extracting(column -> column.name().toLowerCase(Locale.ROOT))
                        .contains("created_at", "from_status", "to_status", "entity_id")
                        .doesNotContain("changed_at"));
        assertThat(schemas)
                .filteredOn(schema -> schema.tableName().equalsIgnoreCase("work_order"))
                .singleElement()
                .satisfies(schema -> assertThat(schema.foreignKeys())
                        .anySatisfy(key -> {
                            assertThat(key.column()).isEqualToIgnoringCase("common_code_id");
                            assertThat(key.referencedTable()).isEqualToIgnoringCase("common_code");
                        }));
    }

    @Test
    @DisplayName("빈 SQL과 길이 초과 SQL은 실행하지 않는다")
    void rejectEmptyOrTooLongSql() {
        assertThatThrownBy(() -> aiQueryService.executeReadOnly("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SQL이 비어 있거나 허용 길이를 초과했습니다.");
        assertThatThrownBy(() -> aiQueryService.executeReadOnly("SELECT 1 FROM work_order " + "x".repeat(5000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SQL이 비어 있거나 허용 길이를 초과했습니다.");
    }

    @Test
    @DisplayName("변경 문과 복합 SELECT와 허용되지 않은 테이블은 거절한다")
    void rejectUnsafeSql() {
        assertThatThrownBy(() -> aiQueryService.executeReadOnly(
                "INSERT INTO work_order(work_no) VALUES ('WO-HACK')"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("단일 SELECT 쿼리만 실행할 수 있습니다.");
        assertThatThrownBy(() -> aiQueryService.executeReadOnly(
                "SELECT work_no FROM work_order UNION SELECT work_no FROM work_order"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("단일 SELECT 쿼리만 실행할 수 있습니다.");
        assertThatThrownBy(() -> aiQueryService.executeReadOnly("SELECT id FROM ai_conversation"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회가 허용되지 않은 테이블이 포함되어 있습니다.");
        assertThatThrownBy(() -> aiQueryService.executeReadOnly("SELECT id FROM ai_message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회가 허용되지 않은 테이블이 포함되어 있습니다.");
        assertThatThrownBy(() -> aiQueryService.executeReadOnly("SELECT id FROM refresh_token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회가 허용되지 않은 테이블이 포함되어 있습니다.");
        assertThatThrownBy(() -> aiQueryService.executeReadOnly(
                "SELECT CONVERT_TZ(planned_start_date, 'UTC', 'Asia/Seoul') FROM work_order"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("안전하지 않은 SQL은 실행할 수 없습니다.");
    }

    private Object value(Map<String, Object> row, String column) {
        return row.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(column))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
    }
}
