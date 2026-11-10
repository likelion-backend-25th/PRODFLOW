package net.likelion.bebc25.prodflow.ai.service;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiMcpToolService {

    private final AiQueryService aiQueryService;

    public AiMcpToolService(AiQueryService aiQueryService) {
        this.aiQueryService = aiQueryService;
    }

    // AI가 생성한 SELECT SQL 실행
    @McpTool(
            name = "execute_readonly_query",
            description = "PRODFLOW의 작업지시, 생산계획, 생산라인 및 변경 이력을 조회한다."
    )
    public AiQueryService.QueryResult executeReadOnlyQuery(
            @McpToolParam(
                    description = "조회할 SELECT SQL",
                    required = true
            )
            String sql
    ) {
        System.out.println("===== MCP SQL 조회 Tool 실행 =====");
        System.out.println("AI가 생성한 SQL: " + sql);

        AiQueryService.QueryResult result =
                aiQueryService.executeReadOnly(sql);

        System.out.println("===== SQL 조회 완료 =====");

        return result;
    }

    // AI가 SQL 생성에 사용할 DB 스키마 조회
    @McpTool(
            name = "get_database_schema",
            description = "조회 가능한 테이블, 컬럼, 자료형 및 외래키 관계를 확인한다."
    )
    public List<AiQueryService.TableSchema> getDatabaseSchema() {

        System.out.println("===== MCP DB 스키마 조회 =====");

        return aiQueryService.getDatabaseSchema();
    }
}