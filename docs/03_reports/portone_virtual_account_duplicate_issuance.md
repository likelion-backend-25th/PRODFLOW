# PortOne API 응답 유실로 인한 가상계좌 중복 발급 문제

## 목차

- [1. 문제 발생](#1-문제-발생)
- [2. 원인 분석](#2-원인-분석)
- [3. 해결 방법](#3-해결-방법)
- [4. 배운점](#4-배운점)
- [5. 참고자료](#5-참고자료)

## 1. 문제 발생

- 문제 유형
    - 외부 api 연동
- 현상 설명
    - portone 가상계좌 발급 과정에서 네트워크 오류가 발생시 동일한 결제에 대해 중복 발급 요청이 발생

---

## 2. 원인 분석

- 처음에는 API 요청이 실패하면 다시 호출하면 된다고 생각했지만 응답을 받지 못했다고 해서 실제 계좌 발급까지 실패한 것은 아니었고 또 @Transaction을 사용하더라도 DB 작업만 롤백할 수 있고,
  portone에서 이미 처리된 작업은 되돌릴 수 없다는 점이 문제였다

오류 상황)

  ```
  @Transactional
  public void issuePayment(Long paymentId) {

  Payment payment = paymentRepository.findById(paymentId)
  .orElseThrow();

  // PortOne에 가상계좌 발급 요청
  portOneClient.issueInstantPayment(
  buildIssueRequest(payment)
  );

  // 발급 완료 상태 저장
  payment.markWaiting();
  }
  ```

---

## 3. 해결 방법

- 가상계좌 발급 전에 결제 상태를 `READY`에서 `REQUESTED` 로 변경

```
READY
  ↓
REQUESTED
  ↓
WAITING
  ↓
PAID
```

- DB 락을 사용해 같은 결제 건에 대해 여러 요청이 동시에 발급되지 않도록함

```
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Payment p WHERE p.id = :paymentId")
Optional<Payment> findByIdForUpdate(
        @Param("paymentId") Long paymentId
);
```

- 응답 유실 상황에서는 실제 발급 여부를 알 수 없기 때문에, 새로운 발급 요청을 보내는 대신 기존 PortOne 결제 ID로 상태를 먼저 조회하도록 변경

```
public void issuePayment(Long paymentId) {

    boolean firstRequest = paymentStore.claimIssue(paymentId);

    // 이미 발급 요청한 결제라면 재발급하지 않음
    if (!firstRequest) {
        synchronizePayment(paymentId);
        return;
    }

    try {
        // 최초 요청인 경우에만 발급 API 호출
        portOneClient.issueInstantPayment(
                buildIssueRequest(paymentId)
        );

    } catch (Exception e) {
        // 응답 유실 가능성이 있으므로 즉시 재발급하지 않음
    }

    try {
        // PortOne에서 실제 발급 여부 확인
        synchronizePayment(paymentId);

    } catch (Exception e) {
        // 조회까지 실패한 경우 불확실 상태로 저장
        paymentStore.markIssueUncertain(paymentId);
    }
}
```

- 조회까지 실패하면 `ISSUE_UNCERTAIN` 상태로 저장하고, 나중에 상태를 확인할 수 있도록함

---

## 4. 배운점

이번 경험을 통해 @Transaction만으로 모든 문제를 해결할 수 없고, 외부 결제 API와 연동할 때는 정상적인 요청뿐만 아니라 응답 유실, 중복 요청, 네트워크 장애까지 고려해야 한다는 점을 배웠다.

## 5. 참고자료

- 없음