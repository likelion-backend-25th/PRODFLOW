# MCP 호출 시 JWT 인증 오류 및 Spring Security 오류 디스패치 문제

## 목차

- [1. 문제 발생](#1-문제-발생)
- [2. 원인 분석](#2-원인-분석)
- [3. 해결 방법](#3-해결-방법)
- [4. 배운점](#4-배운점)
- [5. 참고자료](#5-참고자료)

## 1. 문제 발생

- 문제 유형
    - MCP HTTP 요청의 JWT 인증
- 현상 설명
    - 로그인한 사용자가 AI 챗봇으로 데이터베이스 조회를 요청하면 MCP 서버 호출에서 인증 오류가 났음.
    - 사용자 질문 요청은 인증을 통과했지만, 내부 `/mcp` 요청은 익명 사용자로 처리되어 Spring Security가 거부했음.
    - 인증 실패 응답을 만드는 과정에서 `response is already committed` 예외가 추가로 발생했음.
    - 로그인한 사용자인데도 AI가 MCP 도구를 실행하지 못해 조회가 동작하지 않았음.

---

## 2. 원인 분석

- 사용자 질문을 받는 HTTP 요청과 MCP 클라이언트가 `/mcp`로 보내는 HTTP 요청은 서로 다른 요청이었음.
- 최초 요청의 JWT는 Spring Security를 통과했지만, MCP 클라이언트의 새 요청에는 인증 정보가 자동으로 넘어가지 않았음.
- `HttpClientStreamableHttpTransport`가 `Authorization` 헤더를 넣지 않아 MCP 요청의 JWT가 비어 있었음.
- `JwtAuthenticationFilter`는 헤더가 없거나 토큰을 꺼낼 수 없으면 인증 객체를 만들지 않고 다음 필터로 넘겼음.
- `AnonymousAuthenticationFilter`가 요청을 익명으로 두고, 인증이 필요한 `/mcp` 접근을 거부했음.
- 거부 시 `CustomAuthenticationEntryPoint`가 응답을 확정한 뒤, 서블릿이 `/error`로 다시 디스패치하면 그 요청에도 보안 필터가 적용됐음.
- 이미 확정된 응답을 오류 경로에서 다시 처리하려다 `response is already committed`가 났음.

---

## 3. 해결 방법

- `AiModelService`가 MCP 클라이언트를 만들 때 사용자 요청의 JWT를 `Authorization: Bearer` 헤더로 넣음.
- `HttpClientStreamableHttpTransport`의 `requestBuilder()`에 헤더를 추가해, 새 HTTP 요청에도 인증 정보가 실리게 함.
- `/mcp`를 `permitAll()`로 열지 않고, 기존 JWT 검증을 그대로 타게 둠.

    ```java
    var transport = HttpClientStreamableHttpTransport
            .builder(mcpUrl)
            .endpoint("/mcp")
            .requestBuilder(
                    HttpRequest.newBuilder()
                            .header("Authorization", "Bearer " + accessToken)
            )
            .build();
    ```

- `SecurityConfig`에서 일반 API와 오류 디스패치를 구분하고, `DispatcherType.ERROR`인 `/error`만 인증 없이 통과하게 함.
- 모든 `/error`를 열지 않고, 오류 디스패치 여부와 경로를 함께 확인하게 제한함.

    ```java
    .requestMatchers(request ->
            request.getDispatcherType() == DispatcherType.ERROR
                    && "/error".equals(request.getServletPath())
    ).permitAll()
    ```

- MCP 요청에 `Authorization` 헤더가 전달되고 Spring Security 인증을 통과하는 것을 로그로 확인함.
- 인증 실패 뒤 오류 디스패치가 같은 응답을 다시 검사하지 않음.

---

## 4. 배운점

- 서버 안에서 새로 여는 HTTP 요청은 기존 요청의 인증 정보를 물려받지 않으므로, JWT를 헤더에 직접 넣어야 함.
- 오류 응답이 이미 확정된 뒤에는 `/error` 재진입에서 보안 필터가 같은 응답을 다시 쓰지 않게 막아야 함.

---

## 5. 참고자료

- https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html (Spring Security의
  URL 접근 제어와 `DispatcherType.ERROR` 처리)
- https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html (MCP 클라이언트의 HTTP 전송 방식과 인증정보 전달)
- https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html (MCP HTTP 엔드포인트의 인증 및 보안 구성)
