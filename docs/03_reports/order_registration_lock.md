# 동시 수주 등록 시 생산 물량이 중복 예약된 문제

## 목차

- [1. 문제 발생](#1-문제-발생)
- [2. 원인 분석](#2-원인-분석)
- [3. 해결 방법](#3-해결-방법)
- [4. 배운점](#4-배운점)
- [5. 참고자료](#5-참고자료)

## 1. 문제 발생

- 문제 유형
    - 동시 요청 처리 / JPA 비관적 잠금 / 트랜잭션 격리 수준
- 현상 설명
    - 여러 담당자가 동시에 수주를 등록하면서 같은 생산 여유가 중복 예약되는 문제 발생
    - 두 요청이 모두 등록 가능하다고 판단하여, 납기 내 생산 가능한 수량을 초과하는 주문이 등록됨
    - 개별 등록에서는 정상 동작했으나, 동시 등록에서는 예약 물량이 충돌함
    - 생산 일정 조회부터 수주 저장까지의 처리 순서를 점검함

---

## 2. 원인 분석

1. 생산 여유 조회와 수주 저장 사이에서 요청이 겹침
    - 수주 등록은 기존 예약 조회 → 생산 여유 계산 → 납기 검증 → 저장 순서로 처리됨
    - 첫 번째 요청이 저장되기 전에 두 번째 요청도 같은 예약 내역을 조회함
    - 두 요청이 동일한 생산 여유를 기준으로 검증을 통과함
    - 아래는 충돌 지점을 설명하기 위해 처리 흐름을 축약한 코드임

    ```java
    // 두 요청이 같은 시점의 생산 여유를 읽을 수 있음
    long available = calculateAvailableQuantity(request.dueDate());

    if (request.quantity() > available) {
        throw new IllegalArgumentException("선택한 납기까지 생산할 수 없습니다.");
    }

    // 조회 이후 다른 요청도 검증을 통과한 상태에서 각각 저장될 수 있음
    saveOrderAndItems(request);
    ```

2. 트랜잭션만으로는 동시 실행을 막을 수 없음
    - 트랜잭션은 관련 저장을 함께 확정하거나 실패 시 되돌리는 기능임
    - 다른 요청의 조회와 계산까지 대기시키지는 않음
    - 생산 여유 확인부터 저장까지 순서대로 처리할 잠금이 필요했음
3. 견적별 잠금만으로는 생산 여유를 보호하지 못함
    - 같은 견적을 잠그면 해당 견적의 중복 등록은 방지 가능함
    - 서로 다른 견적은 다른 데이터를 잠그므로 동시에 등록이 진행될 수 있음
    - 공장의 생산 여유를 공유하는 모든 수주 등록에 공통 잠금이 필요했음
4. 잠금 대기 후 조회 기준도 함께 점검함
    - MySQL의 `REPEATABLE_READ`에서는 일반 조회가 이전 조회 기준을 유지할 수 있음
    - 잠금 획득 전에 조회 기준이 만들어졌다면, 대기 후 앞선 주문이 조회에 반영되지 않을 수 있음
    - 먼저 확정된 수주를 반영하려면 격리 수준도 함께 조정해야 했음

---

## 3. 해결 방법

1. 생산 여유 계산 전에 공통 DB 잠금 적용함
    - 모든 수주 등록이 사용하는 ‘수주 대기 상태’ 데이터 한 건을 잠금 기준으로 사용함
    - JPA의 `PESSIMISTIC_WRITE`로 비관적 쓰기 잠금을 적용함
    - 먼저 잠금을 획득한 요청이 끝날 때까지 다른 등록 요청은 대기하도록 처리함
    - 견적이 달라도 같은 생산 여유를 동시에 예약하지 못하도록 구성함

    ```java
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from CommonCode c
            where c.codeGroup.groupCode = 'ORDER_STATUS'
              and c.code = 'WAITING'
              and c.status = :status
            """)
    Optional<CommonCode> findOrderWaitingForUpdate(@Param("status") CodeStatus status);
    ```

    - 위 조회는 수주 대기 상태를 변경하는 작업이 아님
    - 모든 등록 요청을 같은 기준으로 대기시키는 역할을 수행함
2. 잠금 획득 후 중복 확인·일정 계산·저장 순서로 처리함
    - 공통 잠금 다음에 등록 대상 견적도 잠금 처리함
    - 같은 견적으로 이미 등록된 수주가 있으면 중복 등록을 거절함
    - 기존 예약을 반영하여 납기를 계산하고, 가능한 경우에만 수주를 저장함
    - 잠금은 계산 직후가 아니라 저장을 포함한 트랜잭션 종료 시 해제됨
    - 아래는 현재 등록 처리의 핵심 부분을 축약한 코드임

    ```java
    // 공통 잠금으로 서로 다른 견적의 수주 등록도 순서대로 처리함
    CommonCode waiting = commonCodeRepository.findOrderWaitingForUpdate(CodeStatus.ACTIVE)
            .orElseThrow(() -> new IllegalStateException("수주 대기 상태 코드가 없습니다."));

    // 대상 견적을 잠근 뒤 중복 등록 여부를 다시 확인함
    Quotation quotation = quotationRepository.findByIdForUpdate(request.quotationId())
            .orElseThrow(() -> new NoSuchElementException("견적이 없습니다."));

    orderRepository.findByQuotationId(quotation.getId()).ifPresent(existing -> {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 수주가 있습니다.");
    });

    // 앞선 수주의 예약 물량을 반영하여 납기를 검증함
    QuotationScheduleResult schedule =
            scheduleService.calculate(totalQuantity, request.dueDate(), today);

    // 계산 결과로 수주와 품목을 저장함. 트랜잭션 종료 시 잠금이 해제됨
    ```

3. `READ_COMMITTED`로 최신 확정 데이터를 조회하도록 변경함
    - 격리 수준은 다른 요청이 변경한 데이터를 언제 읽을지 정하는 규칙임
    - `READ_COMMITTED`는 일반 조회 시점마다 저장이 확정된 데이터를 읽는 방식임
    - 잠금 대기 후 앞선 주문을 포함하여 생산 여유를 다시 계산하도록 적용함

    ```java
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CustomerOrderDto.CreateResponse create(
            @NotNull @Valid CustomerOrderDto.CreateRequest request, Long userId) {
        // 공통 잠금 → 견적 잠금 → 중복 확인 → 일정 계산 → 저장
    }
    ```

    - 먼저 처리된 주문이 저장되면 뒤의 요청은 줄어든 생산 여유를 기준으로 판단함
    - 납기 내 생산이 불가능하면 등록을 거절하고 가능한 납기를 안내함
    - 납기를 임의로 변경하여 저장하지 않도록 처리함
4. 동시 등록 검증 조건 정리함
    - 같은 견적의 등록 요청 두 건을 동시에 실행하도록 구성함
    - 한 건만 등록되고 나머지는 중복 등록으로 거절되는지 검증함
    - 서로 다른 주문이 같은 납기의 생산 여유를 경쟁하도록 구성함
    - 먼저 처리된 주문만 등록되고, 뒤의 요청은 생산 여유 부족으로 거절되는지 검증함
    - 이번 문서 수정에서는 기존 검증 코드를 확인했으며 테스트를 새로 실행하지는 않음

---

## 4. 배운점

- 개별 요청이 정상이어도 동시 실행에서는 같은 생산 여유를 중복 사용할 수 있음을 배움
- 트랜잭션과 잠금은 역할이 다르며, 트랜잭션만으로 동시성 문제가 해결되지는 않음을 이해함
- 잠금 대상은 견적 한 건뿐 아니라 여러 주문이 공유하는 생산 여유까지 고려해야 함을 배움
- 잠금은 생산 여유 조회 전부터 수주 저장이 끝날 때까지 유지해야 함을 배움
- 대기 후 최신 예약을 반영하려면 잠금과 격리 수준을 함께 검토해야 함을 배움
- 공통 잠금은 중복 예약을 막지만, 요청이 몰리면 대기 시간이 늘어날 수 있음을 이해함

---

## 5. 참고자료

- [수주 등록 처리 코드](../../src/main/java/net/likelion/bebc25/prodflow/customer_order/service/CustomerOrderService.java): 잠금 순서·납기 검증·격리 수준
- [공통 데이터 잠금 코드](../../src/main/java/net/likelion/bebc25/prodflow/commoncode/repository/CommonCodeRepository.java): 수주 대기 상태의 비관적 쓰기 잠금
- [견적 잠금 코드](../../src/main/java/net/likelion/bebc25/prodflow/quotation/repository/QuotationRepository.java): 등록 대상 견적의 쓰기 잠금
- [생산 일정 계산 코드](../../src/main/java/net/likelion/bebc25/prodflow/quotation/schedule/QuotationScheduleService.java): 기존 예약 반영 및 납기 검증
- [동시 수주 등록 검증 코드](../../src/test/java/net/likelion/bebc25/prodflow/quotation/service/QuotationOrderIntegrationTest.java): 중복 등록·생산 용량 중복 예약 방지 검증
