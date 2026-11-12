# ProdFlow REST API 명세서

공통 접두 URI는`/api/v1`이다. 외부 공개 회원가입은 없다. 권한은`SUPER_ADMIN`,`ORDER_MANAGER`,`PRODUCTION_MANAGER`,`INQUIRY_MANAGER`이다.

## 1. 공통 응답

성공 시 래퍼 없이 DTO 단건, 목록, 페이징 객체를 그대로 반환한다. 등록일`createdAt`, 수정일`updatedAt`은 테이블에 컬럼이 있는 응답에 포함한다.

단건 예:

```json
{
  "id": 1,
  "modelCode": "HK-GS23",
  "productName": "헬로키티케이스",
  "deviceId": 3,
  "deviceName": "갤럭시 S23",
  "productDescription": "갤럭시 S23용 인쇄 케이스",
  "currentPrice": 12000,
  "status": "생산가능",
  "createdAt": "2026-10-24T09:00:00",
  "updatedAt": "2026-10-24T09:00:00"
}
```

페이징 예:

```json
{
  "content": [],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 42,
  "totalPages": 5,
  "last": false
}
```

실패 예:

```json
{
  "timestamp": "2026-10-24T09:30:00",
  "status": 409,
  "error": "CONFLICT",
  "code": "DUPLICATE_MODEL_CODE",
  "message": "이미 등록된 모델코드입니다."
}
```

## **2. 인증 및 사용자**

### **2.1 로그인**

- Method:`POST`
- URI:`/api/v1/auth/login`
- Path Parameters: 없음
- 인증 필요 여부: 불필요
- 처리 규칙
  - 이메일과 비밀번호로 로그인한다. 성공 시 Access Token을 응답 Body로 반환하고 Refresh Token은 HttpOnly 쿠키로 발급한다.
  - 로그인 성공 시`last_login_at`을 갱신하며, 기존 Refresh Token이 있으면 새 토큰으로 교체한다.
  - 이메일 또는 비밀번호가 올바르지 않으면`401 Unauthorized`.
  - `INACTIVE`,`DELETED`계정은 로그인할 수 없으며`403 Forbidden`을 반환한다.
  - 로그인 성공 시 Refresh Token 해시를 DB에 저장한다.
  - Refresh Token 원문은 응답 Body에 포함하지 않는다.
  - 최초 비밀번호 변경이 필요하면`mustChangePassword=true`를 반환한다.
  - 허용되지 않은 Origin의 요청은`403 Forbidden`.
- Request Body: 필수

```json
{
  "email": "admin@prodflow.local",
  "password": "Temp1234!"
}
```

| **필드**   | **타입** | **필수** | **설명**             |
|----------|--------|--------|--------------------|
| email    | String | O      | 로그인 이메일. 이메일 형식 검증 |
| password | String | O      | 로그인 비밀번호           |

- Response:`200 OK`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
  "tokenType": "Bearer",
  "userId": 1,
  "name": "관리자",
  "email": "admin@prodflow.local",
  "role": "SUPER_ADMIN",
  "mustChangePassword": true
}
```

**응답 Header**

```
Set-Cookie: refreshToken=<JWT>; HttpOnly; Path=/api/v1/auth; SameSite=Lax
```

쿠키의`Secure`,`SameSite`속성은 서버 설정에 따라 달라진다. 쿠키 유효기간은 Refresh Token 만료 설정을 기준으로 지정한다.

### **2.2 로그아웃**

- Method:`POST`
- URI:`/api/v1/auth/logout`
- Path Parameters: 없음
- 인증 필요 여부: 불필요
  - 인증 방식: Refresh Token 쿠키
- 처리 규칙
  - 요청 쿠키의 Refresh Token을 서버에서 무효화하고 브라우저의 쿠키를 삭제한다.
  - 유효한 Refresh Token과 일치하는 DB 저장 토큰이 있으면 해당 레코드를 삭제한다.
  - Refresh Token이 없거나 유효하지 않아도 정상 로그아웃으로 처리한다.
  - 로그아웃은 여러 번 호출해도 정상 처리되는 멱등 방식이다.
  - 성공 시`204 No Content`.
  - 허용되지 않은 Origin의 요청은`403 Forbidden`.
- Request Body: 없음

요청 Body는 사용하지 않는다.

- Response:`204 No Content`

### **2.3 토큰 재발급**

- Method:`POST`
- URI:`/api/v1/auth/refresh`
- Path Parameters: 없음
- 인증 필요 여부: 불필요
  - 인증 방식: Refresh Token 쿠키
- 처리 규칙
  - 기존 Refresh Token을 검증하고 Access Token 및 Refresh Token을 새로 발급한다.
  - Refresh Token이 없거나 유효하지 않으면`401 Unauthorized`.
  - Refresh Token이 만료되면 DB 토큰을 삭제하고`401 Unauthorized`.
  - DB에 저장된 토큰 해시와 일치하지 않으면 해당 세션을 폐기하고`401 Unauthorized`.
  - 사용자가 존재하지 않으면`401 Unauthorized`.
  - `INACTIVE`,`DELETED`계정은`403 Forbidden`.
  - 재발급 성공 시 DB의`token_hash`,`expires_at`을 갱신한다.
  - 응답 Body에`mustChangePassword`값을 포함한다.
  - 허용되지 않은 Origin의 요청은`403 Forbidden`.
- Request Body: 없음
- Response:`200 OK`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsIn...newAccess",
  "tokenType": "Bearer",
  "mustChangePassword": false
}
```

### **2.4 비밀번호 변경**

- Method:`POST`
- URI:`/api/v1/auth/change-password`
- Path Parameters: 없음
- 인증 필요 여부: 필수
  - 권한: 로그인 사용자
- 처리 규칙
  - 현재 비밀번호를 검증한 뒤 새로운 비밀번호로 변경한다.
  - 최초 임시 비밀번호 변경에 사용할 수 있으며, 현재 구현은`mustChangePassword=true`인 사용자만으로 요청을 제한하지 않는다.
  - 현재 비밀번호가 일치하지 않으면`400 Bad Request`.
  - 새 비밀번호와 확인 비밀번호가 다르면`400 Bad Request`.
  - 새 비밀번호가 기존 비밀번호와 같으면`400 Bad Request`.
  - 새 비밀번호는 8자 이상 20자 이하여야 한다.
  - 비밀번호는 BCrypt로 암호화하여 저장한다.
  - 성공 시`must_change_password=false`로 변경한다.
  - DB에 저장된 해당 사용자의 Refresh Token을 삭제한다.
  - 응답에서 Refresh Token 쿠키를 삭제한다.
  - 변경 성공 후 다시 로그인해야 한다.
  - 기존 Access Token은 만료 전까지 유효할 수 있다.
- Request Headers

```
Authorization: Bearer <accessToken>Content-Type: application/json
```

- Request Body: 필수

```json
{
  "temporaryPassword": "Abcdef1234!Z",
  "newPassword": "Prodflow123!",
  "confirmPassword": "Prodflow123!"
}
```

| **필드**            | **타입** | **필수** | **설명**        |
|-------------------|--------|--------|---------------|
| temporaryPassword | String | O      | 현재 사용 중인 비밀번호 |
| newPassword       | String | O      | 새 비밀번호. 8~20자 |
| confirmPassword   | String | O      | 새 비밀번호 확인     |

- Response:`204 No Content`

### **2.5 사용자 추가**

- Method:`POST`
- URI:`/api/v1/users`
- Path Parameters: 없음
- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`
- 처리 규칙
  - 최고 관리자가 신규 직원을 등록한다. 임시 비밀번호는 프론트엔드에서 생성하여 서버로 전달한다.
  - 이메일은 중복 등록할 수 없다.
  - 임시 비밀번호는 정확히 12자여야 한다.
  - 영문 대문자, 소문자, 숫자 및 허용 특수문자를 각각 1개 이상 포함해야 한다.
  - 허용 특수문자는`! @ # $ % & * ?`이다.
  - 서버는 전달받은 임시 비밀번호를 암호화하여 저장한다.
  - 신규 계정 초기 상태는`ACTIVE`.
  - 신규 계정의`mustChangePassword`는`true`.
  - 임시 비밀번호 원문은 응답에 포함되지 않는다.
  - 이메일 중복이나 임시 비밀번호 규칙 위반은`400 Bad Request`.
- Request Body: 필수

```json
{
  "name": "김수주",
  "email": "order@prodflow.local",
  "phone": "010-1111-2222",
  "role": "ORDER_MANAGER",
  "temporaryPassword": "Abcdef1234!Z"
}
```

| **필드**            | **타입**   | **필수** | **설명**              |
|-------------------|----------|--------|---------------------|
| name              | String   | O      | 직원 이름               |
| email             | String   | O      | 직원 이메일              |
| phone             | String   | O      | 직원 연락처              |
| role              | UserRole | O      | 사용자 권한              |
| temporaryPassword | String   | O      | 프론트엔드에서 생성한 임시 비밀번호 |

사용 가능한 권한은`SUPER_ADMIN`,`ORDER_MANAGER`,`PRODUCTION_MANAGER`,`INQUIRY_MANAGER`이다.

- Response:`201 Created`

```json
{
  "id": 2,
  "name": "김수주",
  "email": "order@prodflow.local",
  "phone": "010-1111-2222",
  "role": "ORDER_MANAGER",
  "status": "ACTIVE",
  "lastLoginAt": null,
  "createdAt": "2026-11-11T10:00:00",
  "updatedAt": "2026-11-11T10:00:00",
  "deletedAt": null,
  "mustChangePassword": true
}
```

### **2.6 사용자 목록 및 검색**

- Method:`GET`
- URI:`/api/v1/users`
- Path Parameters: 없음
- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`
- 처리 규칙
  - 직원 목록 조회, 이름·이메일·전화번호 검색, 계정 상태 필터 및 페이징을 제공한다.
  - `DELETED`계정은 항상 조회 결과에서 제외한다.
  - `keyword`는 앞뒤 공백 제거 후 최대 100자까지 허용한다.
  - 검색어는 소문자로 변환하여 조회한다.
  - `status`미지정 시`ACTIVE`,`INACTIVE`모두 조회한다.
  - `status=DELETED`는`400 Bad Request`.
  - `status=ALL`또는 잘못된 Enum 값은`400 Bad Request`.
  - 검색, 필터, 페이징은 DB 조회 단계에서 적용한다.
  - 결과가 없으면`content=[]`,`totalElements=0`,`totalPages=0`.
- Query Parameters

| **파라미터** | **타입**     | **필수** | **기본값**   | **설명**               |
|----------|------------|--------|-----------|----------------------|
| keyword  | String     | X      | 없음        | 이름, 이메일, 전화번호 검색     |
| status   | UserStatus | X      | 전체        | `ACTIVE`, `INACTIVE` |
| page     | Integer    | X      | 0         | 페이지 번호               |
| size     | Integer    | X      | 10        | 페이지 크기               |
| sort     | String     | X      | `id,desc` | 정렬 기준 및 방향           |

- Request Body: 없음
- 요청 예시
  - 전체 조회:

    ```
    GET /api/v1/users?page=0&size=10
    ```

  - 검색:

    ```
    GET /api/v1/users?keyword=김수주&page=0&size=10
    ```

  - 활성 계정:

    ```
    GET /api/v1/users?status=ACTIVE&page=0&size=10
    ```

  - 비활성 계정:

    ```
    GET /api/v1/users?status=INACTIVE&page=0&size=10
    ```

  - 검색 및 필터:

    ```
    GET /api/v1/users?keyword=김&status=ACTIVE&page=0&size=10
    ```

- Response:`200 OK`

```json
{
  "content": [
    {
      "id": 2,
      "name": "김수주",
      "email": "order@prodflow.local",
      "phone": "010-1111-2222",
      "role": "ORDER_MANAGER",
      "status": "ACTIVE",
      "lastLoginAt": "2026-11-05T09:30:00",
      "createdAt": "2026-10-24T10:00:00",
      "updatedAt": "2026-11-01T14:00:00",
      "deletedAt": null,
      "mustChangePassword": false
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

- 응답 필드

| **필드**        | **타입**  | **설명**            |
|---------------|---------|-------------------|
| content       | Array   | 직원 정보 목록          |
| page          | Integer | 현재 페이지 번호         |
| size          | Integer | 페이지 크기            |
| totalElements | Long    | 검색 및 필터 적용 후 직원 수 |
| totalPages    | Integer | 전체 페이지 수          |

### **2.7 사용자 상세 조회**

- Method:`GET`
- URI:`/api/v1/users/{id}`
- Path Parameters

| **파라미터** | **타입** | **필수** | **설명**    |
|----------|--------|--------|-----------|
| id       | Long   | O      | 조회할 직원 ID |

- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`
- 처리 규칙
  - 직원 ID를 기준으로 직원의 상세 정보를 조회한다.
  - 존재하는 직원의 상세 정보를 반환한다.
  - 존재하지 않는 직원 ID는 현재 구현에서`IllegalArgumentException`이 발생하므로 **`400 Bad Request`*를 반환한다.
  - 현재 서비스에는 삭제 계정 조회 차단 로직이 없다.
  - 따라서`DELETED`계정도 ID로 조회하면 반환될 수 있다.
- Request Body: 없음
- Response:`200 OK`

```json
{
  "id": 2,
  "name": "김수주",
  "email": "order@prodflow.local",
  "phone": "010-1111-2222",
  "role": "ORDER_MANAGER",
  "status": "ACTIVE",
  "lastLoginAt": "2026-11-05T09:30:00",
  "createdAt": "2026-10-24T10:00:00",
  "updatedAt": "2026-11-01T14:00:00",
  "deletedAt": null,
  "mustChangePassword": false
}
```

### **2.8 사용자 수정**

- Method:`PUT`
- URI:`/api/v1/users/{id}`
- Path Parameters

| **파라미터** | **타입** | **필수** | **설명**    |
|----------|--------|--------|-----------|
| id       | Long   | O      | 수정할 직원 ID |

- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`
- 처리 규칙
  - 직원의 이름, 이메일, 연락처, 권한, 계정 상태를 수정한다. 필요하면 임시 비밀번호를 재발급할 수 있다.
  - `status`는`ACTIVE`,`INACTIVE`만 허용한다.
  - `DELETED`상태로 수정하려는 요청은`400 Bad Request`.
  - 이미 삭제된 직원은 수정할 수 없으며`400 Bad Request`.
  - 존재하지 않는 직원 ID는`400 Bad Request`.
  - 이메일 중복 시`400 Bad Request`.
  - 임시 비밀번호를 전달하면 12자리 규칙을 검증한다.
  - 임시 비밀번호 재발급 시`mustChangePassword=true`로 변경한다.
  - 이메일, 역할, 상태 또는 임시 비밀번호가 변경되면 Refresh Token을 폐기한다.
  - 이름이나 연락처만 수정하면 Refresh Token은 유지한다.
- Request Body: 필수

```json
{
  "name": "김수주",
  "email": "order@prodflow.local",
  "phone": "010-1111-3333",
  "role": "ORDER_MANAGER",
  "status": "ACTIVE",
  "temporaryPassword": null
}
```

| **필드**            | **타입**     | **필수** | **설명**                              |
|-------------------|------------|--------|-------------------------------------|
| name              | String     | O      | 수정할 이름                              |
| email             | String     | O      | 수정할 이메일                             |
| phone             | String     | O      | 수정할 연락처                             |
| role              | UserRole   | O      | 수정할 권한                              |
| status            | UserStatus | O      | `ACTIVE`, `INACTIVE`                |
| temporaryPassword | String     | X      | 재발급할 임시 비밀번호. 미입력 또는 NULL이면 변경하지 않음 |

- 임시 비밀번호 재발급 요청 예시

```json
{
  "name": "김수주",
  "email": "order@prodflow.local",
  "phone": "010-1111-3333",
  "role": "ORDER_MANAGER",
  "status": "ACTIVE",
  "temporaryPassword": "Abcdef1234!Z"
}
```

- Response:`200 OK`

```json
{
  "id": 2,
  "name": "김수주",
  "email": "order@prodflow.local",
  "phone": "010-1111-3333",
  "role": "ORDER_MANAGER",
  "status": "ACTIVE",
  "lastLoginAt": "2026-11-05T09:30:00",
  "createdAt": "2026-10-24T10:00:00",
  "updatedAt": "2026-11-11T11:00:00",
  "deletedAt": null,
  "mustChangePassword": false
}
```

### **2.9 사용자 삭제**

- Method:`DELETE`
- URI:`/api/v1/users/{id}`
- Path Parameters

| **파라미터** | **타입** | **필수** | **설명**    |
|----------|--------|--------|-----------|
| id       | Long   | O      | 삭제할 직원 ID |

- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`
- 처리 규칙
  - 직원 계정을 물리적으로 삭제하지 않고 소프트 삭제한다.
  - 계정 데이터를 물리적으로 삭제하지 않는다.
  - `status=DELETED`로 변경한다.
  - `deleted_at`에 현재 일시를 기록한다.
  - 해당 직원의 Refresh Token을 삭제한다.
  - 삭제된 계정은 직원 목록과 통계에서 제외한다.
  - 이미 삭제된 계정의 재삭제는`400 Bad Request`.
  - 존재하지 않는 직원 ID는`400 Bad Request`.
- Request Body: 없음
- Response:`200 OK`

```json
{
  "id": 2,
  "name": "김수주",
  "email": "order@prodflow.local",
  "phone": "010-1111-2222",
  "role": "ORDER_MANAGER",
  "status": "DELETED",
  "lastLoginAt": "2026-11-05T09:30:00",
  "createdAt": "2026-10-24T10:00:00",
  "updatedAt": "2026-11-11T12:00:00",
  "deletedAt": "2026-11-11T12:00:00",
  "mustChangePassword": false
}
```

### **2.10 인증 및 인가**

Spring Security와 JWT를 사용하여 API 접근 권한을 관리한다.

- 권한별 API 접근 범위

| **API**                             | **권한**                        |
|-------------------------------------|-------------------------------|
| `POST /api/v1/auth/login`           | 비로그인 허용                       |
| `POST /api/v1/auth/logout`          | 비로그인 허용, Refresh Token 쿠키 처리  |
| `POST /api/v1/auth/refresh`         | 비로그인 허용, 유효한 Refresh Token 필요 |
| `POST /api/v1/auth/change-password` | 로그인 사용자                       |
| `GET /api/v1/users/me`              | 로그인 사용자                       |
| `POST /api/v1/users`                | SUPER_ADMIN                   |
| `GET /api/v1/users`                 | SUPER_ADMIN                   |
| `GET /api/v1/users/{id}`            | SUPER_ADMIN                   |
| `PUT /api/v1/users/{id}`            | SUPER_ADMIN                   |
| `DELETE /api/v1/users/{id}`         | SUPER_ADMIN                   |
| `GET /api/v1/users/stats`           | SUPER_ADMIN                   |
| `GET /api/v1/users/order`           | SUPER_ADMIN                   |

- 최초 비밀번호 변경이 필요한 계정

`mustChangePassword=true`인 사용자가 유효한 Access Token으로 요청하면 JWT 인증 필터에서 일반 업무 API 접근을 차단한다.

허용되는 인증된 API는 다음 두 가지다.

- `POST /api/v1/auth/change-password`
- `GET /api/v1/users/me`

로그인, 토큰 재발급, 로그아웃은 별도로 인증 없이 접근하도록 설정되어 있다.

**비밀번호 변경 전 일반 API 접근 시 응답 —`403 Forbidden`**

```json
{
  "code": "PASSWORD_CHANGE_REQUIRED",
  "message": "비밀번호 변경 후 이용할 수 있습니다."
}
```

- 인증 및 인가 오류

| **상황**                          | **HTTP 상태**      |
|---------------------------------|------------------|
| Access Token이 필요한 API에 토큰 없이 접근 | 401 Unauthorized |
| Access Token이 유효하지 않음           | 401 Unauthorized |
| JWT 필터에서 비활성 계정으로 판단            | 401 Unauthorized |
| 인증은 되었지만 접근 권한 부족               | 403 Forbidden    |
| 최초 비밀번호 변경 전 일반 API 접근          | 403 Forbidden    |
| 로그인 이메일 또는 비밀번호 오류              | 401 Unauthorized |
| 로그인 시 계정 상태가 ACTIVE가 아님         | 403 Forbidden    |
| 허용되지 않은 인증 API Origin           | 403 Forbidden    |

- JWT 필터의 인증 실패 응답

```json
{
  "code": "UNAUTHORIZED_ACCESS",
  "message": "유효한 로그인이 필요합니다."
}
```

- 공통 보안 규칙
  - Access Token은`Authorization: Bearer <accessToken>`형식으로 전달한다.
  - Refresh Token은`refreshToken`HttpOnly 쿠키로 전달한다.
  - 서버 세션은 사용하지 않으며 Stateless 인증을 적용한다.
  - 인증 API에서는 요청 Origin을 검증한다.
  - CORS는 서버에 설정된 허용 Origin만 허용한다.
  - 쿠키를 이용한 교차 출처 요청에 대해 Credentials 사용을 허용한다.

### **2.11 직원 계정 통계 조회**

- Method:`GET`
- URI:`/api/v1/users/stats`
- Path Parameters: 없음
- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`
- 처리 규칙
  - 계정관리 페이지 상단에 표시할 전체 계정 수, 활성 계정 수, 비활성 계정 수를 조회한다.
  - `ACTIVE`직원만 활성 계정 수에 포함한다.
  - `INACTIVE`직원만 비활성 계정 수에 포함한다.
  - `DELETED`직원은 집계하지 않는다.
  - `totalAccounts = activeAccounts + inactiveAccounts`.
  - 검색, 상태 필터 및 페이징 조건과 무관하게 전체 직원 기준으로 집계한다.
- Request Body: 없음
- Response:`200 OK`

```json
{
  "totalAccounts": 15,
  "activeAccounts": 12,
  "inactiveAccounts": 3
}
```

- 응답 필드

| **필드**           | **타입** | **설명**   |
|------------------|--------|----------|
| totalAccounts    | Long   | 전체 직원 수  |
| activeAccounts   | Long   | 활성 직원 수  |
| inactiveAccounts | Long   | 비활성 직원 수 |

### **2.12 내 정보 조회**

- Method:`GET`
- URI:`/api/v1/users/me`
- Path Parameters: 없음
- 인증 필요 여부: 필수
  - 권한: 로그인 사용자
- 처리 규칙
  - JWT 인증 정보를 이용하여 현재 로그인한 직원의 정보를 조회한다.
  - JWT에서 인증된 사용자 ID를 사용한다.
  - 다른 직원의 ID를 지정하여 조회할 수 없다.
  - `mustChangePassword=true`여도 내 정보 조회는 가능하다.
  - 토큰이 없거나 유효하지 않으면`401 Unauthorized`.
  - JWT 필터에서 사용자가 비활성 상태로 판단되면`401 Unauthorized`.
  - 서비스에 도달했지만 사용자 상태가 ACTIVE가 아니라면`403 Forbidden`.
  - 비밀번호 및 Refresh Token은 응답에 포함하지 않는다.
- Request Body: 없음
- Response:`200 OK`

```json
{
  "userId": 2,
  "name": "김수주",
  "phone": "010-1111-2222",
  "email": "order@prodflow.local",
  "role": "ORDER_MANAGER",
  "status": "ACTIVE",
  "mustChangePassword": false
}
```

- 응답 필드

| **필드**             | **타입**     | **설명**           |
|--------------------|------------|------------------|
| userId             | Long       | 로그인한 직원 ID       |
| name               | String     | 직원 이름            |
| phone              | String     | 직원 연락처           |
| email              | String     | 직원 이메일           |
| role               | UserRole   | 직원 권한            |
| status             | UserStatus | 계정 상태            |
| mustChangePassword | Boolean    | 최초 비밀번호 변경 필요 여부 |

### **2.13 수주관리자 목록 조회**

- Method:`GET`
- URI:`/api/v1/users/order`
- Path Parameters: 없음
- 인증 필요 여부: 필수
- 처리 규칙
  - `ORDER_MANAGER`권한을 가진 사용자 목록을 조회한다.
  - 결과가 없으면 빈 배열을 반환한다.
- Request Body: 없음
- Response:`200 OK` , `UserDto.Response`

```json
[
  {
    "id": 2,
    "name": "김수주",
    "email": "order@prodflow.local",
    "phone": "010-1111-2222",
    "role": "ORDER_MANAGER",
    "status": "ACTIVE",
    "lastLoginAt": "2026-11-05T09:30:00",
    "createdAt": "2026-10-24T10:00:00",
    "updatedAt": "2026-11-01T14:00:00",
    "deletedAt": null,
    "mustChangePassword": false
  }
]
```

- id: 사용자 ID, Long
- email: 사용자 이메일, String
- phone: 사용자 연락처, String
- role: 사용자 권한, String
- status: 사용자 상태, String
- lastLoginAt: 최근 로그인 시각, LocalDateTime
- createdAt: 생성 시각, LocalDateTime
- updatedAt: 수정 시각, LocalDateTime
- deleteAt: 삭제 시각, LocalDateTime
- mustChangePassword: 초기비밀번호 변경 여부, boolean

### **2.14 공통 오류 응답 및 상태코드**

현재`GlobalExceptionHandler`는 예외 종류에 따라 응답 형식을 구분한다.

- AuthException

인증 서비스에서 발생하는 업무 예외는 다음 구조로 반환한다.

```json
{
  "code": "ERROR_CODE",
  "message": "오류 메시지"
}
```

상태코드는 예외에 지정된 HTTP 상태를 사용한다.

- 일반 입력 및 업무 예외

| **예외 종류**                           | **HTTP 상태** | **처리**                     |
|-------------------------------------|-------------|----------------------------|
| MethodArgumentNotValidException     | 400         | 요청 Body 유효성 검증 실패          |
| MethodArgumentTypeMismatchException | 400         | 파라미터 타입 또는 Enum 값 오류       |
| ConstraintViolationException        | 400         | 서비스 입력 검증 실패               |
| IllegalArgumentException            | 400         | 업무 규칙 위반, 존재하지 않는 직원 ID 등  |
| IllegalStateException               | 400         | 잘못된 객체 상태, 삭제된 계정 수정·재삭제 등 |
| AuthenticationException             | 401         | 인증 실패                      |
| AccessDeniedException               | 403         | 접근 권한 부족                   |
| AuthorizationDeniedException        | 403         | 인가 거부                      |
| NoSuchElementException              | 404         | 리소스를 찾을 수 없음               |
| Exception                           | 500         | 처리되지 않은 서버 내부 오류           |

이러한 일반 예외는`ApiErrorResponse`를 사용한다. 단,`AuthException`과 JWT 필터가 직접 반환하는 오류는 별도로 정의된 JSON 구조를 사용한다.

**주의:**현재 사용자 서비스에서 존재하지 않는 직원 ID에`IllegalArgumentException`을 사용하므로, 직원 상세 조회·수정·삭제는 해당 상황에서 404가 아닌 400을 반환한다.

## 3. 거래처 (`clients` )

### 3.1 거래처 목록, 검색

- Method: `GET`
- URI: `/api/v1/clients`
- 인증 필요 여부: 불필요
- Query Parameters
  - keyword: 검색어
  - page: 페이지 커서 (default: 0),
  - size: 페이지 크기 (default: 8)
  - sort: 정렬 컬럼,정렬순서 (예: createdAt,asc)
- 처리 규칙
  - 거래처 목록을 한 페이지당 8개씩 반환한다.
  - 검색어가 존재할 경우 회사명 or 대표자명 or 연락처명에 해당 하는 검색어가 존재하는 레코드들을 반환한다.
  - 검색어가 존재하지 않을 경우 전체 레코드들을 반환한다.
  - 정렬기준은 기본값으로는 등록일 기준 최신순으로 정렬, 프론트에서 정렬기준 Query Param을 받아와 해당 기준대로 정렬한다.
- Response: `200 OK`, `Page<ClientDto.Response>`

    ```json
    {
    	"content": [
    		{
    			"id": 1,
    			"company": "한결산업",
    		  "employee": "김도윤",
    		  "phone": "010-1234-5678",
    		  "email": "doyun",
    		  "total_order": 6,
    		  "total_amount": 18920000
    		  "recent_ordered_at": "2026-08-12T21:30:00"
    		},
    		{
    			"id": 2,
    			...
    		}
    	],
    	"empty": false,
    	...
    }
    ```

  - content: Page 클래스에서 구조체 리스트가 들어가는 부분
    - 여기서는 ClientDto.Response 리스트가 들어감
  - id: 거래처 ID, Long
  - company: 거래처 회사 이름, String
  - employee: 거래처 대표자 이름, String
  - phone: 거래처 이메일, String
  - email: 거래처 연락처, String
  - total_order: 누적 수주량, int
  - total_amount: 누적 거래액, Long
  - recent_ordered_at: 최근 수주일, LocalDateTime
  - 그 뒤로는 Page 클래스 속성들이 나열

### 3.2 거래처 상세 조회

- Method: `GET`
- URI: `/api/v1/clients/{id}`
- Path Parameters
  - id: 거래처 ID, 필수, Long
- 인증 필요 여부: 불필요
- 처리 규칙
  - 해당 거래처 id의 상세 페이지를 반환한다.
- Response: `200 OK`, `ClientDto.DetailResponse`

    ```json
    {
    	"id": "1",
      "company": "한결산업",
      "employee": "김도윤",
      "phone": "010-1234-5678",
      "email": "doyun",
      "address": "서울특별시 금천구 가산산업로 24, 3층"
      "user_name": "홍길동",
      "user_phone": "010-5678-1234",
      "total_order": 6,
      "total_amount": 18920000
      "ongoing_orders": 2
    }
    ```

  - id: 거래처 ID 값
  - company: 거래처 회사 이름, String
  - employee: 거래처 대표자 이름, String
  - phone: 거래처 이메일, String
  - email: 거래처 연락처, String
  - address: 거래처 주소, String
    - Address 객체에 담겨있는 roadAddress와 detailAddress를 순서대로 병합한다.
      - 예: 서울특별시 금천구 가산산업로 24 / 3층 → 서울특별시 금천구 가산산업로 24, 3층
  - user_name: 담당 수주 관리자 이름, String
  - user_phone: 담당 수주 관리자 연락처, String
  - total_order: 누적 수주량, int
  - total_amount: 누적 거래액, Long
  - ongoing_orders: 진행 중인 수주량, int

### 3.3 거래처 등록

- Method: `POST`
- URI: `/api/v1/clients`
- 인증 필요 여부: 필수
  - 권한: `SUPER_ADMIN`, `ORDER_MANAGER`
- 처리 규칙
  - 요청 본문(body)를 기반으로 거래처 정보를 등록한다.
  - 거래처 정보가 성공적으로 등록되면 응답 반환후 해당 거래처 상세페이지로 리다이렉트한다.
- Request Body: 필수, `ClientDto.Request`

    ```json
    {
      "company": "한결산업",
      "employee": "김도윤",
      "phone": "010-1234-5678",
      "email": "doyun",
      "user_id": 2,
      "address": {
    	  "zipcode": "08520",
    	  "roadAddress": "서울특별시 금천구 가산산업로 24",
    	  "detailAddress": "3층"
      }
    }
    ```

  - company: 거래처 회사 이름, String
  - employee: 거래처 대표자 이름, String
  - phone: 거래처 이메일, String
  - email: 거래처 연락처, String
  - user_id: 수주 관리자 ID, Long
  - address
    - zipcode: 우편번호, String
    - roadAddress: 도로명 주소, String
    - detailAddress: 상세 주소, String
- Response: `201 CREATED` (`ClientDto.DetailResponse`)

    ```json
    {
      "company": "한결산업",
      "employee": "김도윤",
      "phone": "010-1234-5678",
      "email": "doyun",
      "address": "서울특별시 금천구 가산산업로 24, 3층"
      "user_name": "홍길동",
      "user_phone": "010-5678-1234",
      "total_order": 6,
      "total_amount": 18920000
      "ongoing_orders": 2
    }
    ```

  - 거래처 상세 조회 Response 참조

### 3.4 거래처 수정

- Method: `PUT`
- URI: `/api/v1/clients/{id}`
- 인증 필요 여부: 필수
  - 권한: `SUPER_ADMIN`, `ORDER_MANAGER`
- 처리 규칙
  - 요청 본문(body)를 기반으로 거래처 정보를 수정한다.
  - 거래처 정보가 성공적으로 등록되면 응답 반환후 해당 거래처 상세페이지로 리다이렉트한다.
- Request Body: 필수, `ClientDto.Request`

    ```json
    {
      "company": "한결산업",
      "employee": "김도윤",
      "phone": "010-1234-5678",
      "email": "doyun",
      "user_id": 1,
      "address": {
    	  "zipcode": "08520",
    	  "roadAddress": "서울특별시 금천구 가산산업로 24",
    	  "detailAddress": "3층"
      }
    }
    ```

  - company: 거래처 회사 이름, String
  - employee: 거래처 대표자 이름, String
  - phone: 거래처 이메일, String
  - email: 거래처 연락처, String
  - user_id: 수주 관리자 ID, Long
  - address
    - zipcode: 우편번호, String
    - roadAddress: 도로명 주소, String
    - detailAddress: 상세 주소, String
- Response: `201 CREATED` (`ClientDto.DetailResponse`)

    ```json
    {
      "company": "한결산업",
      "employee": "김도윤",
      "phone": "010-1234-5678",
      "email": "doyun",
      "address": "서울특별시 금천구 가산산업로 24, 3층"
      "user_name": "홍길동",
      "user_phone": "010-5678-1234",
      "total_order": 6,
      "total_amount": 18920000
      "ongoing_orders": 2
    }
    ```

  - 거래처 상세 조회 Response 참조

`PATCH /api/v1/clients/{clientId}`

권한:`SUPER_ADMIN`,`ORDER_MANAGER`. 회사명, 대표자, 주소, 이메일, 연락처를 수정한다

### 3.5 거래처 엑셀 일괄 등록

- Method: `POST`
- URI: `/api/v1/clients/import`
- 인증 필요 여부: 필수
  - 권한: `SUPER_ADMIN`, `ORDER_MANAGER`
- Request Body
  - 필수
  - Content-Type: `multipart/form-data`
  - File: `.xlsx`
- 처리 규칙
  - 요청을 기반으로 엑셀 파일에 담긴 거래처 정보를 일괄 등록 한다.
  - 거래처 정보들은 올바르게 담겨져 있다고 가정한다.
  - 거래처 정보들이 성공적으로 등록되면 응답 반환후 거래처 목록 페이지로 리다이렉트한다.
- Response: `201 CREATED`, `List<ClientDto.ImportResponse>`

    ```json
    {
      "id": 1,
      "company": "한결산업"
    }
    ```

  - id: 거래처 ID, Long
  - company: 거래처 회사명, String

### 3.6 최근 한달간 등록된 거래처와 거래 요청한 거래처 수 조회

- Method: `GET`
- URI: `/api/v1/clients/counts`
- 인증 필요 여부: 불필요
- 처리 규칙
  - 최근 한달간 등록한 거래처 수와 수주 주문을 한 거래처 수를 조회한다.
- Response: `200 OK`, `ClientDto.DetailResponse`

    ```json
    {
      "count_by_order": 4,
      "count_by_created_at": 10
    }
    ```

  - count_by_order: 최근 한달간 수주 주문을 한 거래처 수
  - count_by_created_at: 최근 한달간 등록한 거래처 수

## **4. 품목**

기종 이름은 품목 컬럼이 아니다.`product.device_code`에는 공통코드`DEVICE`의 id만 저장하고, 응답의 기종 글자는`device.name`이다. 기종 필터를 품목 이름에서 중복 제거해서 만들지
않는다. 기종 목록은`GET /api/v1/common-codes?groupCode=DEVICE`다.

### **4.1 기종 목록**

- Method:`GET`
- URI:`/api/v1/common-codes`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  - 쿼리`groupCode`: 필수. 기종은`DEVICE`
  - `DEVICE`그룹의`common_code`중`status = ACTIVE`만 반환한다
  - `sortOrder`오름차순
  - `groupCode`가 비어 있으면`400 BUSINESS_RULE_VIOLATION`
  - 응답에`status`는 없다
  - 품목 등록 화면의 기종 선택도 이 API를 쓴다
- Response:`200 OK`

```json
[
  {
    "id": 3,
    "code": "DEV1",
    "name": "갤럭시 S23",
    "sortOrder": 1
  }
]
```

### **4.2 품목 등록**

- Method:`POST`
- URI:`/api/v1/products`
- Path Parameters: 없음
- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙
  - `deviceCode`와`deviceName`중 하나만 보낸다
  - 둘 다 있으면`400`, 메시지`기존 기종 ID 또는 새 기종명 중 하나만 입력해주세요.`
  - 둘 다 없으면`400`, 메시지`기존 기종 ID 또는 새 기종명을 입력해주세요.`
  - `deviceCode`는`common_code.id`다. 없으면 현재`500`, 메시지`기종 코드를 찾을 수 없습니다.`
  - `deviceName`은 앞뒤 공백을 제거하고 연속 공백을 하나로 줄인다. 같은 이름(대소문자 무시)의 ACTIVE 기종이 있으면 그 행을 재사용한다. 없으면`common_code`에 INSERT한다
  - 새 기종의`code`는`DEV`+ 다음`sortOrder`,`status`는`ACTIVE`,`sortOrder`는 기존 ACTIVE 기종의 마지막 값 + 1이다. 기종이 없으면 1
  - `modelCode`가 이미 있으면`400`, 메시지`이미 존재하는 모델코드입니다.`
  - 등록 직후`status`는`AVAILABLE`
  - DB 컬럼`device_code`에 공통코드 id를 저장한다
- Request Body

기존 기종:

```json
{
  "modelCode": "HK-GS23",
  "productName": "헬로키티케이스",
  "deviceCode": 3,
  "productDescription": "갤럭시 S23용 인쇄 케이스",
  "currentPrice": 12000
}
```

새 기종:

```json
{
  "modelCode": "HK-GS23",
  "productName": "헬로키티케이스",
  "deviceName": "갤럭시 S23",
  "productDescription": "갤럭시 S23용 인쇄 케이스",
  "currentPrice": 12000
}
```

- Response:`201 Created`

```json
{
  "id": 1,
  "modelCode": "HK-GS23",
  "productName": "헬로키티케이스",
  "deviceName": "갤럭시 S23",
  "productDescription": "갤럭시 S23용 인쇄 케이스",
  "currentPrice": 12000,
  "status": "AVAILABLE",
  "createdAt": "2026-10-24T10:30:00",
  "updatedAt": "2026-10-24T10:30:00"
}
```

### **4.3 품목 목록**

- Method:`GET`
- URI:`/api/v1/products`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  ◦ 쿼리 status: AVAILABLE 또는 DISCONTINUED (선택). 생략 시 전체
  ◦ 쿼리 page: 페이지 번호. 0부터 시작. 기본 0
  ◦ 쿼리 size: 페이지 크기. 기본 10
  ◦ 쿼리 sort: 기본 id,asc
  ◦ 공통 PageResponse로 반환한다
  ◦ 키워드 검색은 4.4를 쓴다
- Response:`200 OK`,`List<ProductDto.Response>`

```json
{
  "content": [
    {
      "id": 1,
      "modelCode": "HK-GS23",
      "productName": "헬로키티케이스",
      "deviceName": "갤럭시 S23",
      "productDescription": "갤럭시 S23용 인쇄 케이스",
      "currentPrice": 12000,
      "status": "AVAILABLE",
      "createdAt": "2026-10-24T10:30:00",
      "updatedAt": "2026-10-24T10:30:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

### **4.4 품목 검색**

- Method:`GET`
- URI:`/api/v1/products/search`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  ◦ 쿼리 keyword: 모델코드, 제품명, 기종 이름, 제품 설명 부분 검색 (선택)
  ◦ 쿼리 status: AVAILABLE 또는 DISCONTINUED (선택). 생략 시 상태 조건 없음
  ◦ 쿼리 page: 0부터. 기본 0
  ◦ 쿼리 size: 기본 10
  ◦ 쿼리 sort: 기본 id,asc
  ◦ keyword가 없거나 공백이면 4.3 목록과 같다
  ◦ 공통 PageResponse로 반환한다
- Response:`200 OK`,`List<ProductDto.Response>`

응답 필드는 4.3과 같다.

### **4.5 품목 상세**

- Method:`GET`
- URI:`/api/v1/products/{id}`
- Path Parameters
  - `id`: 품목 ID, 필수, Long
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  - 품목 정보만 반환한다
  - 현재 도면은 4.10, 수주 이력은 4.11
  - 없는`id`면 현재`500`, 메시지`품목을 찾을 수 없습니다: id={id}`
- Response:`200 OK`,`ProductDto.Response`

응답 필드는 4.2 등록 응답과 같다.

### **4.6 품목 수정**

- Method:`PUT`
- URI:`/api/v1/products/{id}`
- Path Parameters
  - `id`: 품목 ID, 필수, Long
- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙
  - 수정 필드:`productName`,`deviceCode`,`productDescription`,`currentPrice`
  - `modelCode`는 요청에 없고 바꾸지 않는다
  - `deviceCode`는 존재하는`common_code.id`여야 한다. 없으면 현재`500`, 메시지`기종 코드를 찾을 수 없습니다.`
  - `currentPrice`는 0보다 커야 한다. 아니면`400`, 메시지`기준 단가는 0보다 커야 합니다.`
  - `order_product.unit_price`는 바꾸지 않는다
  - 없는`id`면 현재`500`, 메시지`품목을 찾을 수 없습니다: id={id}`
- Request Body

```json
{
  "productName": "헬로키티케이스",
  "deviceCode": 3,
  "productDescription": "갤럭시 S23용 인쇄 케이스",
  "currentPrice": 13000
}
```

- Response:`200 OK`,`ProductDto.Response`

응답 필드는 4.2와 같다.

### **4.7 모델코드 중복 확인**

- Method:`GET`
- URI:`/api/v1/products/model-code/exists`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  - 쿼리`modelCode`: 필수. 앞뒤 공백은 제거한 뒤 비교한다
  - 비어 있으면`400`, 메시지`모델코드는 필수입니다.`
  - `available`이`true`이면 등록 가능하다.`false`이면 이미 있다
- Response:`200 OK`

```json
{
  "modelCode": "HK-GS23",
  "available": true
}
```

### **4.8 품목 상태 변경**

- Method:`PATCH`
- URI:`/api/v1/products/{id}/status`
- Path Parameters
  - `id`: 품목 ID, 필수, Long
- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙
  - `status`는`AVAILABLE`또는`DISCONTINUED`
  - 없는`id`면 현재`500`, 메시지`품목을 찾을 수 없습니다.`
  - 목록·검색에서 빼려면 조회 시`status=AVAILABLE`을 붙인다. 상태 변경 API가 다른 데이터를 지우지는 않는다
- Request Body

```json
{
  "status": "DISCONTINUED"
}
```

- Response:`200 OK`,`ProductDto.Response`

```json
{
  "id": 1,
  "modelCode": "HK-GS23",
  "productName": "헬로키티케이스",
  "deviceName": "갤럭시 S23",
  "productDescription": "갤럭시 S23용 인쇄 케이스",
  "currentPrice": 12000,
  "status": "DISCONTINUED",
  "createdAt": "2026-10-24T10:30:00",
  "updatedAt": "2026-10-24T10:30:00"
}
```

### **4.9 관련 파일 등록**

- Method:`POST`
- URI:`/api/v1/products/{id}/drawings`
- Path Parameters
  - `id`: 품목 ID, 필수, Long
- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙
  ◦ multipart/form-data
  ◦ 파트 이름: files(여러 파일), file(단건). 둘 다 보낼 수 있고, 합쳐서 순서대로 저장한다
  ◦ 유효한 파일이 하나도 없으면 400, 메시지 업로드할 파일이 없습니다.
  ◦ 100MB를 넘는 파일이 있으면 400, 메시지 파일은 100MB 이하여야 합니다.
  ◦ 파일은 스토리지에 올리고 object_key, file_name을 저장한다
  ◦ object_key는 products/{productId}/{uuid}_{fileName}
  ◦ 응답에는 objectKey와 조회 URL을 넣지 않는다
  ◦ 새 행의 replaced_at은 null이다
  ◦ 업로드가 기존 행의 replaced_at을 채우거나 기존 행을 삭제하지는 않는다. replaced_at이 null인 행이 여러 개일 수 있다
  ◦ 없는 id면 현재 500, 메시지 품목을 찾을 수 없습니다: id={id}
- Response:`201 Created`  `List<ProductDrawingDto.Response>`

```json
{
  "id": 10,
  "fileName": "drawing.pdf",
  "createdAt": "2026-10-24T10:30:00"
}
```

### **4.10 관련 파일 조회**

- Method:`GET`
- URI:`/api/v1/products/{id}/drawings/current`
- Path Parameters
  - `id`: 품목 ID, 필수, Long
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  ◦ replaced_at이 null인 product_drawing을 반환한다
  ◦ 각 항목에 S3 Presigned GET URL(previewUrl)을 포함한다. 유효 시간은 10분이다
  ◦ expiresAt은 그 Presigned URL 만료 시각이다
  ◦ 응답에 objectKey는 넣지 않는다
  ◦ 없는 id면 현재 500, 메시지 품목을 찾을 수 없습니다: id={id}
  - Response:`200 OK`  `List<ProductDrawing.Dto.CurrentResponse>`

```json
[
  {
    "id": 10,
    "fileName": "drawing.pdf",
    "createdAt": "2026-10-24T10:30:00",
    "previewUrl": "https://prodflow-bucket.s3.ap-northeast-2.amazonaws.com/...",
    "expiresAt": "2026-10-24T10:40:00Z"
  }
]
```

### **4.11 품목 관련 수주 이력**

- Method:`GET`
- URI:`/api/v1/products/{id}/orders`
- Path Parameters
  - `id`: 품목 ID, 필수, Long
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  - 쿼리`size`: 조회 건수. 기본 3. 0 이하면 3으로 처리한다
  - 해당 품목이 들어간 최근 수주 라인이다
  - `lineAmount`는 라인 금액이다. 수주에 부가세가 포함돼 있으면`unitPrice * quantity`, 아니면 그 금액의 1.1배를 반올림한 값이다
  - `totalAmount`는 수주 전체 금액이다
  - `statusCode`,`statusName`은 수주 상태 공통코드다
  - 없는`id`면 현재`500`, 메시지`품목을 찾을 수 없습니다: id={id}`
- Response:`200 OK`

```json
[
  {
    "orderId": 13,
    "orderNo": "PF-2026-013",
    "clientName": "모바일(주)",
    "productName": "헬로키티케이스",
    "quantity": 100,
    "unitPrice": 12000,
    "lineAmount": 1320000,
    "totalAmount": 1320000,
    "estimatedDueDate": "2026-11-20",
    "statusCode": "WAITING",
    "statusName": "생산대기"
  }
]
```

## **5. 수주 및 견적**

날짜는`yyyy-MM-dd`, 일시는 ISO 8601 문자열을 사용한다. 이 장의 페이징 응답은`content`,`page`,`size`,`totalElements`,`totalPages`이며,`page`는 0부터
시작한다.

견적 승인과 수주 확정은 별도이다. 견적을 승인해도 수주가 자동 생성되지 않으며, 수주 등록 이후 계약금 확인 시 확정 및 작업지시 생성이 진행된다.

### **5.1 수주 등록**

- Method:`POST`
- URI:`/api/v1/orders`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙
  - `APPROVED`견적만 등록할 수 있다. 이미 연결된 수주가 있으면`409`로 거절한다.
  - `items`에는 승인 견적의 모든 품목을 중복 없이 전달한다.`quotationItemId`는 견적 품목 ID이며`productId`가 아니다.
  - 품목별 수량은 100개 이상, 승인 견적 수량 이하여야 한다. 단가·VAT·거래처 정보는 견적에서 가져온다.
  - 최종`dueDate`는 저장 직전 최신 생산 상황으로 재검증한다. 계약금 마감일은 한국 날짜 기준 등록일+3일이다.
  - `useCompanyAddress=true`이면 거래처 주소를 사용한다.`false`이면`shippingAddress`의 우편번호·도로명·상세 주소가 모두 필요하다.
  - 초기 진행 상태는`WAITING`이다. 등록 시 결제 건·가상계좌·작업지시는 생성하지 않는다.
- Request Body

```json
{
  "quotationId": 17,
  "items": [
    {
      "quotationItemId": 31,
      "quantity": 1000
    }
  ],
  "dueDate": "2026-11-30",
  "useCompanyAddress": false,
  "shippingAddress": {
    "zipcode": "08501",
    "roadAddress": "서울특별시 금천구 가산디지털1로 1",
    "detailAddress": "101호"
  },
  "receiverName": "김담당",
  "receiverPhone": "010-2222-3333",
  "notifyChannel": "EMAIL",
  "memo": "로고 위치 확인"
}
```

`notifyChannel`은`SMS`,`EMAIL`중 하나이다. 수령인명은 필수·최대 255자, 연락처는 숫자·`+`·괄호·공백·`-`로 8~50자, 메모는 최대 255자이다.

- Response:`201 Created`
  - `Location: /api/v1/orders/{id}`

```json
{
  "id": 15,
  "orderNo": "PF-20261109-15",
  "quotationId": 17,
  "totalAmount": 12000000,
  "dueDate": "2026-11-30",
  "depositDueDate": "2026-11-12",
  "plannedStartDate": "2026-11-25",
  "plannedEndDate": "2026-11-25",
  "status": "WAITING",
  "confirmationStatus": "PENDING",
  "paymentStatus": "UNPAID"
}
```

- 주요 오류:`400`입력·수량·배송지·납기 오류,`403`권한·계정 조건 미충족,`404`견적 없음,`409`미승인 견적·중복 수주

### **5.2 수주 목록**

- Method:`GET`
- URI:`/api/v1/orders`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- Query Parameters

| **파라미터**             | **타입**  | **필수** | **기본값**      | **설명**                              |
|----------------------|---------|--------|--------------|-------------------------------------|
| `tab`                | String  | X      | `OPEN`       | `OPEN`: 완료·취소 제외, `DONE`: 완료 수주     |
| `keyword`            | String  | X      | 없음           | 수주번호·거래처명·저장된 품목명 검색                |
| `confirmationStatus` | String  | X      | 전체           | `PENDING`, `CONFIRMED`, `CANCELLED` |
| `statusCodeId`       | Long    | X      | 전체           | 수주 진행 상태 공통코드 ID                    |
| `paymentStatus`      | String  | X      | 전체           | `UNPAID`, `PARTIALLY_PAID`, `PAID`  |
| `sortType`           | String  | X      | `CREATED_AT` | `CREATED_AT`, `COMPLETED_AT`        |
| `page`               | Integer | X      | 0            | 페이지 번호                              |
| `size`               | Integer | X      | 8            | 페이지 크기                              |

- 처리 규칙
  - 검색 조건은 모두 AND로 적용한다. 선택 날짜 기준 내림차순이며 완료일이 없으면 뒤에 표시한다.
  - `statusCodeId`는 공통코드 그룹 ID 2의 수주 진행 상태를 사용한다.
  - `completedAt`은 배송 완료일이다.`delay`는 납기 지연·주의 판단 결과이다.
- 요청 예시:`GET /api/v1/orders?tab=OPEN&keyword=키티&page=0&size=8`
- Response:`200 OK`

```json
{
  "content": [
    {
      "id": 15,
      "orderNo": "PF-20261109-15",
      "clientId": 1,
      "clientCompany": "샘플상사",
      "items": [
        {
          "id": 30,
          "productId": 1,
          "productName": "헬로키티케이스",
          "quantity": 1000
        }
      ],
      "itemCount": 1,
      "totalQuantity": 1000,
      "totalAmount": 12000000,
      "dueDate": "2026-11-30",
      "plannedStartDate": "2026-11-25",
      "plannedEndDate": "2026-11-25",
      "depositDueDate": "2026-11-12",
      "confirmationStatus": "PENDING",
      "statusCodeId": 10,
      "status": "WAITING",
      "statusName": "대기",
      "paymentStatus": "UNPAID",
      "carrierName": null,
      "trackingNumber": null,
      "completedAt": null,
      "createdAt": "2026-11-09T10:00:00",
      "delay": null
    }
  ],
  "page": 0,
  "size": 8,
  "totalElements": 1,
  "totalPages": 1
}
```

### **5.3 수주 상세**

- Method:`GET`
- URI:`/api/v1/orders/{orderId}`
- Path Parameters:`orderId`— 수주 ID, Long
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  - 저장된 수주·품목·거래처 연락처·배송지·금액·일정과 납기 지연 판단을 반환한다.
  - 입금 내역은`/api/v1/payments?orderId={orderId}`, 상태 변경 이력은 5.4 API로 별도 조회한다. 상세 응답에 포함하지 않는다.
- Response:`200 OK`

```json
{
  "id": 15,
  "orderNo": "PF-20261109-15",
  "quotationId": 17,
  "quotationNo": "QT-20261109-17",
  "userId": 2,
  "userName": "김수주",
  "createdAt": "2026-11-09T10:00:00",
  "updatedAt": "2026-11-09T10:00:00",
  "confirmationStatus": "PENDING",
  "statusCodeId": 10,
  "status": "WAITING",
  "statusName": "대기",
  "paymentStatus": "UNPAID",
  "client": {
    "id": 1,
    "companyName": "샘플상사",
    "contactName": "김담당",
    "email": "client@example.com",
    "phone": "010-2222-3333",
    "address": "서울특별시 금천구 가산디지털1로 1 101호"
  },
  "items": [
    {
      "id": 30,
      "productId": 1,
      "productName": "헬로키티케이스",
      "deviceName": "갤럭시 S23",
      "quantity": 1000,
      "unitPrice": 12000,
      "supplyAmount": 10909091,
      "vatAmount": 1090909,
      "lineAmount": 12000000
    }
  ],
  "vatIncluded": true,
  "supplyAmount": 10909091,
  "vatAmount": 1090909,
  "totalAmount": 12000000,
  "totalQuantity": 1000,
  "requestedDueDate": "2026-11-30",
  "dueDate": "2026-11-30",
  "plannedStartDate": "2026-11-25",
  "plannedEndDate": "2026-11-25",
  "productionCompletedAt": null,
  "depositDueDate": "2026-11-12",
  "shippingAddress": "서울특별시 금천구 가산디지털1로 1 101호",
  "receiverName": "김담당",
  "receiverPhone": "010-2222-3333",
  "notifyChannel": "EMAIL",
  "memo": "로고 위치 확인",
  "delay": null
}
```

- 주요 오류:`404`수주 없음

### **5.4 수주 상태 변경 이력**

- Method:`GET`
- URI:`/api/v1/orders/{orderId}/histories`
- Path Parameters:`orderId`— 수주 ID, Long
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  - 해당 수주의 상태 이력을 반환한다. 변경 전·후 상태, 처리자, 사유, 처리 시각을 확인할 수 있다.
  - 최신 처리 시각·ID 순서로 반환한다.`userName`은 이력에 저장된 처리자명이다. 시스템 처리 시`userId`,`userName`은 null일 수 있다.
- Response:`200 OK`

```json
[
  {
    "id": 10,
    "fromStatus": "WAITING",
    "toStatus": "CONFIRMED",
    "userId": 2,
    "userName": "김수주",
    "note": "계약금 수동 입금 확인",
    "createdAt": "2026-11-09T11:00:00"
  }
]
```

### **5.5 수주 전체 집계**

- Method:`GET`
- URI:`/api/v1/orders/summary`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  - 검색·페이지 조건과 독립된 전체 카드 및 탭 건수를 반환한다.
  - `waiting`은`CONFIRMED`상태의 생산대기 건수이다.
  - `making`은`IN_PRODUCTION`,`PACKING`,`ON_HOLD`의 합계이다.
  - `shipping`은`READY_TO_SHIP`,`SHIPPING`의 합계이다.
  - `doneThisMonth`는 한국 날짜 기준 이번 달 배송 완료 건수이다.
- Response:`200 OK`

```json
{
  "waiting": 3,
  "making": 5,
  "shipping": 2,
  "doneThisMonth": 4,
  "openCount": 10,
  "doneCount": 8
}
```

### **5.6 수주 진행 상태 목록**

- Method:`GET`
- URI:`/api/v1/orders/statuses`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙:`common_code`에서`code_group_id=2`,`status=ACTIVE`인 코드를`sortOrder`순서로 반환한다.
- Response:`200 OK`

```json
[
  {
    "id": 10,
    "code": "WAITING",
    "name": "대기",
    "sortOrder": 1
  }
]
```

### **5.7 수주 목록 CSV 내보내기**

- Method:`GET`
- URI:`/api/v1/orders/export`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- Query Parameters: 5.2의`tab`,`keyword`,`confirmationStatus`,`statusCodeId`,`paymentStatus`,`sortType`과 동일하다.`page`,
  `size`는 사용하지 않는다.
- 처리 규칙: 선택한 탭·검색·필터에 해당하는 전체 결과를 페이지 제한 없이 UTF-8 BOM CSV로 내려준다.
- 요청 예시:`GET /api/v1/orders/export?tab=OPEN&paymentStatus=UNPAID`
- Response:`200 OK`
  - `Content-Type: text/csv;charset=UTF-8`
  - `Content-Disposition: attachment`— 파일명`수주목록.csv`
  - 응답 본문은 CSV 파일이며 JSON이 아니다.

### 5.8 거래처 수주 이력 조회

- Method: `GET`
- URI: `/api/v1/orders/clients/{id}`
- Path Parameters
  - id: 거래처 ID, 필수, Long
- 인증 필요 여부: 불필요
- 처리 규칙
  - 특정 거래처가 수주 주문한 이력들을 조회한다
- Response: `200 OK`, `Page<CustomerOrderDto.ClientDetailResponse>`

    ```json
    {
    	"content": [
    		{
    			"customer_order": {
    				...
    			},
    			"product_name": "갤럭시 케이스"
    		},
    		{
    			...
    		}
    	],
    	"empty": false,
    	...
    }
    ```

  - content: Page 클래스에서 구조체 리스트가 들어가는 부분
    - 여기서는 CustomerOrderDto.ClientDetailResponse 리스트가 들어감
  - customer_order: 거래처의 개별 수주 내역, customer_order 테이블 레코드
  - product_name: 해당 수주의 주문 제품명, String
  - 그 뒤로는 Page 클래스 속성들이 나열

### 5.9 거래처의 가장 최근 수주 내역 조회

- Method: `GET`
- URI: `/api/v1/orders/clients/{id}/recent`
- Path Parameters
  - id: 거래처 ID, 필수, Long
- 인증 필요 여부: 불필요
- 처리 규칙
  - 특정 거래처의 가장 최근에 수주한 내역을 조회한다
- Response: `200 OK`, `CustomerOrderDto.ClientDetailResponse`

    ```json
    {
    	"customer_order": {
    		...
    	},
    	"product_name": "갤럭시 케이스"
    }
    ```

  - customer_order: 거래처의 개별 수주 내역, customer_order 테이블 레코드
  - product_name: 해당 수주의 주문 제품명, String

### **5.10 견적 초안 저장**

- Method:`POST`
- URI:`/api/v1/quotations`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙
  - `clientId`와`newClient`중 하나만 전달한다. 신규 거래처 정보가 있으면 거래처를 함께 등록한다.
  - 희망 납기는 오늘 이후여야 한다.`validUntil`은 발행일+7일 또는 +14일이며 생략 시 +14일이다.
  - 품목별 수량은 최소 100개, 단가는 0원 이상이다. 견적 단가는 요청으로 전달한다.
  - 금액과 생산 일정을 계산하여`DRAFT`로 저장한다. PDF 생성·이메일 발송·생산 자리 예약은 하지 않는다.
- Request Body — 기존 거래처

```json
{
  "clientId": 1,
  "requestedDueDate": "2026-11-30",
  "validUntil": "2026-11-23",
  "vatIncluded": true,
  "items": [
    {
      "productId": 1,
      "quantity": 1000,
      "unitPrice": 12000
    }
  ]
}
```

- 신규 거래처는`clientId`를 생략하고 다음`newClient`를 전달한다.

```json
{
  "company": "샘플상사",
  "employee": "김담당",
  "email": "client@example.com",
  "phone": "010-2222-3333",
  "zipcode": "08501",
  "roadAddress": "서울특별시 금천구 가산디지털1로 1",
  "detailAddress": "101호"
}
```

- Response:`201 Created`— 5.10의 견적 상세 응답,`Location: /api/v1/quotations/{id}`,`Cache-Control: no-store`
- 주요 오류:`400`거래처 선택·품목·수량·유효기간·납기 오류,`403`권한·계정 조건 미충족,`404`거래처·품목 없음

### **5.11 견적 목록**

- Method:`GET`
- URI:`/api/v1/quotations`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- Query Parameters

| **파라미터**       | **타입**    | **필수** | **기본값**      | **설명**                                                                  |
|----------------|-----------|--------|--------------|-------------------------------------------------------------------------|
| `keyword`      | String    | X      | 없음           | 견적번호·거래처명·작성자 검색                                                        |
| `status`       | String    | X      | 전체           | `DRAFT`, `SENT`, `APPROVED`, `REJECTED`, `CANCELLED`, `EXPIRED`         |
| `orderPending` | Boolean   | X      | false        | 승인 상태이며 연결 수주가 없는 견적만 조회                                                |
| `sortType`     | String    | X      | `CREATED_AT` | `CREATED_AT`, `REQUESTED_DUE_DATE`, `ESTIMATED_DUE_DATE`, `VALID_UNTIL` |
| `startDate`    | LocalDate | X      | 없음           | 현재 형식 검증만 수행, 실제 기간 필터 미적용                                              |
| `endDate`      | LocalDate | X      | 없음           | 현재 형식 검증만 수행, 실제 기간 필터 미적용                                              |
| `page`         | Integer   | X      | 0            | 페이지 번호                                                                  |
| `size`         | Integer   | X      | 12           | 페이지 크기                                                                  |

- 처리 규칙: 상태 생략·빈 값·공백이면 전체 상태를 조회한다. 선택한 날짜 기준 내림차순, 같은 날짜는 ID 내림차순이다.`orderPending`과`status`는 함께 적용된다.
- Response:`200 OK`

```json
{
  "content": [
    {
      "id": 17,
      "quotationNo": "QT-20261109-17",
      "clientId": 1,
      "clientCompany": "샘플상사",
      "requestedDueDate": "2026-11-30",
      "estimatedDueDate": "2026-11-30",
      "totalAmount": 12000000,
      "status": "DRAFT",
      "customerOrderId": null,
      "orderNo": "-",
      "validUntil": "2026-11-23",
      "userId": 2,
      "userName": "김수주",
      "createdAt": "2026-11-09T09:00:00"
    }
  ],
  "page": 0,
  "size": 12,
  "totalElements": 1,
  "totalPages": 1
}
```

### **5.12 견적 상세**

- Method:`GET`
- URI:`/api/v1/quotations/{quotationId}`
- Path Parameters:`quotationId`— 견적 ID, Long
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙: 견적·거래처·품목·금액·일정·작성자·연결 수주 정보를 반환한다. 연결 수주가 없으면 상세의`customerOrderId`,`orderNo`는 null이다.
- Response:`200 OK`

```json
{
  "id": 17,
  "quotationNo": "QT-20261109-17",
  "status": "DRAFT",
  "clientId": 1,
  "clientCompany": "샘플상사",
  "clientName": "김담당",
  "clientPhone": "010-2222-3333",
  "clientEmail": "client@example.com",
  "clientAddress": "서울특별시 금천구 가산디지털1로 1 101호",
  "itemCount": 1,
  "totalQuantity": 1000,
  "items": [
    {
      "id": 31,
      "productId": 1,
      "productName": "헬로키티케이스",
      "deviceName": "갤럭시 S23",
      "quantity": 1000,
      "unitPrice": 12000,
      "lineAmount": 12000000
    }
  ],
  "vatIncluded": true,
  "supplyAmount": 10909091,
  "vatAmount": 1090909,
  "totalAmount": 12000000,
  "depositAmount": 6000000,
  "balanceAmount": 6000000,
  "requestedDueDate": "2026-11-30",
  "estimatedDueDate": "2026-11-30",
  "plannedStartDate": "2026-11-25",
  "plannedEndDate": "2026-11-25",
  "validUntil": "2026-11-23",
  "userId": 2,
  "userName": "김수주",
  "customerOrderId": null,
  "orderNo": null,
  "createdAt": "2026-11-09T09:00:00",
  "updatedAt": "2026-11-09T09:00:00",
  "createdBy": "김수주"
}
```

- 주요 오류:`404`견적 없음

### **5.13 견적 초안 수정**

- Method:`PUT`
- URI:`/api/v1/quotations/{quotationId}`
- Path Parameters:`quotationId`— 견적 ID, Long
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- Request Body: 5.8과 동일한 전체 입력
- 처리 규칙
  - `DRAFT`만 수정한다. 견적 ID·번호·최초 작성자를 유지하고 품목·금액·납기를 갱신한다.
  - 기존 PDF를 무효화하며 PDF 생성·메일 발송은 하지 않는다.
  - 유효기간 생략 시 기존 값을 유지한다. 지정 시 최초 발행일+7일 또는 +14일이어야 한다.
- Response:`200 OK`— 5.10의 견적 상세 응답,`Cache-Control: no-store`
- 주요 오류:`400`초안이 아닌 상태·입력 오류,`404`견적·거래처·품목 없음

### **5.14 견적 초안 삭제**

- Method:`DELETE`
- URI:`/api/v1/quotations/{quotationId}`
- Path Parameters:`quotationId`— 견적 ID, Long
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙:`DRAFT`견적과 품목을 삭제하고 커밋 후 PDF를 정리한다. 거래처는 유지한다.
- Response:`204 No Content`— 응답 본문 없음
- 주요 오류:`404`견적 없음,`409`초안이 아닌 상태

### **5.15 신규 견적 납기 미리보기**

- Method:`POST`
- URI:`/api/v1/quotations/schedule`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙
  - 품목별 수량은 최소 100개이다. 희망 납기 생략 시 가장 빠른 납기와 생산 일정을 반환한다.
  - 불가능한 희망 납기에는`200`과`feasible=false`를 반환하며 생산 시작·완료 예정일은 null이다.
  - 한국 날짜 기준 다음 해 12월 31일까지 계산한다. 견적 저장·생산 자리 예약은 하지 않는다.
  - 수주 등록 전에도 선택한 품목·수량·납기의 확인에 사용할 수 있으며, 실제 저장 시 재검증한다.
- Request Body

```json
{
  "items": [
    {
      "productId": 1,
      "quantity": 1000
    }
  ],
  "requestedDueDate": "2026-11-30"
}
```

- Response:`200 OK`

```json
{
  "earliestDueDate": "2026-11-17",
  "requestedDueDate": "2026-11-30",
  "feasible": true,
  "plannedStartDate": "2026-11-25",
  "plannedEndDate": "2026-11-25"
}
```

- 주요 오류:`400`입력·품목·계산 범위 오류

### **5.16 기존 견적 납기 재산정 미리보기**

- Method:`POST`
- URI:`/api/v1/quotations/{quotationId}/schedule`
- Path Parameters:`quotationId`— 견적 ID, Long
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- Request Body:`{ "requestedDueDate": "2026-11-30" }`— 날짜 생략 시`{}`
- 처리 규칙: 유효기간 내`SENT`견적의 저장된 품목·수량으로 계산한다. 견적을 변경하지 않는다.
- Response:`200 OK`— 5.13의 납기 미리보기 응답
- 주요 오류:`400`상태·유효기간·입력·계산 범위 오류,`404`견적 없음

### **5.17 기존 견적 납기 변경**

- Method:`PATCH`
- URI:`/api/v1/quotations/{quotationId}/schedule`
- Path Parameters:`quotationId`— 견적 ID, Long
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- Request Body:`{ "requestedDueDate": "2026-11-30" }`— 날짜 필수
- 처리 규칙
  - 유효기간 내`SENT`견적만 변경한다. 저장 직전 재계산하며 불가능하면 기존 일정을 유지한다.
  - 희망·예상 납기, 생산 시작·완료 예정일만 변경한다. 품목·금액·유효기간·상태는 유지하고 메일은 보내지 않는다.
- Response:`200 OK`— 5.10의 견적 상세 응답
- 주요 오류:`400`상태·유효기간·불가능한 납기,`404`견적 없음

### **5.18 견적 초안 저장 및 PDF 생성**

- Method:`POST`
- URI:`/api/v1/quotations/pdf`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- Request Body: 5.8의 견적 초안 입력과 동일
- 처리 규칙: 초안 저장·커밋 후 PDF를 생성하여 S3에 저장한다. 메일은 보내지 않는다. PDF 준비 실패 시에도 저장된 초안은 유지한다.
- Response:`201 Created`
  - `Location: /api/v1/quotations/{quotationId}`
  - `X-Quotation-Id: {quotationId}`,`Cache-Control: no-store`

```json
{
  "quotationId": 17,
  "quotationNo": "QT-20261109-17",
  "clientCompany": "샘플상사",
  "clientEmail": "client@example.com",
  "previewUrl": "https://example-bucket.s3.amazonaws.com/quotation.pdf?signature=example",
  "expiresAt": "2026-11-09T01:15:00Z",
  "confirmationToken": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
}
```

`previewUrl`은 만료 시각이 있는 서명 URL이며 PDF 파일 자체가 아니다.`confirmationToken`은 확인한 문서를 발송할 때 사용한다.

- PDF 준비 실패 응답:`502 Bad Gateway`예시

```json
{
  "code": "PDF_PREPARATION_FAILED",
  "message": "PDF를 준비하지 못했습니다. 다시 시도해주세요.",
  "quotationId": 17,
  "retryUrl": "/api/v1/quotations/17/pdf/regenerate"
}
```

### **5.19 견적 초안 수정 및 PDF 재생성**

- Method:`PUT`
- URI:`/api/v1/quotations/{quotationId}/pdf`
- Path Parameters:`quotationId`— 견적 ID, Long
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- Request Body: 5.8의 견적 초안 입력과 동일
- 처리 규칙:`DRAFT`를 수정·커밋한 뒤 PDF를 준비한다. ID·번호·최초 작성자를 유지하며 PDF 실패 시에도 수정 내용은 유지한다.
- Response:`200 OK`— 5.16의 PDF 미리보기 응답,`Cache-Control: no-store`
- 주요 오류:`400`초안 상태·입력·PDF 생성 오류,`404`견적 없음,`502`PDF 준비 실패

### **5.20 기존 초안 PDF 재생성**

- Method:`POST`
- URI:`/api/v1/quotations/{quotationId}/pdf/regenerate`
- Path Parameters:`quotationId`— 견적 ID, Long
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- Request Body: 없음
- 처리 규칙: 저장된`DRAFT`정보로 PDF를 다시 생성한다. 초안 입력을 수정하거나 메일을 보내지 않는다.
- Response:`200 OK`— 5.16의 PDF 미리보기 응답,`Cache-Control: no-store`
- 주요 오류:`400`초안이 아닌 상태·PDF 생성 오류,`404`견적 없음,`502`PDF 준비 실패

### **5.21 견적 PDF 미리보기 조회**

- Method:`GET`
- URI:`/api/v1/quotations/{quotationId}/pdf`
- Path Parameters:`quotationId`— 견적 ID, Long
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙: 저장된 PDF를 확인하고 서명 URL과 확인 토큰을 반환한다. JSON 응답의`previewUrl`로 PDF를 연다.
- Response:`200 OK`— 5.16의 PDF 미리보기 응답,`Cache-Control: no-store`
- 주요 오류:`404`견적 없음,`409 PDF_NOT_READY`PDF 미준비,`409 PDF_CHANGED`현재 견적과 PDF 불일치,`502`PDF 조회 실패

### **5.22 견적 발송 확정 및 이메일 전송**

- Method:`POST`
- URI:`/api/v1/quotations/{quotationId}/send`
- Path Parameters:`quotationId`— 견적 ID, Long
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- Request Body

```json
{
  "confirmationToken": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
}
```

- 처리 규칙
  - PDF 미리보기에서 받은`confirmationToken`을 보낸다. 소문자 16진수 64자리 두 개를`.`으로 연결한 형식이다.
  - `DRAFT`와 현재 PDF·유효기간·납기를 검증하고`SENT`로 확정한다. 커밋 후 거래처 이메일로 PDF를 첨부하여 전송한다.
  - 이미`SENT`이면`duplicateRequest=true`를 반환하며 다시 발송하지 않는다.
  - 메일 전송 실패 시에도 견적은`SENT`를 유지한다. HTTP 성공 여부와 별도로`emailStatus`를 확인해야 한다.
  - 메일의 수신자·결과·실패 사유는`client_noti_log`에`kind=QUOTATION`으로 기록한다.
- Response:`200 OK`,`Cache-Control: no-store`

```json
{
  "quotationId": 17,
  "status": "SENT",
  "emailStatus": "SENT",
  "duplicateRequest": false,
  "message": "견적 발송이 완료되었습니다."
}
```

| **`emailStatus`** | **의미**                          |
|-------------------|---------------------------------|
| `SENT`            | 메일 전송 성공                        |
| `FAILED`          | 메일 전송 실패. 자동 재발송하지 않음           |
| `UNKNOWN`         | 중복 요청 시 기존 메일 처리 결과가 아직 확인되지 않음 |

- 주요 오류:`400`토큰 형식 오류,`404`견적 없음,`409`상태·PDF·납기·유효기간 재확인 필요,`502`발송 확정 전 PDF 읽기 실패

### **5.23 견적 승인**

- Method:`PATCH`
- URI:`/api/v1/quotations/{quotationId}/approve`
- Path Parameters:`quotationId`— 견적 ID, Long
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- Request Body: 없음
- 처리 규칙: 거래처 승인 결과를 직원이 반영한다. 유효기간 내`SENT`를`APPROVED`로 변경하며, 이미 승인된 견적은 현재 상세를 반환한다. 수주는 자동 생성하지 않는다.
- Response:`200 OK`— 5.10의 견적 상세 응답
- 주요 오류:`404`견적 없음,`409`처리 불가 상태·유효기간 경과

### **5.24 견적 거절**

- Method:`PATCH`
- URI:`/api/v1/quotations/{quotationId}/reject`
- Path Parameters:`quotationId`— 견적 ID, Long
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- Request Body: 없음
- 처리 규칙: 유효기간 내`SENT`를`REJECTED`로 변경한다. 같은 거절 요청은 현재 상세를 반환한다.
- Response:`200 OK`— 5.10의 견적 상세 응답
- 주요 오류:`404`견적 없음,`409`처리 불가 상태·유효기간 경과

### **5.25 견적 취소**

- Method:`PATCH`
- URI:`/api/v1/quotations/{quotationId}/cancel`
- Path Parameters:`quotationId`— 견적 ID, Long
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- Request Body: 없음
- 처리 규칙: 유효기간 내`SENT`를`CANCELLED`로 변경한다. 연결 수주를 취소하는 API가 아니다.
- Response:`200 OK`— 5.10의 견적 상세 응답
- 주요 오류:`404`견적 없음,`409`처리 불가 상태·유효기간 경과

### **5.26 견적 수동 만료 처리**

- Method:`POST`
- URI:`/api/v1/quotations/expire`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- Request Body: 없음
- 처리 규칙: 유효기간이 지난`SENT`견적을`EXPIRED`로 변경한다. 유효기간 당일은 제외한다. 서버 시작·일일 스케줄러에서도 같은 만료 조건을 사용한다.
- Response:`200 OK`

```json
{
  "expiredCount": 3
}
```

## 6. 생산관리

### 6.1 생산라인 등록

권한:`SUPER_ADMIN`.

- Method:`POST`
- URI:`/api/v1/lines`
- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`

처리 규칙

- 요청 본문으로 라인을 등록
- 상태는`AVAILABLE`로 둠
- `updatedAt`은 `creatededAt`과 동일
- 등록되면`201 Created`와 라인 정보를 반환
- Request Body: 필수,`LineDto.CreateRequest`

```json
{
  "lineName": "1라인",
  "productionCapacity": 1000
}
```

- lineName: 라인 이름, String
- productionCapacity: 일일 생산능력, int
- Response:`201 Created`,`LineDto.Response`

```json
{
  "id": 1,
  "lineName": "1라인",
  "productionCapacity": 1000,
  "introducedDate": "2024-03-01",
  "status": "AVAILABLE",
  "updatedAt": null
}
```

### 6.2  라인 정보 수정

- Method:`PATCH`
- URI:`/api/v1/lines/{lineId}`
- 인증 필요 여부: 필수
  - 호출 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
  - 이름 변경:`SUPER_ADMIN`
  - 하루 생산량 변경:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙
  - `lineName`과`productionCapacity`를 함께 받음
  - 이름이 바뀌면 최고 관리자만 수정함
  - 하루 생산량이 바뀔 때만 생산량 변경 이력을 남긴다. 이름만 바꿀 때는 사유가 없어도 됨
  - 생산량을 줄이면 같은 라인에서 재배치
  - `updatedAt`은 수정 시각으로 기록
- Request Body: 필수,`LineDto.UpdateRequest`

```json
{
  "lineName": "1라인",
  "productionCapacity": 1200,
  "reason": "설비 증설"
}
```

- `lineName`: 라인명, 공백 불가
- `productionCapacity`: 일일 생산능력, int, 1 이상
- `reason`: 생산량 변경 사유. 이름만 바꿀 때는 생략할 수 있음
- Response:`200 OK`,`LineDto.Response`

### 6.3 라인 상태 변경

- Method:`PATCH`
- URI:`/api/v1/lines/{lineId}/status`
- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`,`PRODUCTION_MANAGER`
- 처리 규칙
  - 상태를`AVAILABLE`,`RUNNING`,`INSPECTION`,`BROKEN`중 하나로 변경한다.
  - 같은 상태면 변경하지 않음
  - 점검은 생산 중인 작업이 있으면 거절한다. 준비 작업만 다른 가동 라인으로 옮긴 뒤 점검으로 저장
  - 고장은 준비 작업만 다른 가동 라인으로 옮긴다. 자리가 없는 준비 작업은 그 라인에 남기고 고장을 저장
  - 가동 가능으로 되돌리면 라인 상태만 바꾸고, 빠져 있던 작업은 자동으로 돌아오지 않음
  - 변경 내용은 상태 이력에 남김
  - `updatedAt`은 변경 시각으로 기록
- Request Body: 필수,`LineDto.UpdateStatusRequest`

```json
{
  "status": "INSPECTION"
}
```

- status: 라인 상태, LineStatus
- Response:`200 OK`,`LineDto.Response`

### 6.3 생산라인 목록 조회

- Method:`GET`
- URI:`/api/v1/lines`
- 인증 필요 여부: 필수
  - 권한: 로그인 사용자
- 처리 규칙
  - 전체 라인과 상태별 건수를 함께 조회
  - 각 라인에는 생산 중(`PRODUCE`)인 작업 한 건의 작업 번호, 품목, 수주, 담당자를 붙임
  - 생산 중인 작업이 없으면 그 네 값은`null`
- Request Body: 없음
- Response:`200 OK`,`LineDto.ListResponse`

```json
 {
  "totalCount": 4,
  "runningCount": 1,
  "availableCount": 2,
  "inspectionOrBrokenCount": 1,
  "lines": [
    {
      "lineId": 1,
      "lineName": "1라인",
      "status": "OPERATING",
      "productionCapacity": 1000,
      "workNo": "WO-2026-001",
      "productName": "품목 A",
      "orderNo": "CO-2026-001",
      "managerName": "이재원"
    }
  ]
}

```

### 6.3.1 생산라인 상세 조회

- Method:`GET`
- URI:`/api/v1/lines/{lineId}`
- 인증 필요 여부: 필수
  - 권한: 로그인 사용자
- 처리 규칙
  - 라인 정보, 현재 생산 작업, 배정된 작업지시, 생산량 변경 이력을 조회
  - 작업 목록은 준비, 생산, 일시정지만 포함한다. 포장, 포장완료, 생산 중지는 빠짐
  - 생산 중인 작업이 없으면 현재 작업의 작업 번호, 품목, 수주, 일정, 담당자는`null`
  - 해당 상태의 작업이 없으면`workOrders`는 빈 배열
  - 라인이 없으면`400`을 반환

    ```json
    {
      "lineId": 1,
      "lineName": "1라인",
      "status": "OPERATING",
      "productionCapacity": 1000,
      "workOrderCount": 2,
      "waitingCount": 1,
      "workNo": "WO-2026-001",
      "productName": "품목 A",
      "orderNo": "CO-2026-001",
      "plannedStartDate": "2026-11-10",
      "plannedEndDate": "2026-11-14",
      "managerName": "이재원",
      "workOrders": [
        {
          "workNo": "WO-2026-001",
          "orderNo": "CO-2026-001",
          "productName": "품목 A",
          "quantity": 500,
          "plannedStartDate": "2026-11-10",
          "plannedEndDate": "2026-11-14",
          "status": "PRODUCE"
        },
        {
          "workNo": "WO-2026-004",
          "orderNo": "CO-2026-003",
          "productName": "품목 B",
          "quantity": 300,
          "plannedStartDate": "2026-11-17",
          "plannedEndDate": "2026-11-18",
          "status": "PREPARE"
        }
      ],
      "capacityLogs": [
        {
          "changedAt": "2026-11-01T09:00:00",
          "beforeCapacity": 800,
          "afterCapacity": 1000,
          "reason": "설비 증설",
          "managerName": "김태호"
        }
      ]
    }
    ```

- Request Body: 없음
- Response:`200 OK`,`LineDto.DetailResponse`

### 6.4 작업지시 자동 생성

- Method: 없음
- URI: 별도 API 없이 계약금 결제 완료 시 자동 생성
- 인증 필요 여부: 해당 없음
- 처리 규칙
  - 생성 API는 없음
  - 계약금 결제가`PAID`가 되면 서버가`work_order`와`production_plan`을 생성
  - 수주를`CONFIRMED`로 바꾼다.
  - 한 수주 수량을 라인별로 나누면 작업지시를 그 수만큼 만듬
  - 각 지시 수량 합은 수주 품목 수량과 같음

### 6.5 작업지시 목록 조회

- Method:`GET`
- URI:`/api/v1/work-orders`
- 인증 필요 여부: 필수
  - 권한: 로그인 사용자
- 처리 규칙
  - `assignedId`가 있으면 그 담당자 작업만 고른 뒤 페이지를 자름
  - 검색 조건이 있으면 포장 완료가 뒤이고, 완료 예정일이 빠른 순, 같은 날이면 등록이 최신 순으로 출력
  - 한 행은 작업지시 하나이고, 품목은 하나
  - `page`는 0부터,`size`기본값은 5
- Request Body: 없음
- Query
  - `orderId`: 수주 ID, Long
  - `keyword`: 수주번호, 품목명, 거래처명, 담당자명, String
  - `from`,`to`: 지시일, LocalDate
  - `status`: 작업 상태 코드, String.`PREPARE`,`PRODUCE`,`SUSPEND`,`HALT`,`PACK`,`PACK_DONE`
  - `lineId`: 라인 ID, Long
  - `assignedId`: 담당자 ID, Long
  - `page`: 페이지, int, 기본 0
  - `size`: 크기, int, 기본 5
- Response:`200 OK`, 작업지시 목록

```json
{
  "content": [
    {
      "id": 11,
      "workNo": "WO-2026-011",
      "orderNo": "PF-2026-011",
      "clientName": "모빌셀",
      "productName": "클리어 케이스 iPhone 16",
      "quantity": 1000,
      "lineName": "4라인",
      "assignedName": "이재원",
      "workOrderDate": "2026-10-27",
      "plannedEndDate": "2026-10-31",
      "status": "PREPARE",
      "orderProductId": 21,
      "plannedStartDate": "2026-10-27",
      "completedAt": null
    }
  ],
  "totalElements": 12,
  "totalPages": 3,
  "size": 5,
  "number": 0
}
```

### 6.5.1 작업지시 상세 조회

- Method:`GET`
- URI:`/api/v1/work-orders/{workOrderId}`
- 인증 필요 여부: 필수
  - 권한: 로그인 사용자
- 처리 규칙
  - 목록 항목에 라인 id, 담당자 id, 메모, 수주 납기, 날짜별 계획 수량을 포함한다.
  - 담당자가 없으면`assignedId`,`assignedName`은`null`이다.
  - 해당 작업지시서의 일일 생산 계획도 포함된 구조
- Request Body: 없음
- Response:`200 OK`

```json
{
  "id": 11,
  "workNo": "WO-2026-011",
  "orderNo": "PF-2026-011",
  "clientName": "모빌셀",
  "productName": "클리어 케이스 iPhone 16",
  "quantity": 1000,
  "lineId": 4,
  "lineName": "4라인",
  "assignedId": 5,
  "assignedName": "이재원",
  "workOrderDate": "2026-10-27",
  "plannedStartDate": "2026-10-27",
  "plannedEndDate": "2026-10-31",
  "completedAt": null,
  "status": "PREPARE",
  "note": "클리어 케이스 iPhone 16 4라인 2026-10-27 ~ 2026-10-31",
  "estimatedDueDate": "2026-11-04",
  "plans": [
    {
      "id": 30,
      "workOrderId": 11,
      "planDate": "2026-10-27",
      "quantity": 400
    },
    {
      "id": 31,
      "workOrderId": 11,
      "planDate": "2026-10-28",
      "quantity": 600
    }
  ]
}

```

- workNo:`작업 번호`
- orderNo: `수주 번호`
- clientName: `거래처명`
- productName: `품목명`
- estimatedDueDate: `납기일`
- workOrderDate: `지시일`
- assignedId, assignedName: `담당자` , 없으면 `null`
- plannedStartDate, plannedEndDate: 생산 시작·완료 예정일
- quantity, productName:`orderProduct`와`quantity`. 품목은 하나
- status:`commonCode`
- line, user:`lineId`,`userId` ,`lineName`
- note: `작업 내용`
- plans:  `날짜별 계획 수량`

### 6.6 라인·담당자 수정

- Method:`PATCH`
- URI:`/api/v1/work-orders/{workOrderId}`
- 인증 필요 여부: 필수
  - 호출 권한:`SUPER_ADMIN`,`ORDER_MANAGER`,`PRODUCTION_MANAGER`
  - 라인 변경:`SUPER_ADMIN`,`PRODUCTION_MANAGER`
  - 담당자 변경:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙
  - `lineId`와`assignedId`중 바꿀 항목만 보낸다. 둘 다 보낼 수도 있다.
  - 작업지시를 지우거나 다시 만들지 않는다.
  - 생산·일시정지는 다른 라인으로 옮기지 않는다. 같은 라인 id를 다시 보내는 것은 허용한다.
  - 다른 라인은 가동 가능 라인만 받는다. 일반 이동은 기존 날짜가 도착 라인에 들어가는지만 확인한다.
  - 고장·점검으로 빠졌던 라인으로 되돌릴 때, 원래 시작일부터 현재 시작일 전이 비어 있으면 그 날부터 일정을 당긴다.
  - 담당자는 사용 중인 생산 현장 관리자다.
  - 이력에는 변경 전·후 라인과 담당자를 남긴다. 바꾸지 않은 항목은 이전 값과 같은 값을 저장한다. 첫 배정도 이전 라인을 비우지 않는다.
- Request Body: 필수, `WorkOrderDto.UpdatedRequest`

```json
{
  "lineId": 4,
  "assignedId": 8
}
```

- lineId: 생산라인 ID, Long.`line_id`  생략하면 라인을 바꾸지 않음
- userId: 담당자 ID, Long.`user_id`  생략하면 담당자를 바꾸지 않음
- Response:`200 OK`, 작업지시 상세
- 작업지시가 없으면`404`, 라인·담당자 규칙 위반은`400`, 역할이 아니면`403`

### 6.7 생산 상태 변경

- Method:`PATCH`
- URI:`/api/v1/work-orders/{workOrderId}/status`
- 인증 필요 여부: 필수
  - 권한: 해당 작업지시 담당자,`SUPER_ADMIN`,`PRODUCTION_MANAGER`
- 처리 규칙
  - `changeStatus`로 작업 상태 코드를 바꾼다. 허용된 전이만 가능함
    - `PREPARE`→`PRODUCE`,`HALT`
    - `PRODUCE`→`SUSPEND`,`HALT`,`PACK`
    - `SUSPEND`→`PRODUCE`,`HALT`
    - `HALT`→`PREPARE`
    - `PACK`→`PACK_DONE`
  - 생산 시작은 라인을 가동 중으로, 수주를 생산중으로 바꿈.
  - 담당자가 있어야 하고 시작 예정일 이후여야 하며, 대기 수주와 고장·점검 라인에서는 시작하지 않음 또한 그 라인에 다른 생산 중 작업이 있으면 시작하지 못함
  - 실제 생산량으로 완료를 검증하지 않으며 포장으로 바꾸면`completedAt`에 완료일을 남김
  - 같은 수주의 다른 작업이 모두 포장 이후이면 수주는 포장중이 되고, 출고·배송은 이 API에서 바꾸지 않음
  - 일시정지는 같은 수주에 다른 생산이 없으면 수주를 생산보류로 바꿈
  - 생산 중지는 취소된 수주가 아니면 수주를 생산대기로 바꿈 사유는 상태 이력에 남김
  - 변경 내용은`status_history`에 남김
  - 생산 시작은 그날 계획 수량이나 라인 점유 합이 하루 생산능력을 넘으면 거절
  - 포장으로 바꾸면, 그 라인에 다른 생산이 없을 때 라인을 가동 가능으로 바꿈
  - 생산 중지 뒤 준비로 돌아오면 라인을 가동 가능으로 바꿈
  - 취소된 수주는 준비로 돌아오지 않음
- Request Body: 필수, `WorkOrderDto.UpdatedStatusRequest`

```json
{
  "status": "PRODUCE",
  "note": null
}
```

- `status`: 작업 상태 코드, String.`PREPARE`,`PRODUCE`,`SUSPEND`,`HALT`,`PACK`,`PACK_DONE`
- `note`: 생산 중지 사유, String. 생략할 수 있음
- Response:`200 OK`,`WorkOrderDto.DetailResponse`
- 작업지시가 없으면`404`, 허용되지 않은 전이는`400`, 담당자가 아니면`403`

### 6.8 **작업지시 상태 변경 이력 조회**

- Method:`GET`
- URI:`/api/v1/work-orders/{workOrderId}/status-history`
- 인증 필요 여부: 필수
  - 권한: 로그인 사용자
- 처리 규칙
  - 해당 작업지시의 상태 변경 이력을 변경 시각 최신순으로 조회
  - 이력이 없거나 작업지시가 없으면 빈 배열
- Request Body: 없음
- Response:`200 OK`,`List<StatusHistoryDto.Response>`

```json
[
  {
    "id": 41,
    "entity": "WORK_ORDER",
    "entityId": 11,
    "fromStatus": "PREPARE",
    "toStatus": "PRODUCE",
    "userName": "이재원",
    "note": null,
    "createdAt": "2026-10-27T09:00:00"
  }
]
```

- fromStatus, toStatus : 변경 전·후 작업 상태 코드
- userName: 변경한 직원
- note: 생산 중지 사유. 없으면`null`
- createdAt: 변경 시각

### 6.9 작업지시 라인·담당자 변경 이력 조회

- Method:`GET`
- URI:`/api/v1/work-orders/{workOrderId}/history`
- 인증 필요 여부: 필수
  - 권한: 로그인 사용자
- 처리 규칙
  - 라인·담당자 변경 이력을 최근 5건, 변경 시각 최신순으로 조회
  - 바꾸지 않은 라인·담당자는 이전 값과 같은 값이 들어 있음.
  - 담당자가 없던 변경은 담당자명이`null`
  - 이력이 없거나 작업지시가 없으면 빈 배열임
- Request Body: 없음
- Response:`200 OK`,`List<WorkOrderHistoryDto.Response>`

```json
[
  {
    "previousLineName": "2라인",
    "newLineName": "4라인",
    "previousUserName": "이재원",
    "newUserName": "이재원",
    "changedByName": "김태호"
  }
]
```

### 6.10 생산 일정

- Method: `GET`
- URI: `/api/v1/production/calender`
- 인증 필요 여부: 필수
  - 권한: 로그인 사용자
- 처리 규칙
  - 수주 확정 상태(`CONFIRMED`)인 작업지시만 조회
  - 작업지시 상태가 `PREPARE`, `PRODUCE`, `PACK`, `SUSPEND`인 데이터만 조회
  - 생산라인별 작업지시 일정을 조회
  - 생산라인 ID 및 작업지시의 예정 시작일(`plannedStartDate`)을 기준으로 정렬
  - 작업지시별 생산라인 정보, 작업지시 정보, 수주 정보 및 제품 정보를 반환
- Request Body: 없음
- Response: `200 OK`

```json
[
  {
    "lineId": 4,
    "lineName": "4라인",
    "workOrderId": 11,
    "workNo": "WO-202610-011-b7d3",
    "plannedStartDate": "2026-10-27",
    "plannedEndDate": "2026-10-27",
    "status": "PRODUCE",
    "orderNo": "PF-2026-011",
    "productName": "블랙 케이스",
    "quantity": 500
  },
  {
    "lineId": 4,
    "lineName": "4라인",
    "workOrderId": 12,
    "workNo": "WO-202610-012-c8e4",
    "plannedStartDate": "2026-10-28",
    "plannedEndDate": "2026-10-29",
    "status": "PREPARE",
    "orderNo": "PF-2026-012",
    "productName": "투명 케이스",
    "quantity": 300
  }
]
```

## **7. 입금**

입금 기능은 계약금과 잔금을 관리하며, 현금 수동 입금과 PortOne 가상계좌 결제를 지원한다.

수주 등록 시 계약금 결제 건을 생성하고, 출고 처리 과정에서 잔금 결제 건을 생성한다. 가상계좌 발급은 PortOne과 연동하며 실제 입금 완료 여부는 웹훅 또는 결제 상태 재동기화로 확인한다.

**공통 결제 규칙**

- `DEPOSIT`: 계약금. 수주 총액의 50%를 원 단위 내림으로 계산한다.
- `BALANCE`: 잔금. 총액에서 계약금을 제외한 금액이다.
- 같은 수주에 동일한`paymentType`의 결제 건은 중복 생성하지 않는다.
- 결제 건의 최초 상태는`READY`이다.
- 결제 수단은`VA`(가상계좌),`MD`(수동 입금)로 구분한다.
- 가상계좌 발급 은행과 만료 기간은 서버 설정을 사용한다.
- 입금 완료 시 결제 상태는`PAID`, 가상계좌 상태는`USED`가 된다.

### **7.1 결제 건 생성 — 내부 자동 처리**

- Method: 없음 (내부 자동 처리)
- URI: 별도 API 없음
- 인증 필요 여부: 해당 없음
- 처리 규칙
  - 기존에 작성한`POST /api/v1/orders/{orderId}/payments`는 현재`PaymentController`에 존재하지 않는다.
  - 결제 건은 수주 등록 및 출고 처리 과정에서 내부 서비스가 자동으로 생성한다.
  - `PaymentService.prepareAndScheduleIssue()`에서 결제 건을 생성한다.
  - 잔금 결제는 계약금 결제가`PAID`인 경우에만 생성할 수 있다.
  - 동일 수주와 결제 유형의 기존 결제 건이 있으면 중복 생성하지 않는다.
  - 결제 건의 최초 상태는`READY`이다.
  - 수주 및 결제 정보가 DB에 커밋된 뒤 PortOne 가상계좌 발급을 시도한다.
  - 발급 결과가 불확실하면 결제 상태 및 로그를 통해 확인한다.
- 생성 시점

| **결제 구분** | **생성 시점** | **금액**           |
|-----------|-----------|------------------|
| `DEPOSIT` | 수주 등록 과정  | 총액의 50%, 원 단위 내림 |
| `BALANCE` | 출고 처리 과정  | 총액 - 계약금         |

### **7.2 현금 계약금 수동 입금**

- Method:`POST`
- URI:`/api/v1/payments/deposit/manual`
- Path Parameters: 없음
- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙
  - 직원이 현금으로 받은 계약금을 직접 확인하여 입금 완료 처리한다.
  - `WAITING`수주만 계약금을 처리할 수 있다.
  - 입금 금액은 계약금 예정액과 정확히 일치해야 한다.
  - 미래 시각으로 입금 일시를 지정할 수 없다.
  - 기존 계약금 결제 건이 존재하면`READY`상태여야 한다.
  - 기존 입금 이력이 있거나 활성 가상계좌가 있으면 중복 입금을 방지한다.
  - 수동 입금 시`payment.status=PAID`,`payment.payment_method=MD`로 처리한다.
  - `payment_history`에 입금 일시를 기록한다.
  - 수주를`CONFIRMED`상태로 변경하고 작업지시 및 생산계획을 생성한다.
  - 모든 DB 변경과 작업 배정은 하나의 트랜잭션으로 처리한다.
  - PortOne API는 호출하지 않는다.
- Request Body: 필수

```json
{
  "orderId": 10,
  "amount": 6000000,
  "paidAt": "2026-11-11T15:00:00+09:00"
}
```

| **필드**    | **타입**         | **필수** | **설명**               |
|-----------|----------------|--------|----------------------|
| `orderId` | Long           | O      | 수주 ID, 0보다 큰 값       |
| `amount`  | Long           | O      | 입금 금액, 0보다 큰 값       |
| `paidAt`  | OffsetDateTime | X      | 입금 일시. 생략 시 현재 한국 시간 |

- Response:`200 OK`

```json
{
  "paymentId": 70,
  "orderId": 10,
  "amount": 6000000,
  "paymentMethod": "MD",
  "paidAt": "2026-11-11T15:00:00+09:00",
  "orderStatus": "CONFIRMED",
  "confirmationStatus": "CONFIRMED",
  "paymentStatus": "PARTIALLY_PAID",
  "workOrderIds": [
    101,
    102
  ]
}
```

- 응답 필드

| **필드**               | **타입**             | **설명**             |
|----------------------|--------------------|--------------------|
| `paymentId`          | Long               | 결제 ID              |
| `orderId`            | Long               | 수주 ID              |
| `amount`             | Long               | 입금 처리 금액           |
| `paymentMethod`      | PaymentMethod      | 결제 수단. 수동 입금은 `MD` |
| `paidAt`             | OffsetDateTime     | 실제 입금 일시           |
| `orderStatus`        | String             | 처리 후 수주 진행 상태      |
| `confirmationStatus` | ConfirmationStatus | 수주 확정 상태           |
| `paymentStatus`      | OrderPaymentStatus | 수주 결제 상태           |
| `workOrderIds`       | List               | 생성된 작업지시 ID 목록     |

`confirmationStatus`는 실제 수주 엔티티의 현재 값이 반환된다.

- 주요 오류

| **HTTP 상태** | **설명**                             |
|-------------|------------------------------------|
| `400`       | 금액 불일치, 미래 입금 일시 등 입력 오류           |
| `401`       | 인증 실패                              |
| `403`       | 처리 권한 없음                           |
| `404`       | 수주가 존재하지 않음                        |
| `409`       | 이미 처리된 계약금, 수주 상태 충돌, 활성 가상계좌 존재 등 |

### **7.3 가상계좌 발급**

- Method:`POST`
- URI:`/api/v1/payments/{paymentId}/issue`
- Path Parameters

| **파라미터**    | **타입** | **필수** | **설명** |
|-------------|--------|--------|--------|
| `paymentId` | Long   | O      | 결제 ID  |

- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙
  - 이미 생성된 결제 건을 대상으로 PortOne 가상계좌 발급을 시도하거나 기존 발급 결과를 확인한다.
  - `READY`결제에 최초 발급을 요청한다.
  - 발급 요청 시`REQUESTED`상태를 저장한다.
  - PortOne 발급이 확인되면 결제는`WAITING`, 가상계좌는`ACTIVE`가 된다.
  - 이미`WAITING`,`PAID`인 결제는 중복 발급하지 않는다.
  - `REQUESTED`,`ISSUE_UNCERTAIN`은 중복 발급 POST 대신 결과 확인을 수행한다.
  - 발급 결과를 확인하지 못하면`ISSUE_UNCERTAIN`으로 관리할 수 있다.
  - 취소 중이거나 취소된 수주는 발급할 수 없다.
  - PortOne 상태 조회 결과에 따라 발급 실패가 확인되면`FAILED`가 될 수 있다.
- 기본 설정

| **설정**                             | **기본값**   | **설명**      |
|------------------------------------|-----------|-------------|
| `payment.virtual-account-bank`     | `SHINHAN` | 발급 은행       |
| `payment.virtual-account-due-days` | `14`      | 입금 가능 기간(일) |

- Request Body: 없음
- 요청 예시

```
POST /api/v1/payments/70/issueAuthorization: Bearer <accessToken>
```

요청 Body는 사용하지 않는다.

- Response:`200 OK`

```json
{
  "paymentId": 70,
  "orderId": 10,
  "orderNo": "ORD-2026-0010",
  "paymentType": "DEPOSIT",
  "amount": 6000000,
  "status": "WAITING",
  "paymentMethod": "VA",
  "virtualAccount": {
    "virtualAccountId": 30,
    "paymentId": 70,
    "bank": "SHINHAN",
    "accountNumber": "12345678901234",
    "accountName": "프로드플로우",
    "expiresAt": "2026-11-25T15:00:00+09:00",
    "status": "ACTIVE"
  }
}
```

- 응답 필드 —`PaymentDto.IssueResponse`

| **필드**           | **타입**                     | **설명**               |
|------------------|----------------------------|----------------------|
| `paymentId`      | Long                       | 결제 ID                |
| `orderId`        | Long                       | 수주 ID                |
| `orderNo`        | String                     | 수주번호                 |
| `paymentType`    | PaymentType                | `DEPOSIT`, `BALANCE` |
| `amount`         | Long                       | 결제 예정 금액             |
| `status`         | PaymentStatus              | 결제 상태                |
| `paymentMethod`  | PaymentMethod              | 결제 수단                |
| `virtualAccount` | VirtualAccountDto.Response | 발급된 가상계좌 정보          |

- 가상계좌 응답 필드 —`VirtualAccountDto.Response`

| **필드**             | **타입**               | **설명**      |
|--------------------|----------------------|-------------|
| `virtualAccountId` | Long                 | 가상계좌 ID     |
| `paymentId`        | Long                 | 연결된 결제 ID   |
| `bank`             | Bank                 | 은행 Enum 코드  |
| `accountNumber`    | String               | 계좌번호        |
| `accountName`      | String               | 예금주         |
| `expiresAt`        | OffsetDateTime       | 가상계좌 만료 일시  |
| `status`           | VirtualAccountStatus | 가상계좌의 실효 상태 |

**참고:**가상계좌 응답에는`bankCode`,`portonePaymentId`필드가 포함되지 않는다.

### **7.4 PortOne 웹훅**

- Method:`POST`
- URI:`/api/v1/payments/webhook`
- Path Parameters: 없음
- 인증 필요 여부: 불필요
  - 요청 검증: PortOne 웹훅 서명
- 처리 규칙
  - PortOne의 결제 상태 변경 알림을 받아 실제 결제 정보를 다시 조회하고 DB에 반영한다.
  - **처리 및 검증 규칙**
  - 웹훅 서명을 HMAC-SHA256 방식으로 검증한다.
  - 웹훅 시각과 서버 시각의 차이가 300초를 초과하면 거부한다.
  - PortOne 결제 조회 API를 호출하여 실제 결제 상태를 재검증한다.
  - 결제 ID, 상점 ID, 금액, 통화, 채널, PG사 정보를 확인한다.
  - 현재 구현에서는`KRW`,`TOSSPAYMENTS`등을 검증한다.
  - 지원하지 않는 이벤트나 등록되지 않은 결제의 웹훅은 무시할 수 있다.
  - **입금 완료 처리**
  - 결제 상태를`PAID`로 변경한다.
  - 가상계좌를`USED`로 변경한다.
  - `payment_history`에 거래 ID와 입금 시각을 저장한다.
  - 계약금 입금이면 수주를`CONFIRMED`로 변경하고 작업지시를 생성한다.
  - 잔금 입금이면 수주 결제 상태를 전액 입금 완료로 변경한다.
  - 중복 웹훅으로 입금 내역이나 작업지시가 중복 생성되지 않도록 처리한다.
  - 오래되거나 역순으로 수신된 웹훅이 확정된 상태를 되돌리지 않도록 제한한다.
- 지원 이벤트
  - `Transaction.Ready`
  - `Transaction.Paid`
  - `Transaction.VirtualAccountIssued`
  - `Transaction.PartialCancelled`
  - `Transaction.Cancelled`
  - `Transaction.Failed`
  - `Transaction.PayPending`
  - `Transaction.CancelPending`
- Request Headers

| **Header**          | **필수** | **설명** |
|---------------------|--------|--------|
| `webhook-id`        | O      | 웹훅 식별자 |
| `webhook-timestamp` | O      | 웹훅 시각  |
| `webhook-signature` | O      | 웹훅 서명  |

- Request Body: 필수 (PortOne 웹훅 원문)

PortOne에서 전송하는 웹훅 JSON 원문을 사용한다.

- Response:`204 No Content`

정상 처리 시 응답 Body는 없다.

- 주요 오류

| **HTTP 상태** | **설명**            |
|-------------|-------------------|
| `400`       | 웹훅 Body 형식 오류     |
| `401`       | 웹훅 서명 누락·오류·시간 초과 |
| `503`       | 웹훅 서명 검증 키 미설정    |

결제 정합성 검증 실패 시에는 해당 예외에 따른 추가 오류 응답이 발생할 수 있다.

### **7.5 가상계좌 재발급**

- Method:`POST`
- URI:`/api/v1/payments/{paymentId}/reissue`
- Path Parameters: 없음
- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙
  - 기존 가상계좌의 PortOne 말소를 확인한 후 새로운 가상계좌를 발급한다.
  - **재발급 조건**
  - 가상계좌 결제 수단인`VA`여야 한다.
  - 기존 가상계좌가 존재해야 한다.
  - 입금 이력이 없어야 한다.
  - 기존 계좌가`USED`상태이면 재발급할 수 없다.
  - 수주가 취소된 상태이면 안 된다.
  - `WAITING`,`CANCELLED`,`FAILED`상태에서 신규 재발급을 시작할 수 있다.
  - `REISSUE_CLOSING`상태라면 진행 중인 재발급 작업을 이어서 확인한다.
  - **처리 규칙**

  1. 결제 및 기존 가상계좌 상태를 확인한다.
  2. 재발급 의도를 저장하고`REISSUE_CLOSING`으로 전환한다.
  3. PortOne에서 기존 결제 상태를 조회한다.
  4. 필요하면 기존 가상계좌 말소를 요청한다.
  5. 말소 완료를 확인한 후 기존 계좌를`CANCELLED`로 변경한다.
  6. 새 PortOne 결제 ID로 전환한다.
  7. 신규 가상계좌를 발급한다.
  8. 기존 계좌는 삭제하지 않고 이력으로 보존한다.

  - **검증 및 제한**
  - 새 만료일은 서버 설정을 기준으로 계산한다.
  - 클라이언트에서 만료일을 전달하지 않는다.
  - 재발급 진행 중에는 최초 확정된 만료일을 유지한다.
  - 기존 계좌 말소 결과가 불확실하면 신규 계좌를 발급하지 않는다.
  - 중복 말소 및 중복 발급을 방지한다.
  - 입금 완료 또는 재발급 불가능한 상태는`409 Conflict`가 발생할 수 있다.
- Request Body: 없음
- 요청 예시

```
POST /api/v1/payments/70/reissueAuthorization: Bearer <accessToken>
```

요청 Body는 사용하지 않는다.

- Response:`200 OK`

`PaymentDto.IssueResponse`를 반환하며, 응답 필드는 7.3과 동일하다.

### **7.6 입금 목록 및 검색**

- Method:`GET`
- URI:`/api/v1/payments`
- Path Parameters: 없음
- 인증 필요 여부: 필수
  - 권한: 로그인 사용자
- 처리 규칙
  - 입금관리 화면에서 결제 목록을 검색하고 페이징하여 조회한다.
  - 수주번호와 거래처명은 부분 일치 검색한다.
  - 검색어는 앞뒤 공백 제거 및 소문자 변환 후 검색한다.
  - 조회 기간은 결제 건 생성일(`createdAt`) 기준이다.
  - 시작일과 종료일을 모두 포함한다.
  - 시작일이 종료일보다 늦으면`400 Bad Request`.
  - 가장 최근 가상계좌 정보를 사용한다.
  - 가상계좌가 없는 경우 은행, 계좌번호, 입금기한은`null`이다.
  - 입금 완료일은`payment_history.paidAt`을 한국 시간으로 변환한다.

현재 목록 검색에서는`clientId`,`unpaidOnly`,`virtualAccountStatus`,`from`,`to`를 지원하지 않는다.

`orderId`를 전달하면 일반 목록이 아니라 7.10 수주별 결제 조회가 실행된다.

- Query Parameters

| **파라미터**        | **타입**        | **필수** | **기본값**   | **설명**               |
|-----------------|---------------|--------|-----------|----------------------|
| `orderNumber`   | String        | X      | 없음        | 수주번호 포함 검색           |
| `clientName`    | String        | X      | 없음        | 거래처명 포함 검색           |
| `paymentType`   | PaymentType   | X      | 전체        | `DEPOSIT`, `BALANCE` |
| `paymentStatus` | PaymentStatus | X      | 전체        | 결제 상태                |
| `startDate`     | LocalDate     | X      | 없음        | 결제 생성일 시작            |
| `endDate`       | LocalDate     | X      | 없음        | 결제 생성일 종료            |
| `page`          | Integer       | X      | 0         | 페이지 번호               |
| `size`          | Integer       | X      | 10        | 페이지 크기               |
| `sort`          | String        | X      | `id,desc` | 정렬 조건                |

- Request Body: 없음
- 요청 예시

전체 조회:

```
GET /api/v1/payments?page=0&size=10
```

수주번호 검색:

```
GET /api/v1/payments?orderNumber=ORD-2026&page=0&size=10
```

계약금 조회:

```
GET /api/v1/payments?paymentType=DEPOSIT
```

입금 대기 조회:

```
GET /api/v1/payments?paymentStatus=WAITING
```

기간 조회:

```
GET /api/v1/payments?startDate=2026-11-01&endDate=2026-11-30
```

- Response:`200 OK`

반환 타입은`Page<PaymentDto.ListResponse>`이다.

**`content`에 포함되는 한 건의 예시**

```json
{
  "paymentId": 70,
  "orderNumber": "ORD-2026-0010",
  "clientName": "싱싱한오렌지",
  "paymentType": "DEPOSIT",
  "scheduledAmount": 6000000,
  "paymentStatus": "WAITING",
  "paymentMethod": "VA",
  "bankName": "SHINHAN",
  "accountNumber": "12345678901234",
  "expiresAt": "2026-11-25T15:00:00",
  "receivedAt": null
}
```

- 목록 항목 응답 필드 —`PaymentDto.ListResponse`

| **필드**            | **타입**        | **설명**         |
|-------------------|---------------|----------------|
| `paymentId`       | Long          | 결제 ID          |
| `orderNumber`     | String        | 수주번호           |
| `clientName`      | String        | 거래처명           |
| `paymentType`     | PaymentType   | 계약금 또는 잔금      |
| `scheduledAmount` | Long          | 결제 예정 금액       |
| `paymentStatus`   | PaymentStatus | 결제 상태          |
| `paymentMethod`   | PaymentMethod | 결제 수단          |
| `bankName`        | String        | 가상계좌 은행 표시 문자열 |
| `accountNumber`   | String        | 가상계좌 번호        |
| `expiresAt`       | LocalDateTime | 입금기한, 한국 시간    |
| `receivedAt`      | LocalDateTime | 입금 완료일, 한국 시간  |

전체 응답에는 Spring Data`Page`의`content`,`totalElements`,`totalPages`, 페이지 번호 및 크기 등의 메타데이터가 포함된다.

**주의:**사용자 목록 API와 달리 별도의`PageResponse`로 감싸지 않는다. 따라서 페이지 번호는 사용 중인 Spring Data 직렬화 형식에 따라`number`등의 필드로 표현된다.

### **7.7 계약금 가상계좌 안내 이메일 발송**

- Method:`POST`
- URI:`/api/v1/payments/orders/{orderId}/notifications`
- Path Parameters

| **파라미터**  | **타입** | **필수** | **설명**   |
|-----------|--------|--------|----------|
| `orderId` | Long   | O      | 대상 수주 ID |

- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙
  - 수주 내용을 재검증하고 계약금 가상계좌를 준비한 뒤 거래처 이메일로 결제 안내를 발송한다.
  - 수주는`WAITING`상태여야 한다.
  - 계약금 마감일이 지나지 않아야 한다.
  - 요청한 거래처 이메일, 수주 금액, 부가세, 품목 및 계약금 마감일을 실제 수주 정보와 비교한다.
  - 수주 정보가 달라졌으면 발송하지 않는다.
  - 계약금 결제 건이 없으면 생성 및 가상계좌 발급을 준비한다.
  - 기존 유효한 계좌가 있으면 재사용한다.
  - 동일한`requestId`요청은 기존 결과를 반환하여 중복 발송을 방지한다.
  - 이미 발송 중이거나 전송이 완료된 수주에 새 발송 요청이 들어오면 제한한다.
  - 이메일 실패 후 새로운 요청은 기존 유효한 가상계좌를 재사용한다.
  - 발송 결과를`payment_notification_request`에 저장한다.
  - 이메일 발송 이력을`client_noti_log`에 기록한다.

**중요:**이메일 발송 실패나 계좌 확인 실패가 발생해도 서비스가`FAILED`또는`ISSUE_UNCERTAIN`응답 객체를 반환하므로 HTTP 응답은`200 OK`일 수 있다. 클라이언트는 HTTP 상태뿐 아니라
반드시`status`필드를 확인해야 한다.

- 발송 상태

| **상태**            | **설명**           |
|-------------------|------------------|
| `PROCESSING`      | 가상계좌 및 안내 준비 중   |
| `SENDING`         | 이메일 발송 중         |
| `SENT`            | 이메일 발송 성공        |
| `FAILED`          | 이메일 발송 실패        |
| `ISSUE_UNCERTAIN` | 가상계좌 발급 결과 확인 필요 |

- Request Body: 필수

```json
{
  "requestId": "123e4567-e89b-42d3-a456-426614174000",
  "recipient": "client@example.com",
  "totalAmount": 11000000,
  "supplyAmount": 10000000,
  "vatAmount": 1000000,
  "depositAmount": 5500000,
  "vatIncluded": false,
  "depositDueDate": "2026-11-30",
  "items": [
    {
      "id": 101,
      "productName": "오렌지 포장 제품",
      "quantity": 1,
      "unitPrice": 10000000,
      "supplyAmount": 10000000
    }
  ]
}
```

- 요청 필드 —`PaymentNotificationDto.Request`

| **필드**           | **타입**    | **필수** | **검증 조건 및 설명**    |
|------------------|-----------|--------|-------------------|
| `requestId`      | String    | O      | UUID 문자열 형식       |
| `recipient`      | String    | O      | 유효한 이메일, 최대 100자  |
| `totalAmount`    | Long      | O      | 수주 총액, 0보다 커야 함   |
| `supplyAmount`   | Long      | O      | 공급가액 합계, 0 이상     |
| `vatAmount`      | Long      | O      | 부가세, 0 이상         |
| `depositAmount`  | Long      | O      | 계약금 청구액, 0보다 커야 함 |
| `vatIncluded`    | Boolean   | O      | 단가 부가세 포함 여부      |
| `depositDueDate` | LocalDate | O      | 계약금 마감일           |
| `items`          | List      | O      | 비어 있지 않은 품목 목록    |

- 품목 필드 —`PaymentNotificationDto.Item`

| **필드**         | **타입**  | **필수** | **검증 조건**       |
|----------------|---------|--------|-----------------|
| `id`           | Long    | O      | 품목 ID, 0보다 커야 함 |
| `productName`  | String  | O      | 비어 있지 않은 품목명    |
| `quantity`     | Integer | O      | 수량, 0보다 커야 함    |
| `unitPrice`    | Long    | O      | 단가, 0보다 커야 함    |
| `supplyAmount` | Long    | O      | 공급가액, 0 이상      |

- Response:`200 OK`

**이메일 발송 성공**

```json
{
  "requestId": "123e4567-e89b-42d3-a456-426614174000",
  "status": "SENT",
  "message": "가상계좌 및 결제정보 이메일을 전송했습니다."
}
```

**이메일 발송 실패**

```json
{
  "requestId": "123e4567-e89b-42d3-a456-426614174000",
  "status": "FAILED",
  "message": "가상계좌는 유지되었으나 이메일 전송에 실패했습니다. 다시 발송하면 기존 계좌를 사용합니다."
}
```

**가상계좌 상태 확인 실패**

```json
{
  "requestId": "123e4567-e89b-42d3-a456-426614174000",
  "status": "ISSUE_UNCERTAIN",
  "message": "계좌 또는 최신 수주 상태를 확인하지 못했습니다. 결제 동기화 및 수주 확인 후 다시 시도해주세요."
}
```

- 응답 필드 —`PaymentNotificationDto.Response`

| **필드**      | **타입**                    | **설명**       |
|-------------|---------------------------|--------------|
| `requestId` | String                    | 요청 식별자       |
| `status`    | PaymentNotificationStatus | 발송 처리 상태     |
| `message`   | String                    | 처리 결과 안내 메시지 |

- 주요 오류

| **HTTP 상태** | **설명**                            |
|-------------|-----------------------------------|
| `400`       | 필수 요청값 누락, UUID 형식 오류, 금액 검증 오류 등 |
| `403`       | 발송 권한 없음                          |
| `404`       | 수주가 존재하지 않음                       |
| `409`       | 수주 정보 불일치, 중복 발송, 마감일 경과 등        |

### **7.8 입금 통계 조회**

- Method:`GET`
- URI:`/api/v1/payments/summary`
- Path Parameters: 없음
- 인증 필요 여부: 필수
  - 권한: 로그인 사용자
- 처리 규칙
  - 전체 결제 예정 금액, 입금 대기 금액, 입금 완료 금액, 미수 금액을 조회한다.
  - 전체 예정액은 취소되지 않은 수주의 총액을 기준으로 한다.
  - 결제 건이 아직 생성되지 않은 잔금도 전체 예정액에 포함한다.
  - `PAID`결제를 입금 완료 금액으로 합산한다.
  - `WAITING`결제를 입금 대기 금액으로 합산한다.
  - 수동 입금`MD`도 완료 금액에 포함한다.
  - 미수 금액은 음수가 되지 않도록 계산한다.

계산식:

`unpaidAmount = max(0, totalScheduledAmount - completedAmount - waitingAmount)`

- Request Body: 없음
- Response:`200 OK`

```json
{
  "totalScheduledAmount": 30000000,
  "waitingAmount": 10000000,
  "completedAmount": 15000000,
  "unpaidAmount": 5000000
}
```

- 응답 필드 —`PaymentDto.SummaryResponse`

| **필드**                 | **타입** | **설명**      |
|------------------------|--------|-------------|
| `totalScheduledAmount` | Long   | 전체 결제 예정 금액 |
| `waitingAmount`        | Long   | 입금 대기 금액    |
| `completedAmount`      | Long   | 입금 완료 금액    |
| `unpaidAmount`         | Long   | 미수 금액       |

### **7.9 입금 상세 조회**

- Method:`GET`
- URI:`/api/v1/payments/{paymentId}`
- Path Parameters

| **파라미터**    | **타입** | **필수** | **설명** |
|-------------|--------|--------|--------|
| `paymentId` | Long   | O      | 결제 ID  |

- 인증 필요 여부: 필수
  - 권한: 로그인 사용자
- 처리 규칙
  - 특정 결제 건의 수주 정보, 결제 정보, 최신 가상계좌, 입금 내역을 조회한다.
  - 결제가 없으면`404 Not Found`.
  - 가상계좌가 없으면`virtualAccount=null`.
  - 입금 이력이 없으면`paymentHistory=null`.
  - 부분 입금을 지원하지 않으므로 결제 상태가`PAID`이면 예정 금액 전체를`actualPaidAmount`로 반환한다.
  - `PAID`가 아니면`actualPaidAmount=0`이다.
  - 수동 입금 건의`portonePaymentId`는`null`이다.
  - `reissueAvailable`은 DB 정보 기준의 사전 판정 결과이다.
  - 실제 재발급 시에는 PortOne 상태를 다시 검증한다.
- Request Body: 없음
- Response:`200 OK`

```json
{
  "order": {
    "orderId": 10,
    "orderNumber": "ORD-2026-0010",
    "clientName": "싱싱한오렌지",
    "totalAmount": 12000000,
    "orderStatus": "CONFIRMED"
  },
  "payment": {
    "paymentId": 70,
    "paymentType": "DEPOSIT",
    "scheduledAmount": 6000000,
    "status": "PAID",
    "paymentMethod": "VA",
    "createdAt": "2026-11-11T10:00:00",
    "actualPaidAmount": 6000000,
    "portonePaymentId": "PF1234567890"
  },
  "virtualAccount": {
    "virtualAccountId": 30,
    "paymentId": 70,
    "bank": "SHINHAN",
    "accountNumber": "12345678901234",
    "accountName": "프로드플로우",
    "expiresAt": "2026-11-25T15:00:00+09:00",
    "status": "USED"
  },
  "paymentHistory": {
    "paymentHistoryId": 45,
    "paymentId": 70,
    "virtualAccountId": 30,
    "transactionId": "txn_123456789",
    "paidAt": "2026-11-11T15:00:00+09:00",
    "createdAt": "2026-11-11T15:00:01"
  },
  "reissueAvailable": false
}
```

- 최상위 응답 필드 —`PaymentDto.DetailResponse`

| **필드**             | **타입**                     | **설명**     |
|--------------------|----------------------------|------------|
| `order`            | OrderInfo                  | 수주 정보      |
| `payment`          | PaymentInfo                | 결제 건 정보    |
| `virtualAccount`   | VirtualAccountDto.Response | 가장 최근 가상계좌 |
| `paymentHistory`   | PaymentHistoryDto.Response | 실제 입금 내역   |
| `reissueAvailable` | Boolean                    | 재발급 가능 여부  |

- 수주 정보 —`OrderInfo`

| **필드**        | **타입** | **설명**   |
|---------------|--------|----------|
| `orderId`     | Long   | 수주 ID    |
| `orderNumber` | String | 수주번호     |
| `clientName`  | String | 거래처명     |
| `totalAmount` | Long   | 수주 총액    |
| `orderStatus` | String | 수주 진행 상태 |

- 결제 정보 —`PaymentInfo`

| **필드**             | **타입**        | **설명**                            |
|--------------------|---------------|-----------------------------------|
| `paymentId`        | Long          | 결제 ID                             |
| `paymentType`      | PaymentType   | 계약금 또는 잔금                         |
| `scheduledAmount`  | Long          | 결제 예정 금액                          |
| `status`           | PaymentStatus | 결제 상태                             |
| `paymentMethod`    | PaymentMethod | 결제 수단                             |
| `createdAt`        | LocalDateTime | 결제 건 생성 일시                        |
| `actualPaidAmount` | Long          | 실제 입금 처리 금액                       |
| `portonePaymentId` | String        | 가상계좌 결제의 PortOne ID, 수동 입금 시 null |

- 입금 이력 —`PaymentHistoryDto.Response`

| **필드**             | **타입**         | **설명**                     |
|--------------------|----------------|----------------------------|
| `paymentHistoryId` | Long           | 입금 내역 ID                   |
| `paymentId`        | Long           | 연결된 결제 ID                  |
| `virtualAccountId` | Long           | 연결된 가상계좌 ID, 수동 입금 시 null  |
| `transactionId`    | String         | 외부 거래 식별자, 수동 입금 시 null 가능 |
| `paidAt`           | OffsetDateTime | 실제 입금 일시                   |
| `createdAt`        | LocalDateTime  | 입금 이력 생성 일시                |

### **7.10 수주별 결제·입금 조회**

- Method:`GET`
- URI:`/api/v1/payments?orderId={orderId}`
- Path Parameters: 없음
- 인증 필요 여부: 필수
  - 권한: 로그인 사용자
- 처리 규칙
  - 특정 수주의 전체 결제 예정액, 계약금, 누적 입금액, 미수액 및 결제 목록을 조회한다.
  - 계약금은 수주 총액의 50%를 원 단위 내림으로 계산한다.
  - `PAID`상태 결제만 누적 입금 금액에 포함한다.
  - 미입금 잔액은`max(0, totalAmount - paidAmount)`로 계산한다.
  - 결제 건이 없으면`payments=[]`를 반환한다.
  - 결제 건이나 가상계좌를 새로 생성하지 않는다.
  - PortOne 외부 API를 호출하지 않는다.
  - 수주가 존재하지 않으면`404 Not Found`.
- Query Parameters

| **파라미터**  | **타입** | **필수** | **설명**    |
|-----------|--------|--------|-----------|
| `orderId` | Long   | O      | 조회할 수주 ID |

- Request Body: 없음
- Response:`200 OK`

```json
{
  "orderId": 10,
  "totalAmount": 12000000,
  "depositAmount": 6000000,
  "paidAmount": 6000000,
  "balanceAmount": 6000000,
  "payments": [
    {
      "paymentId": 70,
      "paymentType": "DEPOSIT",
      "amount": 6000000,
      "status": "PAID",
      "paymentMethod": "VA",
      "createdAt": "2026-11-11T10:00:00",
      "virtualAccount": {
        "virtualAccountId": 30,
        "paymentId": 70,
        "bank": "SHINHAN",
        "accountNumber": "12345678901234",
        "accountName": "프로드플로우",
        "expiresAt": "2026-11-25T15:00:00+09:00",
        "status": "USED"
      },
      "paymentHistory": {
        "paymentHistoryId": 45,
        "paymentId": 70,
        "virtualAccountId": 30,
        "transactionId": "txn_123456789",
        "paidAt": "2026-11-11T15:00:00+09:00",
        "createdAt": "2026-11-11T15:00:01"
      }
    }
  ]
}
```

- 최상위 응답 필드 —`PaymentDto.OrderResponse`

| **필드**          | **타입** | **설명**    |
|-----------------|--------|-----------|
| `orderId`       | Long   | 수주 ID     |
| `totalAmount`   | Long   | 수주 총액     |
| `depositAmount` | Long   | 계약금 예정액   |
| `paidAmount`    | Long   | 누적 입금 완료액 |
| `balanceAmount` | Long   | 미입금 잔액    |
| `payments`      | List   | 결제 건 목록   |

- 결제 항목 —`PaymentDto.OrderPaymentResponse`

| **필드**           | **타입**                     | **설명**    |
|------------------|----------------------------|-----------|
| `paymentId`      | Long                       | 결제 ID     |
| `paymentType`    | PaymentType                | 계약금 또는 잔금 |
| `amount`         | Long                       | 결제 예정 금액  |
| `status`         | PaymentStatus              | 결제 상태     |
| `paymentMethod`  | PaymentMethod              | 결제 수단     |
| `createdAt`      | LocalDateTime              | 결제 생성 일시  |
| `virtualAccount` | VirtualAccountDto.Response | 최신 가상계좌   |
| `paymentHistory` | PaymentHistoryDto.Response | 입금 내역     |

### **7.11 PortOne 결제 상태 재동기화**

- Method:`POST`
- URI:`/api/v1/payments/{paymentId}/synchronize`
- Path Parameters: 없음
- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙
  - 가상계좌 발급 응답 유실 또는 웹훅 누락 등의 상황에서 PortOne의 실제 결제 상태를 다시 조회하여 DB에 반영한다.
  - PortOne에서 실제 결제 정보를 조회한다.
  - 결제 ID, 상점, 금액, 통화, 채널, PG사를 검증한다.
  - 결제 및 가상계좌 상태를 최신 정보로 반영한다.
  - 실제 입금이 확인되면 입금 이력 및 수주 상태를 갱신한다.
  - 새로운 가상계좌 발급 POST를 보내지 않는다.
  - `MD`수동 입금 건은 동기화할 수 없다.
  - 발급 정보를 확인할 수 없으면 정상적인 가상계좌 응답을 반환하지 못할 수 있다.
- Request Body: 없음
- Response:`200 OK`

`PaymentDto.IssueResponse`를 반환한다.

응답 필드 및 가상계좌 객체는 7.3과 동일하다.

### **7.12 가상계좌 발급·재발급 이력 조회**

- Method:`GET`
- URI:`/api/v1/payments/{paymentId}/virtual-accounts`
- Path Parameters: 없음
- 인증 필요 여부: 필수
  - 권한:`SUPER_ADMIN`,`ORDER_MANAGER`
- 처리 규칙
  - 특정 결제 건에서 발급한 모든 가상계좌를 최신순으로 조회한다.
  - 모든 가상계좌 발급 이력을 조회한다.
  - 가장 최근 발급한 가상계좌부터 반환한다.
  - 재발급 이전의 말소된 가상계좌도 포함한다.
  - 가상계좌 이력은 삭제하지 않는다.
  - 결제 건이 존재하지 않으면`404 Not Found`.
  - 가상계좌 이력이 없으면 빈 배열을 반환한다.
  - PortOne 외부 API를 호출하지 않는다.

**참고:**실제`VirtualAccountDto.Response`에는`portonePaymentId`필드가 없다. 따라서 해당 필드를 응답 명세에 추가하지 않는다.

- Request Body: 없음
- Response:`200 OK`

```json
[
  {
    "virtualAccountId": 31,
    "paymentId": 70,
    "bank": "SHINHAN",
    "accountNumber": "22222222222222",
    "accountName": "프로드플로우",
    "expiresAt": "2026-11-30T23:59:59+09:00",
    "status": "ACTIVE"
  },
  {
    "virtualAccountId": 30,
    "paymentId": 70,
    "bank": "SHINHAN",
    "accountNumber": "11111111111111",
    "accountName": "프로드플로우",
    "expiresAt": "2026-11-20T23:59:59+09:00",
    "status": "CANCELLED"
  }
]
```

- 응답 필드 —`VirtualAccountDto.Response`

| **필드**             | **타입**               | **설명**     |
|--------------------|----------------------|------------|
| `virtualAccountId` | Long                 | 가상계좌 ID    |
| `paymentId`        | Long                 | 결제 ID      |
| `bank`             | Bank                 | 은행 코드      |
| `accountNumber`    | String               | 계좌번호       |
| `accountName`      | String               | 예금주        |
| `expiresAt`        | OffsetDateTime       | 만료 일시      |
| `status`           | VirtualAccountStatus | 가상계좌 실효 상태 |

### **7.13 공통 인증 및 접근 권한**

| **API**       | **접근 권한**                  |
|---------------|----------------------------|
| 현금 계약금 수동 입금  | SUPER_ADMIN, ORDER_MANAGER |
| 가상계좌 발급       | SUPER_ADMIN, ORDER_MANAGER |
| 가상계좌 재발급      | SUPER_ADMIN, ORDER_MANAGER |
| 결제 상태 재동기화    | SUPER_ADMIN, ORDER_MANAGER |
| 가상계좌 이력 조회    | SUPER_ADMIN, ORDER_MANAGER |
| 계약금 안내 이메일 발송 | SUPER_ADMIN, ORDER_MANAGER |
| PortOne 웹훅    | JWT 불필요, 서명 검증 필수          |
| 입금 목록 조회      | 로그인 사용자                    |
| 입금 통계 조회      | 로그인 사용자                    |
| 입금 상세 조회      | 로그인 사용자                    |
| 수주별 결제·입금 조회  | 로그인 사용자                    |

- 공통 보안 규칙
  - 일반 업무 API는`Authorization: Bearer <accessToken>`을 사용한다.
  - 인증되지 않은 사용자는`401 Unauthorized`.
  - 권한이 부족한 사용자는`403 Forbidden`.
  - `mustChangePassword=true`인 사용자는 일반 결제 API에 접근할 수 없다.
  - PortOne 웹훅은 JWT 대신 웹훅 서명으로 요청을 검증한다.

### **7.14 결제 및 가상계좌 상태**

- 결제 유형 —`PaymentType`

| **값**     | **설명** |
|-----------|--------|
| `DEPOSIT` | 계약금    |
| `BALANCE` | 잔금     |

- 결제 수단 —`PaymentMethod`

| **값** | **설명** |
|-------|--------|
| `VA`  | 가상계좌   |
| `MD`  | 수동 입금  |

- 결제 상태 —`PaymentStatus`

| **상태**                   | **설명**                |
|--------------------------|-----------------------|
| `READY`                  | 결제 준비                 |
| `REQUESTED`              | 가상계좌 발급 요청            |
| `ISSUE_UNCERTAIN`        | 발급 결과 확인 필요           |
| `WAITING`                | 입금 대기                 |
| `PAID`                   | 입금 완료                 |
| `FAILED`                 | 결제 실패                 |
| `CANCELLED`              | 결제 취소                 |
| `REISSUE_CLOSING`        | 재발급을 위한 기존 계좌 말소 확인 중 |
| `ORDER_CANCEL_CLOSING`   | 수주 취소에 따른 계좌 말소 확인 중  |
| `PENDING`                | 처리 대기                 |
| `PAY_PENDING`            | 승인 대기                 |
| `VIRTUAL_ACCOUNT_ISSUED` | 가상계좌 발급 상태            |
| `PARTIAL_CANCELLED`      | 부분 취소                 |

PortOne에서 전달하는`VIRTUAL_ACCOUNT_ISSUED`상태는 현재 서비스에서 내부`WAITING`상태로 변환한다.

- 가상계좌 상태 —`VirtualAccountStatus`

| **상태**      | **설명**      |
|-------------|-------------|
| `ACTIVE`    | 입금 가능       |
| `EXPIRED`   | 입금 기한 만료    |
| `CANCELLED` | 계좌 말소 또는 취소 |
| `USED`      | 입금 완료       |

- 가상계좌 상태 처리 규칙
- 발급 직후 초기 상태는`ACTIVE`이다.
- 입금이 완료되면`USED`로 변경한다.
- 기존 가상계좌 말소가 확정되면`CANCELLED`로 변경한다.
- 만료 일시가 지나면 실효 상태가`EXPIRED`로 표시될 수 있다.
- 정기 작업은 만료된 활성 계좌를`EXPIRED`로 갱신한다.
- `USED`,`CANCELLED`상태는 만료 처리로 덮어쓰지 않는다.

가상계좌 만료 확인 작업은 기본 60초 간격으로 실행된다.

## 8. 배송

택배사 접수 API는 호출하지 않는다. 직원이 받은 운송장 번호를 저장하고, 스마트택배 조회 API로 상태를 갱신한다.

### **8.1 배송 목록**

- Method: `GET`
- URI: `/api/v1/shipments`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  - 쿼리 `tab`: `PACKING` | `READY_TO_SHIP` | `SHIPPING` | `BALANCE` (선택). 생략 시 `READY_TO_SHIP`
  - 쿼리 `page`: 0부터. 기본 `0`
  - 쿼리 `size`: 기본 `10`
  - 쿼리 `sort`: 기본 `estimatedDueDate,asc`
  - `counts`는 탭별 **전체** 건수다. 페이징과 무관하다
  - 포장중(`PACKING`) 탭은 작업지시가 있고 전부 `PACK_DONE`인 수주만 목록·건수에 포함한다
  - 그 항목의 `readyToShipEligible`은 `true`다. 다른 탭은 보통 `false`
  - 잔금대기(`BALANCE`)는 배송 완료(`COMPLETED`)이면서 전액 입금(`PAID`)이 아닌 수주
- Response: `200 OK`

```json
{
  "tab": "READY_TO_SHIP",
  "counts": {
    "packing": 0,
    "readyToShip": 1,
    "shipping": 1,
    "balance": 2
  },
  "page": {
    "content": [
      {
        "orderId": 3,
        "orderNo": "PF-20261022-3",
        "clientName": "한빛케이스(주)",
        "productSummary": "갤럭시 S25 카드수납 슬라이드 케이스",
        "totalQuantity": 1500,
        "shippingAddress": "서울시 ...",
        "estimatedDueDate": "2026-10-30",
        "orderStatus": "READY_TO_SHIP",
        "paymentStatus": "PARTIALLY_PAID",
        "totalAmount": 18000000,
        "shipmentId": null,
        "carrierCode": null,
        "carrierName": null,
        "trackingNumber": null,
        "startedAt": null,
        "deliveredAt": null,
        "readyToShipEligible": false
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### **8.2 포장중 → 출고대기**

- Method: `POST`
- URI: `/api/v1/shipments/orders/{orderId}/ready-to-ship`
- Path Parameters
  - `orderId`: 수주 ID, 필수, Long
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  - 수주 상태가 `PACKING`이어야 한다
  - 해당 수주에 작업지시가 있어야 한다
  - 모든 작업지시가 `PACK_DONE`이어야 한다
  - 수주 상태를 `READY_TO_SHIP`으로 바꾼다
- Response: `200 OK`

```json
{
  "orderId": 3,
  "orderNo": "PF-20261022-3",
  "orderStatus": "READY_TO_SHIP"
}
```

### **8.3 운송장 등록(출고)**

- Method: `POST`
- URI: `/api/v1/shipments`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  - 수주 상태가 `READY_TO_SHIP`일 때만 가능
  - `shipment`를 생성하고 수주 상태를 `SHIPPING`으로 변경한다
  - `carrierCode`는 `carrier.code`와 일치해야 한다
  - 수주당 운송장은 하나다
  - 같은 택배사 + 운송장 번호는 중복할 수 없다
  - 운송장 번호는 숫자만 허용한다
  - 등록 시 잔금 결제 준비(`BALANCE`)를 스케줄한다
- Request Body

```json
{
  "orderId": 3,
  "carrierCode": "04",
  "trackingNumber": "123456789012"
}
```

- Response: `201 Created`

```json
{
  "id": 10,
  "orderId": 3,
  "orderNo": "PF-20261022-3",
  "orderStatus": "SHIPPING",
  "carrierCode": "04",
  "carrierName": "CJ대한통운",
  "trackingNumber": "123456789012",
  "startedAt": "2026-11-06T10:00:00"
}
```

### **8.4 수주별 배송 정보 조회**

- Method: `GET`
- URI: `/api/v1/shipments/{orderId}`
- Path Parameters
  - `orderId`: 수주 ID, 필수, Long
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  - 저장된 배송 정보만 조회한다
  - 외부 배송 추적 API를 호출하지 않는다
  - 배송 미등록이면 `shipment`는 `null`
  - 없는 수주면 `404`
- Response: `200 OK`

```json
{
  "shipment": {
    "shipmentId": 10,
    "orderId": 3,
    "orderStatus": "SHIPPING",
    "carrierCode": "04",
    "carrierName": "CJ대한통운",
    "trackingNumber": "123456789012",
    "level": 3,
    "result": "Y",
    "complete": false,
    "estimate": "2026-11-09",
    "startedAt": "2026-11-06T10:00:00",
    "deliveredAt": null
  }
}
```

### **8.5 배송 조회 갱신**

- Method: `POST`
- URI: `/api/v1/shipments/{id}/tracking/refresh`
- Path Parameters
  - `id`: 운송장 ID, 필수, Long
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  - 스마트택배 조회 API를 호출하고 응답을 `shipment`에 저장한다
  - 저장 필드: `level`, `result`, `complete`, `estimate` (`startedAt`/`deliveredAt`은 완료 시 반영)
  - 배송완료면 수주 상태를 `COMPLETED`로 변경한다
  - 동일 로직을 스케줄러도 주기적으로 수행한다 (`SHIPMENT_TRACKING_REFRESH_ENABLED`)
  - 완료 문자 발송은 **현재 구현되어 있지 않다**
  - 없는 운송장이면 `404`
- Response: `200 OK`, `ShipmentTrackingDto`

응답 필드는 8.4의 `shipment`와 같다.

### **8.6 배송 조회(직접 호출, 저장 없음)**

- Method: `GET`
- URI: `/api/v1/shipments/tracking`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  - 쿼리 `carrierCode`, `trackingNumber` 필수
  - 스마트택배 API만 호출하고 DB에 저장하지 않는다
- Response: `200 OK`

### **8.7 수동 배송 완료**

- Method: `POST`
- URI: `/api/v1/shipments/{id}/complete`
- Path Parameters
  - `id`: 운송장 ID, 필수, Long
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  - 수주 상태가 `SHIPPING`일 때만 가능
  - 운송장을 완료 처리하고 수주를 `COMPLETED`로 바꾼다
  - 잔금이 남아 있으면 잔금대기 목록에 보인다
- Response: `200 OK`

```json
{
  "shipmentId": 10,
  "orderId": 3,
  "orderNo": "PF-20261022-3",
  "orderStatus": "COMPLETED",
  "complete": true,
  "deliveredAt": "2026-11-07T15:00:00"
}
```

### **8.8 택배사 목록**

- Method: `GET`
- URI: `/api/v1/carriers`
- Path Parameters: 없음
- 인증 필요 여부: 필요
  - 권한: 로그인 사용자
- 처리 규칙
  - 출고 화면의 택배사 선택 목록이다
  - `code`, `name`을 반환한다
- Response: `200 OK`

```json
[
  {
    "code": "04",
    "name": "CJ대한통운"
  }
]
```

## 9. 대시보드 통계

- 인증 필요 여부 : 필수
- 공통 권한 : 로그인 사용자
- 응답 형식 : JSON
- 기간 조회 : 기간이 필요한 API는 `from`, `to` 쿼리 파라미터 사용
- 날짜 형식 : YYYY-MM-DD
- 통계 기준 : 취소된 수주는 통계 집계에서 제외하는 것을 기본으로 하며, 각 API의 세부 집계 조건을 따름

### 9.1 대시보드 통합 통계 조회

- Method : GET
- URL : `/api/v1/statistics/dashboard`
- 인증 필요 여부 : 필수
- 처리 규칙
  - 대시보드 화면에 필요한 통계 데이터를 통합하여 조회
  - 수주 통계, 상태별 수주 통계, 일별·주별·월별 생산 통계, 납기 이행률, 품목별 생산 통계, 납기 위험 수주 통계를 반환
  - 상탄 요약 통계는 수주 통계, 생산 통계, 납기 이행률 기반으로 계산
  - 생산 진행률은 생산 완료 수량을 생산 계획 수량으로 나누어 백분율 계산
  - 미입금 금액은 취소되지 않은 수주의 총 금액에서 입금 완료된 금액을 차감하여 계산
  - 납기 위험 통계는 현재 날짜를 기준으로 납기일까지 3일 이하로 남았거나 납기일이 지난 미출고 수주를 조회
  - 조회 시작일과 종료일을 기준으로 기간별 통계 조회
  - 종료일이 시작일보다 빠르면 조회 불가
- Query Parameters : 선택
  - `startDate` : 조회 시작일, String (YYYY-MM-DD)
  - `endDate` : 조회 종료일, String (YYYY-MM-DD)
- Request Body : 없음
- Response : `200 OK`

```json
},
"deliveryRiskStatistics": {
"totalRiskOrderCount": 2,
"items": [
{
"orderId": 3,
"orderNo": "PF-2026-03".
"customerName": "ABC 기업"
"productName": "iPhone 15 Pro 젤리 케이스"
"quantity": 500,
"requestedDueDate": "2026-10-30",
"plannedEndDate": "2026-10-29",
"statusCode": 2,
"riskType": "URGENT",
"riskReason": "납기일까지 2일 남았습니다.",
"paymentStatus": "PARTIAL",
"paidAmount": 500000
}
]
}
}
```

- `summary` : 대시보드 상단 요약 통계 객체
  - `totalOrderCount` : 전체 수주 건수, Long
  - `plannedQuantity` : 조회 기간 내 생산 계획 수량, Long
  - `completedQuantity` : 조회 기간 내 생산 완료 수량, Long
  - `productionProgressRate` : 생산 진행률, Double
  - `onTimeDeliveryRate` : 납기 이행률, Long
  - `unpaidAmount` : 미입금 금액, Long
- `orderStatistics` : 수주 통계 객체
  - `totalOrderCount` : 전체 수주 건수, Long
  - `preiodOrderCount` : 조회 기간 내 수주 건수, Long
- `orderStatusStatistics` : 상태별 수주 통계 목록, Array
  - `statusCode` : 수주 상태 코드, String
  - `statusName` : 수주 상태명, String
  - `orderCount` : 해당 상태의 수주 건수, Long
- `productionStatistics` : 날짜별 생산 통계 목록, Array
  - `date` : 생산 기준일, String(YYYY-MM-DD)
  - `plannedQuantity` : 생산 계획 수량, Long
  - `completedQuantity` : 생산 완료 수량, Long
- `monthlyProductionStatistics` : 월별 생산 통계 목록, Array
  - `year` : 연도, Integer
  - `month` : 월, Integer
  - `plannedQuantity` : 생산 계획 수량
- `weeklyProductionStatistics` : 주별 생산 통계 목록, Array
  - `week` : 해당 월의 주차, Integer
  - `startDate` : 주간 시작일, String (`YYYY-MM-DD`)
  - `endDate` : 주간 종료일, String (`YYYY-MM-DD`)
  - `plannedQuantity` : 생산 계획 수량, Long
  - `completedQuantity` : 생산 완료 수량, Long
- `deliveryPerformance` : 납기 이행률 통계 객체
  - `totalOrderCount` : 납기 이행률 집계 대상 수주 건수, Long
  - `onTimeOrderCount` : 납기 준수 수주 건수, Long
  - `onTimeDeliveryRate` : 납기 이행률, Double
- `productStatistics` : 품목별 생산 완료 통계 목록, Array
  - `productId` : 품목 ID, Long
  - `productName` : 품목명, String
  - `completedQuantity` : 생산 완료 수량, Long
- `productProductionShare` : 품목별 생산 비중 통계 객체
  - `totalCompletedQuantity` : 전체 생산 완료 수량, Long
  - `items` : 품목별 생산 비중 목록, Array
    - `productId` : 품목 ID, Long
    - `productName` : 품목명, String
    - `completedQuantity` : 생산 완료 수량, Long
    - `productionShare` : 전체 생산 완료 수량 대비 비중(%), Double
- `deliveryRiskStatistics` : 납기 위험 수주 통계 객체
  - `totalRiskOrderCount` : 납기 위험 수주 건수, Long
  - `items` : 납기 위험 수주 목록, Array
    - `orderId` : 수주 ID, Long
    - `orderNo` : 수주 번호, String
    - `customerName` : 고객사명, String
    - `productName` : 품목명, String
    - `quantity` : 수주 수량 합계, Integer
    - `requestedDueDate` : 요청 납기일, String (`YYYY-MM-DD`)
    - `plannedEndDate` : 계획 종료일, String (`YYYY-MM-DD`)
    - `statusCode` : 수주 상태 코드 ID, Long
    - `riskType` : 납기 위험 유형, String (`URGENT`, `DELAYED`)
    - `riskReason` : 납기 위험 사유, String
    - `paymentStatus` : 입금 상태, String
    - `paidAmount` : 입금 완료 금액, Long

### 9.2 수주 통계 조회

- Method : GET
- URL : `/api/v1/statistics/orders`
- 인증 필요 여부 : 필수
- 처리 규칙
  - 전체 수주 건수와 조회 기간 내 수주 건수를 조회
  - 취소된 수주는 집계에서 제외
  - 조회 기간 내 수주 건수는 수주 생성일(`createdAt`)을 기준으로 집계
  - 조회 시작일과 종료일은 모두 포함
- Query Parameters : 필수
  - `from` : 조회 시작일, String (`YYYY-MM-DD`)
  - `to` : 조회 종료일, String (`YYYY-MM-DD`)
- Request Body : 없음
- Response : `200 OK`

```json
{
  "totalOrderCount": 42,
  "periodOrderCount": 12
}
```

- totalOrderCount : 전체 수주 건수, Integer
- periodOrderCount : 조회 기간 내 수주 건수, Integer

### 9.3 생산 통계 조회

실제 구현에서는 생산 통계가 월간·주간·일간 API로 분리되어 있어 기존 `/production` 경로 대신 아래 세 가지 API를 사용

9.3.1 월간 생산 통계 조회

- Method : GET
- URL : `/api/v1/statistics/production/monthly`
- 인증 필요 여부 : 필수
- 처리 규칙
  - 선택한 연도와 월의 생산 계획 수량 및 생산 완료 수량을 조회
  - 취소된 수주는 집계에서 제외
  - 생산 완료 수량은 작업지시 상태가 `PACK_DONE`인 데이터를 기준으로 집계
- Query Parameters : 필수
  - `year` : 조회 연도, Integer
  - `month` : 조회 월, Integer (1~12)
- Request Body : 없음
- Response : `200 OK`

```json
{
  "year": 2026,
  "month": 10,
  "plannedQuantity": 36000,
  "completedQuantity": 28400
}
```

- `year` : 조회 연도, Integer
- `month` : 조회 월, Integer
- `plannedQuantity` : 생산 계획 수량, Long
- `completedQuantity` : 생산 완료 수량, Long

9.3.2 주간 생산 통계 조회

- Method : GET
- URL : `/api/v1/statistics/production/weekly`
- 인증 필요 여부 : 필수
- 처리 규칙
  - 선택한 연도와 월의 주차별 생산 계획 수량 및 생산 완료 수량을 조회
  - 주차는 일요일을 기준으로 구분
  - 월의 첫 주와 마지막 주는 해당 월에 포함되는 날짜만 집계
  - 취소된 수주는 집계에서 제외
- Query Parameters : 필수
  - `year` : 조회 연도, Integer
  - `month` : 조회 월, Integer (1~12)
- Request Body : 없음
- Response : `200 OK`

```json
[
  {
    "week": 1,
    "startDate": "2026-10-01",
    "endDate": "2026-10-03",
    "plannedQuantity": 5000,
    "completedQuantity": 4200
  },
  {
    "week": 2,
    "startDate": "2026-10-04",
    "endDate": "2026-10-10",
    "plannedQuantity": 8000,
    "completedQuantity": 7500
  }
]
```

- 응답 형식 : Array
  - `week` : 해당 월의 주차, Integer
  - `startDate` : 주간 시작일, String (`YYYY-MM-DD`)
  - `endDate` : 주간 종료일, String (`YYYY-MM-DD`)
  - `plannedQuantity` : 생산 계획 수량, Long
  - `completedQuantity` : 생산 완료 수량, Long

9.3.3 일간 생산 통계 조회

- Method : GET
- URL : `/api/v1/statistics/production/daily`
- 인증 필요 여부 : 필수
- 처리 규칙
  - 조회 기간 내 날짜별 생산 계획 수량 및 생산 완료 수량을 조회
  - 해당 날짜에 생산 데이터가 없는 경우에도 수량을 0으로 반환
  - 취소된 수주는 집계에서 제외
  - 생산 완료 수량은 작업지시 상태가 `PACK_DONE`인 데이터를 기준으로 집계
- Query Parameters : 필수
  - `startDate` : 조회 시작일, String (`YYYY-MM-DD`)
  - `endDate` : 조회 종료일, String (`YYYY-MM-DD`)
- Request Body : 없음
- Response : `200 OK`

```json
[
  {
    "date": "2026-10-28",
    "plannedQuantity": 1200,
    "completedQuantity": 1000
  },
  {
    "date": "2026-10-29",
    "plannedQuantity": 0,
    "completedQuantity": 0
  }
]
```

- 응답 형식 : Array
  - `date` : 생산 기준일, String (`YYYY-MM-DD`)
  - `plannedQuantity` : 생산 계획 수량, Long
  - `completedQuantity` : 생산 완료 수량, Long

### 9.4 납기 이행률 조회

- Method : GET
- URL : `/api/v1/statistics/delivery-performance`
- 인증 필요 여부 : 필수
- 처리 규칙
  - 요청 납기일이 조회 기간에 포함된 수주를 기준으로 납기 이행률을 조회
  - 취소된 수주는 집계에서 제외
  - 현재 날짜보다 요청 납기일이 이전인 수주를 집계 대상으로 설정
  - 납기일까지 실제 배송이 완료된 수주 중 요청 납기일 이내에 배송 완료된 수주를 납기 준수 건수로 집계
  - 납기 이행률은 납기 준수 건수를 집계 대상 수주 건수로 나누어 백분율로 계산
  - 집계 대상 수주가 없는 경우 납기 이행률은 `0.0`으로 반환
  - 계산된 비율은 소수점 둘째 자리까지 반올림
- Query Parameters : 필수
  - `from` : 조회 시작일, String (`YYYY-MM-DD`)
  - `to` : 조회 종료일, String (`YYYY-MM-DD`)
- Request Body : 없음
- Response : `200 OK`

```json
{
  "totalOrderCount": 24,
  "onTimeOrderCount": 23,
  "onTimeDeliveryRate": 95.83
}
```

- `totalOrderCount` : 납기 이행률 집계 대상 수주 건수, Long
- `onTimeOrderCount` : 요청 납기일 이내 배송 완료된 수주 건수, Long
- `onTimeDeliveryRate` : 납기 이행률(%), Double

### 9.5 상태별 수주 건수 조회

- Method : GET
- URL : `/api/v1/statistics/order-status`
- 인증 필요 여부 : 필수
- 처리 규칙
  - 수주 상태별 수주 건수를 조회
  - 상태 코드와 상태명을 기준으로 그룹화하여 집계
  - 현재 구현에서는 기간별 필터링을 적용하지 않음
- Query Parameters : 없음
- Request Body : 없음
- Response : `200 OK`

```json
[
  {
    "statusCode": "WAITING",
    "statusName": "대기",
    "orderCount": 10
  },
  {
    "statusCode": "PRODUCING",
    "statusName": "생산 중",
    "orderCount": 8
  },
  {
    "statusCode": "SHIPPING",
    "statusName": "배송 중",
    "orderCount": 3
  },
  {
    "statusCode": "DONE",
    "statusName": "완료",
    "orderCount": 21
  }
]
```

- 응답 형식 : Array
  - `statusCode` : 수주 상태 코드, String
  - `statusName` : 수주 상태명, String
  - `orderCount` : 해당 상태의 수주 건수, Long

### 9.6 품목별 생산 완료 수량 조회

실제 구현에서는 품목별 생산 통계와 생산 비중 통계가 별도 API로 제공

9.6.1 품목별 생산 완료 수량 조회

- Method : GET
- URL : `/api/v1/statistics/product-production-share`
- 인증 필요 여부 : 필수
- 처리 규칙
  - 선택한 월의 품목별 생산 완료 수량을 조회
  - 작업지시 상태가 `PACK_DONE`인 데이터를 생산 완료 수량으로 집계
  - 취소된 수주는 집계에서 제외
  - 품목별 생산 완료 수량과 전체 생산 완료 수량을 기준으로 생산 비중을 계산
  - 생산 비중은 소수점 첫째 자리까지 반올림
  - 전체 생산 완료 수량이 0인 경우 생산 비중은 `0.0`으로 반환
- Query Parameters : 필수
  - `month` : 조회 연월, String (`YYYY-MM`)
- Request Body : 없음
- Response : `200 OK`

```json
{
  "totalCompletedQuantity": 2050,
  "items": [
    {
      "productId": 1,
      "productName": "케이스 A",
      "completedQuantity": 1200,
      "productionShare": 58.5
    },
    {
      "productId": 2,
      "productName": "케이스 B",
      "completedQuantity": 850,
      "productionShare": 41.5
    }
  ]
}
```

- `totalCompletedQuantity` : 전체 생산 완료 수량, Long
- `items` : 품목별 생산 비중 목록, Array
  - `productId` : 품목 ID, Long
  - `productName` : 품목명, String
  - `completedQuantity` : 품목별 생산 완료 수량, Long
  - `productionShare` : 전체 생산 완료 수량 대비 비중(%), Double

### 9.7 납기 임박 및 지연 수주 조회

- Method : GET
- URL : `/api/v1/statistics/delivery-risks`
- 인증 필요 여부 : 필수
- 처리 규칙
  - 현재 날짜를 기준으로 납기 임박 또는 납기 지연 상태의 미출고 수주 목록을 조회
  - 취소된 수주는 조회 대상에서 제외
  - 요청 납기일이 현재 날짜부터 3일 이내인 수주는 납기 임박으로 분류
  - 요청 납기일이 현재 날짜보다 이전인 수주는 납기 지연으로 분류
  - 배송 완료된 수주는 조회 대상에서 제외
  - 납기 위험 수주에 포함된 품목명과 수량을 합산하여 반환
  - 입금 완료 상태(`PAID`)인 결제 금액을 수주별로 합산하여 반환
  - 현재 날짜를 기준으로 자동 조회하므로 기간 및 유형별 필터링은 제공하지 않음
- Query Parameters : 없음
- Request Body : 없음
- Response : `200 OK`

```json
{
  "totalRiskOrderCount": 2,
  "items": [
    {
      "orderId": 3,
      "orderNo": "PF-2026-003",
      "customerName": "ABC 기업",
      "productName": "케이스 A",
      "quantity": 500,
      "requestedDueDate": "2026-10-30",
      "plannedEndDate": "2026-10-29",
      "statusCode": 2,
      "riskType": "URGENT",
      "riskReason": "납기일까지 2일 남았습니다.",
      "paymentStatus": "PARTIAL",
      "paidAmount": 500000
    },
    {
      "orderId": 5,
      "orderNo": "PF-2026-005",
      "customerName": "DEF 기업",
      "productName": "케이스 B",
      "quantity": 300,
      "requestedDueDate": "2026-10-27",
      "plannedEndDate": "2026-10-26",
      "statusCode": 3,
      "riskType": "DELAYED",
      "riskReason": "납기일이 지났습니다.",
      "paymentStatus": "UNPAID",
      "paidAmount": 0
    }
  ]
}
```

- `totalRiskOrderCount` : 납기 위험 수주 건수, Long
- `items` : 납기 위험 수주 목록, Array
  - `orderId` : 수주 ID, Long
  - `orderNo` : 수주 번호, String
  - `customerName` : 고객사명, String
  - `productName` : 품목명, String
  - `quantity` : 수주 수량 합계, Integer
  - `requestedDueDate` : 요청 납기일, String (`YYYY-MM-DD`)
  - `plannedEndDate` : 계획 종료일, String (`YYYY-MM-DD`)
  - `statusCode` : 수주 상태 코드 ID, Long
  - `riskType` : 납기 위험 유형, String (`URGENT`, `DELAYED`)
  - `riskReason` : 납기 위험 사유, String
  - `paymentStatus` : 입금 상태, String
  - `paidAmount` : 입금 완료 금액, Long

  ### 9.8 대시보드 알림 목록 조회

  - 인증 필요 여부 : 필수
  - 공통 권한 : 로그인 사용자
  - 응답 형식 : JSON
  - 처리 규칙
    - 생산라인 상태 변경 알림과 납기 위험 알림을 조회
    - 생산라인 상태 알림과 수주 납기 알림은 각각 별도의 API를 통해 조회
    - 로그인한 사용자를 알림 수신자로 지정하여 본인에게 발송된 알림만 조회
    - 각 알림은 생성일시(`createdAt`) 기준 내림차순으로 정렬
    - 대시보드에서는 두 API의 응답을 통합하여 최신순으로 표시
    - 알림 읽음 여부는 `readAt` 값으로 판단
      - `readAt`이 `null`이면 읽지 않은 알림
      - `readAt`에 값이 있으면 읽은 알림

  #### **9.8.1 생산라인 상태 알림 목록 조회**

  - Method : GET
  - URL : `/api/v1/notifications`
  - 인증 필요 여부 : 필수
  - 처리 규칙
    - 생산라인 상태 변경에 따른 알림 목록을 조회
    - 활성 상태의 최고관리자, 수주 관리자, 생산 현장 관리자에게 생성된 알림 중 로그인한 사용자에게 발송된 알림만 조회
    - 알림 생성일시(`createdAt`) 기준 내림차순으로 정렬
  - Query Parameters : 없음
  - Request Body : 없음
  - 조회 대상 테이블 : `line_notification`
  - Response : `200 OK`

```json
[
  {
    "notification": 1,
    "lineId": 2,
    "lineName": "2번 생산라인",
    "notificationType": "BROKEN",
    "message": "2번 생산라인이 고장 상태로 변경되었습니다.",
    "createdAt": "2026-11-09T09:30:00",
    "readAt": null,
    "isRead": false
  },
  {
    "notification": 2,
    "lineId": 1,
    "lineName": "1번 생산라인",
    "notificationType": "MAINTENANCE",
    "message": "1번 생산라인이 점검 상태로 변경되었습니다.",
    "createdAt": "2026-11-09T09:40:00",
    "readAt": "2026-11-09T10:00:00",
    "isRead": true
  }
]
```

- 응답 형식 : Array
  - `notification` : 알림 ID, Long
  - `lineId` : 생산라인 ID, Long
  - `lineName` : 생산라인명, String
  - `notificationType` : 생산라인 알림 유형, String
    - `AVAILABLE` : 가동 가능
    - `MAINTENANCE` : 점검 중
    - `BROKEN` : 고장
  - `message` : 알림 메시지, String
  - `createdAt` : 알림 생성일시, String (`YYYY-MM-DDTHH:mm:ss`)
  - `readAt` : 알림 읽음 처리일시, String (`YYYY-MM-DDTHH:mm:ss`) 또는 `null`
  - `isRead` : 알림 읽음 여부, Boolean

#### **9.8.2 납기 위험 알림 목록 조회**

- Method : GET
- URL : `/api/v1/order-notifications`
- 인증 필요 여부 : 필수
- 처리 규칙
  - 납기 임박 및 납기 지연에 따른 알림 목록을 조회
  - 해당 수주의 담당 직원에게 발송된 알림 중 로그인한 사용자에게 발송된 알림만 조회
  - 알림 생성일시(`createdAt`) 기준 내림차순으로 정렬
  - 납기 임박 및 납기 지연 알림은 매일 한국 시간 자정에 스케줄러를 통해 생성
  - 동일 수주에 동일한 유형의 알림이 이미 존재하면 중복 생성하지 않음
- Query Parameters : 없음
- Request Body : 없음
- 조회 대상 테이블 : `order_notification`
- Response : `200 OK`

```json
[
  {
    "notification": 3,
    "orderId": 3,
    "orderNo": "PF-2026-003",
    "clientName": "ABC 기업",
    "notificationType": "DELIVERY_IMMINENT",
    "message": "PF-2026-003 납기까지 2일 남았습니다.",
    "createdAt": "2026-11-09T00:00:00",
    "readAt": null,
    "isRead": false
  },
  {
    "notification": 4,
    "orderId": 5,
    "orderNo": "PF-2026-005",
    "clientName": "DEF 기업",
    "notificationType": "DELIVERY_DELAYED",
    "message": "PF-2026-005 납기 2026-11-07가 지났습니다.",
    "createdAt": "2026-11-09T00:00:00",
    "readAt": "2026-11-09T09:00:00",
    "isRead": true
  }
]
```

- 응답 형식 : Array
  - `notification` : 알림 ID, Long
  - `orderId` : 수주 ID, Long
  - `orderNo` : 수주 번호, String
  - `clientName` : 고객사명, String
  - `notificationType` : 수주 알림 유형, String
    - `DELIVERY_IMMINENT` : 납기 임박
    - `DELIVERY_DELAYED` : 납기 지연
  - `message` : 알림 메시지, String
  - `createdAt` : 알림 생성일시, String (`YYYY-MM-DDTHH:mm:ss`)
  - `readAt` : 알림 읽음 처리일시, String (`YYYY-MM-DDTHH:mm:ss`) 또는 `null`
  - `isRead` : 알림 읽음 여부, Boolean

#### **9.8.3 읽지 않은 알림 개수 조회**

- Method : GET
- URL : `/api/v1/notifications/unread-count`
- 인증 필요 여부 : 필수
- 처리 규칙
  - 로그인한 사용자의 읽지 않은 생산라인 알림 개수를 조회
  - `readAt`이 `null`인 알림만 집계
- Query Parameters : 없음
- Request Body : 없음
- Response : `200 OK`

```json
3
```

- 응답 형식 : Long
  - 읽지 않은 생산라인 알림 개수

#### **9.8.4 읽지 않은 납기 알림 개수 조회**

- Method : GET
- URL : `/api/v1/order-notifications/unread-count`
- 인증 필요 여부 : 필수
- 처리 규칙
  - 로그인한 사용자의 읽지 않은 납기 위험 알림 개수를 조회
  - `readAt`이 `null`인 알림만 집계
- Query Parameters : 없음
- Request Body : 없음
- Response : `200 OK`

```
2
```

- 응답 형식 : Long
  - 읽지 않은 납기 알림 개수

#### **9.8.5 생산라인 알림 읽음 처리**

- Method : PATCH
- URL : `/api/v1/notifications/{notificationId}/read`
- 인증 필요 여부 : 필수
- 처리 규칙
  - 지정한 알림 ID에 해당하는 알림을 읽음 처리
  - 로그인한 사용자에게 발송된 알림인지 확인
  - 이미 읽은 알림이라면 기존 읽음 처리일시를 유지
- Path Parameters : 필수
  - `notificationId` : 생산라인 알림 ID, Long
- Request Body : 없음
- Response : `200 OK`

```json
{
  "notification": 1,
  "lineId": 2,
  "lineName": "2번 생산라인",
  "notificationType": "BROKEN",
  "message": "2번 생산라인이 고장 상태로 변경되었습니다.",
  "createdAt": "2026-11-09T09:30:00",
  "readAt": "2026-11-09T10:00:00",
  "isRead": true
}
```

#### **9.8.6 납기 알림 읽음 처리**

- Method : PATCH
- URL : `/api/v1/order-notifications/{notificationId}/read`
- 인증 필요 여부 : 필수
- 처리 규칙
  - 지정한 알림 ID에 해당하는 납기 알림을 읽음 처리
  - 로그인한 사용자에게 발송된 알림인지 확인
  - 이미 읽은 알림이라면 기존 읽음 처리일시를 유지
- Path Parameters : 필수
  - `notificationId` : 납기 알림 ID, Long
- Request Body : 없음
- Response : `200 OK`

```json
{
  "notification": 3,
  "orderId": 3,
  "orderNo": "PF-2026-003",
  "clientName": "ABC 기업",
  "notificationType": "DELIVERY_IMMINENT",
  "message": "PF-2026-003 납기까지 2일 남았습니다.",
  "createdAt": "2026-11-09T00:00:00",
  "readAt": "2026-11-09T10:10:00",
  "isRead": true
}
```

## 10. AI

생성된 SQL은 조회 문만 실행한다. SQL 원문은 응답에 넣지 않고`ai_message.generated_sql`에만 저장한다.

### 10.1 대화방 생성

- Method:`POST`
- URI:`/api/v1/ai/conversations`
- 인증 필요 여부: 필수
  - 권한: 로그인 사용자
- 처리 규칙
  - 본문 없이 본인 대화방을 만듦
  - 제목은`새 대화`임.`titleEdited`는`false`,`isClosed`는`false`임.
- Request Body: 없음
- Response:`201 Created`, 대화방 ID

```
3
```

### 10.2 대화방 목록 조회

- Method:`GET`
- URI:`/api/v1/ai/conversations`
- 인증 필요 여부: 필수
  - 권한: 로그인 사용자
- 처리 규칙
  - 종료하지 않은 본인 대화방만 반환
  - `createdAt`내림차순
- Request Body: 없음
- Response:`200 OK`,`List<AiDto.ConversationResponse>`

```json
[
  {
    "conversationId": 3,
    "title": "이번 주 납기 예정",
    "isClosed": false,
    "createdAt": "2026-10-24T16:40:00"
  }
]
```

### 10.3 대화방 제목 수정

- Method:`PATCH`
- URI:`/api/v1/ai/conversations/{conversationId}/title`
- 인증 필요 여부: 필수
  - 권한: 해당 대화방 소유자
- 처리 규칙
  - 제목만 저장하고`titleEdited`를`true`
  - 이후 첫 질문으로 제목을 덮어쓰지 않음
  - 공백은 거절함. 20자 이하임
  - 종료된 방은 제목을 바꾸지 않음
  - 응답 본문은 없음
- Request Body: 필수,`AiDto.TitleUpdateRequest`

```json
{
  "title": "변경한 날짜 납기일"
}
```

- Response:`204 No Content`

### 10.4 대화방 상세 조회

- Method:`GET`
- URI:`/api/v1/ai/conversations/{conversationId}`
- 인증 필요 여부: 필수
  - 권한: 해당 대화방 소유자
- 처리 규칙
  - 종료하지 않은 본인 대화방과 메시지를 반환
  - 메시지는`sequenceNo`오름차순임
  - `generatedSql`은 응답에 넣지 않음. 조회 결과만`resultJson`으로 줌
  - 없거나 본인 방이 아니거나 종료된 방이면`400`임
- Request Body: 없음
- Response:`200 OK`,`AiDto.ConversationDetailResponse`

```json
{
  "conversationId": 3,
  "title": "이번 주 납기 예정",
  "isClosed": false,
  "messages": [
    {
      "messageId": 11,
      "sequenceNo": 1,
      "role": "USER",
      "content": "이번 주 납기 예정인 수주",
      "resultJson": null,
      "createdAt": "2026-10-24T17:00:00"
    },
    {
      "messageId": 12,
      "sequenceNo": 2,
      "role": "ASSISTANT",
      "content": "이번 주 납기 예정 수주는 2건임.",
      "resultJson": "[{\"orderNo\":\"PF-2026-011\"}]",
      "createdAt": "2026-10-24T17:01:00"
    }
  ]
}
```

### 10.5 질문

- Method:`POST`
- URI:`/api/v1/ai/conversations/{conversationId}/messages`
- 인증 필요 여부: 필수
  - 권한: 해당 대화방 소유자
- 처리 규칙
  - 자연어 조건을 MCP 기반으로 실행함. 정해진 프롬프트와 조회 권한 안에서만 조회함.
  - 출력한 결과는 한국어와 마크업 언어로 답변함
  - 사용자 메시지와 응답 메시지를 순서대로 저장함
  - 응답 행에 실행한 SQL과 조회 결과 JSON을 저장함. SQL 원문은 응답에 넣지 않음
  - 첫 질문이고 제목을 직접 고치지 않았으면 질문 앞 20자를 제목으로 둠
  - 종료된 방에는 메시지를 추가하지 않음.`400`
  - 모델은`gemini-3.8-flash`,`gemini-3.6-flash`,`gemini-3.5-flash`,`gemini-3.5-flash-lite`,`gemini-3.1-flash-lite`,
    `openai/gpt-oss-20b`,`openai/gpt-oss-120b`임. 그 밖은`400`
- Request Body: 필수,`AiDto.AskRequest`

```json
{
  "model": "gemini-3.1-flash-lite",
  "message": "이번 주 납기 예정인 수주"
}
```

- Response:`200 OK`,`AiDto.AskResponse`

```json
{
  "model": "gemini-3.1-flash-lite",
  "answer": "이번 주 납기 예정 수주는 2건임."
}
```

### 10.6 대화방 종료

- Method:`PATCH`
- URI:`/api/v1/ai/conversations/{conversationId}/close`
- 인증 필요 여부: 필수
  - 권한: 해당 대화방 소유자
- 처리 규칙
  - `isClosed`를`true`로 하고`closedAt`을 기록함.
  - 행은 삭제하지 않음.
  - 종료 후 목록과 상세에서 빠짐
  - 그 방에는 질문과 제목 변경을 받지 않음
  - 이미 종료됐거나 본인 방이 아니면`400`
  - 응답 본문은 없음
- Request Body: 없음
- Response:`204 No Content`