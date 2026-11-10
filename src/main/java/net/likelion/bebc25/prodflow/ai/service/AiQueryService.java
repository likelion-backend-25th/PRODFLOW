package net.likelion.bebc25.prodflow.ai.service;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.AbstractJSqlParser;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.util.TablesNamesFinder;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AiQueryService {


    // 컬럼 정보
    public record ColumnSchema(
            String name,
            String type
    ) {
    }

    // 외래키 정보
    public record ForeignKeySchema(
            String column,
            String referencedTable,
            String referencedColumn
    ) {
    }

    // 테이블 구조 정보
    public record TableSchema(
            String tableName,
            List<ColumnSchema> columns,
            List<ForeignKeySchema> foreignKeys
    ) {
    }

    // 한 번의 AI 조회에서 반환할 최대 행 수
    private static final int MAX_ROWS = 100;

    // SQL 최대 길이
    private static final int MAX_SQL_LENGTH = 5000;

    // AI가 조회할 수 있는 테이블
    private static final Set<String> ALLOWED_TABLES = Set.of(
            // 작업지시 및 생산계획
            "work_order",
            "production_plan",

            // 생산라인
            "line",

            // 수주 품목 및 제품
            "order_product",
            "product",

            // 공통 상태 코드
            "common_code",

            // 상태 변경 이력
            "status_history",

            // 작업지시 변경 이력
            "work_order_history",

            // 라인 생산능력 변경 이력
            "line_capacity_log"
    );

    // 조회 과정에서 허용하는 일반적인 SQL 함수
    private static final Set<String> SAFE_FUNCTIONS = Set.of(
            "count", "sum", "avg", "min", "max",
            "coalesce", "ifnull",
            "upper", "lower", "concat",
            "round", "abs",
            "date", "year", "month", "day",
            "date_format"
    );

    // AI 조회 전용 DB 연결
    private final JdbcTemplate aiJdbcTemplate;

    public AiQueryService(
            @Qualifier("aiDataSource") DataSource aiDataSource
    ) {
        // 기존 JPA용 DataSource가 아닌 AI 전용 연결 사용
        this.aiJdbcTemplate = new JdbcTemplate(aiDataSource);

        // 한 번에 반환할 최대 조회 행 수
        this.aiJdbcTemplate.setMaxRows(MAX_ROWS);

        // SQL 실행 제한 시간: 5초
        this.aiJdbcTemplate.setQueryTimeout(5);
    }


    // AI가 생성한 SQL을 검증하고 실행
    public QueryResult executeReadOnly(String sql) {

        // 1. SQL 입력값 검증
        if (sql == null || sql.isBlank()
                || sql.length() > MAX_SQL_LENGTH) {
            throw new IllegalArgumentException(
                    "SQL이 비어 있거나 허용 길이를 초과했습니다."
            );
        }

        PlainSelect select;

        try {
            // 2. MySQL 문법으로 SQL 분석
            var statements = CCJSqlParserUtil.parseStatements(
                    sql,
                    parser -> parser.withDialect(
                            AbstractJSqlParser.Dialect.MYSQL
                    )
            );

            // 3. 단일 SELECT 문만 허용
            // 첫 버전에서는 CTE, UNION 등의 복합 구조는 제외
            if (statements.size() != 1
                    || !(statements.get(0) instanceof PlainSelect)) {
                throw new IllegalArgumentException(
                        "단일 SELECT 쿼리만 실행할 수 있습니다."
                );
            }

            select = (PlainSelect) statements.get(0);

            // 4. SQL의 데이터 변경 가능성 검사
            var features = select.getFeatures(
                    function -> SAFE_FUNCTIONS.contains(
                            function.toLowerCase(Locale.ROOT)
                    )
            );

            if (features.mayModifyData()
                    || features.modifiesSchema()
                    || features.isOpaque()
                    || !features.returnsResultSet()) {
                throw new IllegalArgumentException(
                        "안전하지 않은 SQL은 실행할 수 없습니다."
                );
            }

            // 5. 실제 참조하는 테이블 검사
            // SQL에서 참조하는 테이블 추출
            // statements.get(0)은 Statement 타입이므로 메서드 충돌 방지
            Set<String> tables =
                    new TablesNamesFinder<>().getTables(statements.get(0));

            System.out.println("AI가 생성한 SQL: " + sql);
            System.out.println("SQL에서 참조한 테이블: " + tables);
            System.out.println("허용된 테이블: " + ALLOWED_TABLES);

            boolean invalidTable = tables.isEmpty()
                    || tables.stream()
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .anyMatch(name -> !ALLOWED_TABLES.contains(name));

            if (invalidTable) {
                throw new IllegalArgumentException(
                        "조회가 허용되지 않은 테이블이 포함되어 있습니다."
                );
            }

        } catch (JSQLParserException e) {
            throw new IllegalArgumentException(
                    "SQL 문법을 분석할 수 없습니다.", e
            );
        }

        // 6. 검증된 SQL 실행
        // 조회 전용 MySQL 계정(prodflow_ai) 사용
        String executedSql = select.toString();

        List<Map<String, Object>> rows =
                aiJdbcTemplate.queryForList(executedSql);

        // 7. 조회 결과 반환
        return new QueryResult(
                executedSql,
                rows.size(),
                rows
        );
    }

    // MCP Tool에 전달할 조회 결과
    public record QueryResult(
            String executedSql,
            int rowCount,
            List<Map<String, Object>> rows
    ) {
    }


    /**
     * AI가 SQL을 생성하기 전에 실제 DB 구조를 확인하기 위한 메서드
     * <p>
     * 주요 기능
     * 1. AI 전용 DB에 연결
     * 2. 조회가 허용된 테이블 목록 확인
     * 3. 각 테이블의 컬럼명과 데이터 타입 조회
     * 4. 테이블 간 외래키 관계 조회
     * 5. 조회한 스키마 정보를 MCP Tool에 반환
     * <p>
     * 실제 업무 데이터는 조회하지 않으며,
     * SQL 생성에 필요한 DB 구조 정보만 반환한다.
     */
    public List<TableSchema> getDatabaseSchema() {

        // AI 전용 DB 연결을 통한 메타데이터 조회
        return aiJdbcTemplate.execute(
                (ConnectionCallback<List<TableSchema>>) connection -> {

                    var metadata = connection.getMetaData();
                    String database = connection.getCatalog();
                    List<TableSchema> result = new ArrayList<>();

                    // 현재 DB의 테이블 및 뷰 목록 조회
                    try (ResultSet tables = metadata.getTables(
                            database,
                            null,
                            "%",
                            new String[]{"TABLE", "VIEW"}
                    )) {

                        while (tables.next()) {

                            String tableName = tables.getString("TABLE_NAME");

                            // AI 조회 허용 테이블만 처리
                            if (!ALLOWED_TABLES.contains(
                                    tableName.toLowerCase(Locale.ROOT))) {
                                continue;
                            }

                            List<ColumnSchema> columns = new ArrayList<>();

                            // 컬럼명 및 데이터 타입 조회
                            try (ResultSet columnRs = metadata.getColumns(
                                    database, null, tableName, "%"
                            )) {
                                while (columnRs.next()) {
                                    columns.add(new ColumnSchema(
                                            columnRs.getString("COLUMN_NAME"),
                                            columnRs.getString("TYPE_NAME")
                                    ));
                                }
                            }

                            List<ForeignKeySchema> foreignKeys =
                                    new ArrayList<>();

                            // 외래키 및 참조 테이블 관계 조회
                            try (ResultSet fkRs = metadata.getImportedKeys(
                                    database, null, tableName
                            )) {
                                while (fkRs.next()) {
                                    foreignKeys.add(new ForeignKeySchema(
                                            fkRs.getString("FKCOLUMN_NAME"),
                                            fkRs.getString("PKTABLE_NAME"),
                                            fkRs.getString("PKCOLUMN_NAME")
                                    ));
                                }
                            }

                            // 테이블별 스키마 정보 구성
                            result.add(new TableSchema(
                                    tableName,
                                    columns,
                                    foreignKeys
                            ));
                        }
                    }
                    return result;
                }
        );
    }

    // AI 조회 전용 계정 연결 확인 (계정 점검용 코드)
    public Map<String, Object> checkConnection() {
        return aiJdbcTemplate.queryForMap(
                "SELECT CURRENT_USER() AS db_user, DATABASE() AS db_name"
        );
    }
}
