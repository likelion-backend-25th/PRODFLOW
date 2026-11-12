# MCP 도구의 SQL 실행 예외로 인한 Gemini JSON 파싱 오류

## 목차

- [1. 문제 발생](#1-문제-발생)
- [2. 원인 분석](#2-원인-분석)
- [3. 해결 방법](#3-해결-방법)
- [4. 배운점](#4-배운점)
- [5. 참고자료](#5-참고자료)

## 1. 문제 발생

- 문제 유형
    - MCP 도구 응답의 JSON 파싱
- 현상 설명
    - AI 챗봇은 자연어 질문을 SQL로 바꾸고, MCP의 `execute_readonly_query`로 데이터베이스를 조회함.
    - MCP 서버는 위험한 SQL을 막기 위해 실행 전에 안전성 검사를 함.
    - AI가 만든 SQL이 검사에서 거부되면 `안전하지 않은 SQL은 실행할 수 없습니다.` 예외가 발생했음.
    - 실행을 막는 것 자체는 맞았지만, Spring AI와 Gemini가 도구 응답을 읽는 과정에서 `Failed to parse JSON`이 추가로 났음.
    - 잘못된 SQL은 차단됐으나 오류를 응답으로 다루지 못해 AI 요청 전체가 실패했음.

---

## 2. 원인 분석

- 생성된 SQL을 그대로 실행하면 데이터가 바뀌거나 허용되지 않은 테이블에 접근할 수 있어, `AiQueryService`가 실행 전에 검사하고 있었음.
- 읽기 전용 `SELECT`만 허용하고, 검사에 실패하면 예외를 던지도록 되어 있었음.
- `AiMcpResultCapture`는 `delegate.call()` 결과를 `capture()`에 담을 뿐, 도구 실행 예외를 따로 받지 않았음.
- 안전성 검사 예외가 Spring AI 도구 호출 계층까지 올라가고, 일반 오류 문자열이 Gemini의 JSON 파서에 전달됐음.
- 정상 조회 결과와 도구 실패를 구분하지 못한 것이 파싱 오류의 원인이었음.

---

## 3. 해결 방법

- `AiMcpResultCapture`에 `executeSafely()`를 추가하고, `delegate.call()`을 `try-catch`로 감쌈.
- `RuntimeException`이 위로 올라가지 않게 하고, 오류 내용을 JSON 응답으로 바꿈.
- 정상 실행일 때만 기존 `capture()`로 조회 결과를 저장하고, `normalizeJson()`으로 응답이 JSON 객체 또는 배열인지 확인함.
- `AiQueryService`의 안전성 검사는 유지해, 거부된 SQL은 예외를 JSON으로 바꾼 뒤에도 실행되지 않게 함.
- `AiMcpResultCapture`는 SQL 허용 여부를 판단하지 않고, 도구 결과와 오류 응답만 다룸.

    ```java
    private String executeSafely(Supplier<String> action) {
        try {
            String result = action.get();
            capture(result);
            return normalizeJson(result);
        } catch (RuntimeException e) {
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
    ```

- 기존에는 검사에서 막힌 예외가 상위로 전파되어 Gemini에서 `Failed to parse JSON`이 나고 요청 전체가 실패했음.
- 개선 후에는 `executeSafely()`가 예외를 잡아 JSON 오류 응답으로 넘기므로, Spring AI가 도구 실패를 이어서 처리할 수 있음.
- 거부된 쿼리는 계속 실행되지 않고, 정상 응답과 오류 응답 모두 JSON으로 전달됨.

---

## 4. 배운점

- 모델에 넘기는 도구 결과는 예외 문자열이 아니라 JSON이어야 이후 파싱이 이어짐.
- SQL 허용 여부는 조회 서비스가 판단하고, 도구 래퍼는 결과와 오류를 JSON으로 감싸는 역할만 맡김.

---

## 5. 참고자료

- https://docs.spring.io/spring-ai/reference/api/tools.html (`ToolCallback` 실행 구조와 도구 호출 결과 처리)
- https://modelcontextprotocol.io/specification/2025-06-18/server/tools (MCP 도구 실행 오류와 `isError` 응답 규격)
