package net.likelion.bebc25.prodflow.ai.service;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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

    // AI가 SQL 생성에 사용할 DB 전체 스키마 조회
    @McpTool(
            name = "get_database_schema",
            description = "조회 가능한 테이블, 컬럼, 자료형 및 외래키 관계를 확인한다."
    )
    public List<AiQueryService.TableSchema> getDatabaseSchema() {

        System.out.println("===== MCP DB 스키마 조회 =====");

        return aiQueryService.getDatabaseSchema();
    }


    @McpTool(
            name = "get_table_schema",
            description = "지정한 테이블의 컬럼, 자료형 및 외래키 관계만 조회한다."
    )
    public List<AiQueryService.TableSchema> getTableSchema(
            @McpToolParam(
                    description = "조회할 테이블명. 여러 개는 쉼표로 구분",
                    required = true
            )
            String tableNames
    ) {
        // 요청한 테이블명 검증
        if (tableNames == null || tableNames.isBlank()) {
            throw new IllegalArgumentException("테이블명을 입력해야 합니다.");
        }

        // 테이블명을 소문자로 변환하고 중복 제거
        Set<String> requested = Arrays.stream(tableNames.split(",", -1))
                .map(String::trim)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        // 잘못된 테이블명 입력 차단
        if (requested.contains("")) {
            throw new IllegalArgumentException("유효하지 않은 테이블명입니다.");
        }

        // 기존 서비스를 통해 전체 허용 스키마 조회
        List<AiQueryService.TableSchema> schemas =
                aiQueryService.getDatabaseSchema();

        // AI가 요청한 테이블의 스키마만 추출
        List<AiQueryService.TableSchema> result = schemas.stream()
                .filter(schema -> requested.contains(
                        schema.tableName().toLowerCase(Locale.ROOT)
                ))
                .toList();

        // 허용되지 않거나 존재하지 않는 테이블 차단
        if (result.size() != requested.size()) {
            throw new IllegalArgumentException(
                    "허용되지 않거나 존재하지 않는 테이블입니다."
            );
        }
        return result;
    }

}