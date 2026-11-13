# BaseTimeEntity의 PrePersist, PreUpdate

## 목차

- [1. 문제 발생](#1-문제-발생)
- [2. 원인 분석](#2-원인-분석)
- [3. 해결 방법](#3-해결-방법)
- [4. 배운점](#4-배운점)
- [5. 참고자료](#5-참고자료)

## 1. 문제 발생

- 문제 유형
    - JPA Auditing 이벤트 처리
- 현상 설명
    - 엑셀 파일 조회 후 거래처 정보 일괄 등록 시 생성 일자(created_at), 작성자(created_by), 수정 일자(updated_at), 수정자(updated_by)가 엑셀에 적혀져 있는 정보가
      아닌 등록 시점에서 시간과 로그인 사용자로 덮혀씌워지는 현상이 발생

---

## 2. 원인 분석

- BaseTimeEntity에서 해당 컬럼(createdAt 등)에 auditing 어노테이션 (createDate 등)이 정보 입력 유무와 상관없이 등록 시점에 정보를 갱신해버리는 로직을 수행
- 다만, 개별 정보 등록을 위한 auditing 기능은 필수이므로, 기존에 쓰던 auditing 어노테이션을 대체하는 기능이나 로직을 구현해야 함.

---

## 3. 해결 방법

- JPA에서 제공하는 라이프사이클 콜백 어노테이션인 PrePersist와 PreUpdate를 사용
    - PrePersist: insert시 영속화 되기 전에 해당 메소드의 로직 수행
    - PreUpdate: update시 영속화 되기 전에 해당 메소드의 로직 수행
- PrePersist 메소드 로직에서는 createdAt, createdBy, updatedAt, updatedBy 각각 null이 아니면 등록 시점 시간과 로그인 사용자를 등록
- PreUpdate 메소드 로직에서는 updatedAt, updatedBy 각각 null 여부와 상관없이 수정 시점 시간과 로그인 사용자를 등록

    ```java
    // Entity 최초 저장될 때 일시와 사용자의 이름이 존재하지 않으면 자동으로 주입
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (createdBy == null) {
            createdBy = getPrincipalUserName();
        }
        if (updatedBy == null) {
            updatedBy = getPrincipalUserName();
        }
    }
    
    // Entity 수정할 때 수정일시와 사용자의 이름을 자동으로 주입
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        updatedBy = getPrincipalUserName();
    }
    ```

- 로그인 사용자의 정보는 해당 사용자의 이름으로 등록하며, 인증정보가 없는 경우에는 SYSTEM으로 등록

    ```java
    private String getPrincipalUserName() {
        // 현재 실행 중인 스레드의 SecurityContext에서 인증 정보를 가져옴.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    
        // 인증 정보가 없거나 인증되지 않은 경우 SYSTEM을 반환
        if (auth == null || !auth.isAuthenticated()) {
            return "SYSTEM";
        }
    
        // 인증된 사용자 정보(principal)가 프로젝트의 CustomUserDetails인지 확인
        if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            // 현재 CustomUserDetails.getUsername()은 User.name으로 직원 이름을 작성자,수정자로 기록
            return userDetails.getUsername();
        }
    
        // 익명 사용자 또는 CustomUserDetails가 아닌 인증 정보는 SYSTEM으로 기록
        return "SYSTEM";
    }
    ```

---

## 4. 배운점

- JPA auditing 어노테이션이 어떤 로직으로 처리되는지 파악할 수 있었고, 라이프사이클 콜백 어노테이션에 대해서 알 수 있었다.

---

## 5. 참고자료

- https://bepoz-study-diary.tistory.com/384
- https://blog.naver.com/seek316/223353802740