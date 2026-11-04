# ProdFlow 프로젝트 산출물 종합 가이드 및 대시보드

## 목차

- [1. ProdFlow 프로젝트 개요](#1-prodflow-프로젝트-개요)
  - [1.1 서비스 소개](#11-서비스-소개)
  - [1.2 핵심 기술 스택](#12-핵심-기술-스택)
  - [1.3 팀 구성 및 도메인별 역할 분담](#13-팀-구성-및-도메인별-역할-분담)
- [2. 필수 산출물 요약 목록](#2-필수-산출물-요약-목록)
  - [2.1 기획 단계 산출물 (Planning)](#21-기획-단계-산출물-planning)
  - [2.2 설계 단계 산출물 (Design)](#22-설계-단계-산출물-design)
  - [2.3 구현 및 운영 보고 산출물 (Reports)](#23-구현-및-운영-보고-산출물-reports)
- [3. 필수 산출물 정의 및 작성 기준](#3-필수-산출물-정의-및-작성-기준)
  - [3.1 프로젝트 기획서 (Project Proposal)](#31-프로젝트-기획서-project-proposal)
  - [3.2 요구사항 기능 명세서 (PRD)](#32-요구사항-기능-명세서-prd)
  - [3.3 코드 컨벤션 정의서 (Code Convention Document)](#33-코드-컨벤션-정의서-code-convention-document)
  - [3.4 시스템 아키텍처 구성도 (System Architecture Diagram)](#34-시스템-아키텍처-구성도-system-architecture-diagram)
  - [3.5 데이터베이스 ERD (Entity Relationship Diagram)](#35-데이터베이스-erd-entity-relationship-diagram)
  - [3.6 화면 설계서 (UI Wireframe)](#36-화면-설계서-ui-wireframe)
  - [3.7 REST API 명세서 및 더미 데이터 규격서 (API Specification)](#37-rest-api-명세서-및-더미-데이터-규격서-api-specification)
  - [3.8 트러블슈팅 및 기술 문제 해결 기록 (Troubleshooting Log)](#38-트러블슈팅-및-기술-문제-해결-기록-troubleshooting-log)

---

# 1. ProdFlow 프로젝트 개요

## 1.1 서비스 소개

ProdFlow는 생산시설 임직원이 견적, 수주, 생산 예정, 출고, 배송, 잔금까지 한 흐름으로 다루고, 끝난 수주의 계획과 상태 이력을 남기는 **내부 생산·수주 관리 플랫폼**입니다. 외부 회원가입과 거래처용 화면은 없으며, 최고 관리자가 발급한 내부 계정으로만 접근합니다.

- 최고 관리자(`SUPER_ADMIN`): 계정 추가·수정·삭제, 기준정보·라인, 수주·생산 전 범위 업무
- 수주 관리자(`ORDER_MANAGER`): 견적·수주·거래처·품목 등록·수정, 라인 등록·생산능력 변경, 작업 담당자 변경
- 생산 현장 관리자(`PRODUCTION_MANAGER`): 배정된 작업의 시작·일시정지·생산 중지·포장, 라인 가동·점검·고장
- 업무 조회 담당(`INQUIRY_MANAGER`): 조회만 가능. 등록·수정·상태 변경 없음

핵심 흐름은 견적 작성 → 거래처 승인 → 수주 등록 → 계약금 입금 → 작업지시·라인 배정 → 생산·포장·출고·배송 → 잔금 확인이며, 대시보드·내부 알림·AI 업무 조회로 현황을 확인합니다. 공장 설비와 연결되어 있지 않아 실제 생산량·불량 수량은 수집하지 않습니다.

## 1.2 핵심 기술 스택

- 백엔드: Java 25, Spring Boot 4.1.x, Gradle 9.x, Spring Data JPA, Hibernate, QueryDSL 7.x, OpenFeign, Spring Security 7.x, JJWT 0.13, springdoc OpenAPI
- 프론트엔드: React 19.x, TypeScript 6.x, Vite 8.x, Tailwind CSS 4.x, Zustand 5.x, React Router 8.x, Axios
- 데이터베이스: MySQL 9.x (운영), H2 Database (테스트)
- 인프라 및 DevOps: AWS EC2, RDS(MySQL), S3, CloudFront, Docker, Docker Compose, GitHub Actions CI/CD (`main` 푸시 시 배포)
- 외부 연동: PortOne(가상계좌·입금 통지), 주소 검색 API, 공휴일 API, 택배사 배송조회 API, SMS/이메일 발송, AI 업무 조회(모델은 구현 시 선정)

## 1.3 팀 구성 및 도메인별 역할 분담

| 이름 | 직책 | 담당 도메인 | 세부 구현 내용 |
|---|---|---|---|
| 유동훈 | PM | 생산품목, 배송 조회 | 프로젝트 일정·범위 조율, 생산품목·기종·단가·도면, 품목 자동완성·모달 등록, 운송장·배송조회·SMS/이메일 배송 알림 |
| 이승언 | PL | 견적서 및 수주 관리 | 도메인 간 기술 연계, 견적·수주 등록, 납기 계산·재산정, VAT·총액·상태, 승인 견적 기반 수주 채움 |
| 김두희 | 팀원 | 거래처 | 거래처 등록·조회·수정, 주소 검색, 엑셀 대량 등록, 견적 작성 시 거래처 자동완성·없는 거래처 등록 |
| 박병찬 | 팀원 | 생산·라인, AI 챗봇 | 작업지시·생산계획·라인·담당자 배정, 작업 상태·변경 이력, AI 업무 조회·대화 관리 |
| 윤승영 | 팀원 | 대시보드 통계 | 수주 건수·계획 생산량·품목별 분포·납기·입금 현황, 일정 캘린더, 내부 알림 |
| 임민교 | 팀원 | 사용자, 입금 | 인증·권한·계정 상태, 계약금·잔금 결제 건·가상계좌·입금 등록·확인 |

담당 도메인은 위 표를 유지하며, 각 팀원은 화면·REST API·DB까지 수직 슬라이스로 구현합니다.

---

# 2. 필수 산출물 요약 목록

ProdFlow를 체계적으로 구축하기 위해 작성된 단계별 필수 산출물 문서 목록입니다.

## 2.1 기획 단계 산출물 (Planning)

| 산출물 번호 | 산출물 문서명 | 주요 내용 | 파일 링크 |
|---|---|---|---|
| 01 | 프로젝트 기획서 | 서비스 기획 배경, 업무 흐름, 주요 기능 요약 및 팀 구성 | [docs/01_planning/01_proposal.md](01_planning/01_proposal.md) |
| 02 | 요구사항 기능 명세서 (PRD) | 도메인별 요구사항 ID, RBAC 권한 매트릭스, 우선순위 | [docs/01_planning/02_prd.md](01_planning/02_prd.md) |
| 03 | 코드 컨벤션 정의서 | Java/Spring Boot 서식, 네이밍, 레이어드 아키텍처, Git 협업 규약 | [docs/01_planning/03_convention.md](01_planning/03_convention.md) |
| 04 | 비즈니스 프로세스 | 견적→수주→계약금→작업지시→배송→잔금 업무 규칙·상태 전이 | [docs/01_planning/04_business_process.md](01_planning/04_business_process.md) |

## 2.2 설계 단계 산출물 (Design)

| 산출물 번호 | 산출물 문서명 | 주요 내용 | 파일 링크 |
|---|---|---|---|
| 01 | 시스템 아키텍처 구성도 | React SPA, Spring Boot REST, MySQL, AWS, 외부 연동 흐름 | [docs/02_design/01_architecture.md](02_design/01_architecture.md) |
| 02 | 데이터베이스 ERD | 핵심 엔티티·FK 관계 및 Mermaid ERD | [docs/02_design/02_erd.md](02_design/02_erd.md) |
| 03 | 화면 설계서 (UI Wireframe) | 로그인·대시보드·거래처·품목·견적·수주·입금·작업지시·라인·배송·AI 화면 | [docs/02_design/03_ui_wireframe.md](02_design/03_ui_wireframe.md) |
| 04 | REST API 명세서 및 더미 데이터 규격서 | HTTP Method, URI, 요청/응답 JSON, Mock 규격 | [docs/02_design/04_api_specification.md](02_design/04_api_specification.md) |

## 2.3 구현 및 운영 보고 산출물 (Reports)

| 산출물 번호 | 산출물 문서명 | 주요 내용 | 파일 링크 |
|---|---|---|---|
| 01 | 트러블슈팅 및 기술 문제 해결 기록 | 개발·배포 중 발생한 기술 이슈의 원인·해결·시사점 | [docs/03_reports/troubleshooting.md](03_reports/troubleshooting.md) |

---

# 3. 필수 산출물 정의 및 작성 기준

## 3.1 프로젝트 기획서 (Project Proposal)

### 3.1.1 정의

프로젝트의 출발점으로서 ProdFlow의 목적, 배경, 핵심 업무 가치(견적·수주·생산·입금·배송 일원화), 팀원별 도메인 역할 분담을 명확히 규정하는 기본 기획 문서.

### 3.1.2 작성 필요성 및 목적

- 프로젝트 방향성 통일: 팀원 간 서비스 목표와 업무 모델(승인 견적 기반 수주, 계약금 후 작업지시, 계획 수량 중심 집계)에 대한 이해도를 일치시키고 기획 혼선 차단
- 개발 범위 명확화: 개발 일정에 맞는 현실적인 서비스 스코프 설정 및 프론트/백엔드 연동 목표 확립

### 3.1.3 필수 포함 항목

- 프로젝트명 및 한 줄 서비스 소개
- 프로젝트 기획 배경 및 목적
- 서비스 주요 특징 (견적·납기 계산, 수주·입금, 작업지시·라인, 배송·알림, 대시보드, AI 업무 조회)
- 주요 기능 요구사항 요약
- 팀 구성 및 도메인별 역할 분담표

- 프로젝트 기획서: [docs/01_planning/01_proposal.md](01_planning/01_proposal.md)

---

## 3.2 요구사항 기능 명세서 (PRD)

### 3.2.1 정의

시스템이 제공해야 하는 모든 세부 기능을 도출하고, 각 기능별 구현 조건, 대상 역할(RBAC), 우선순위를 목록화하여 관리하는 문서.

### 3.2.2 작성 필요성 및 목적

- 개발 스코프 제어: 구현할 필수 기능(1순위)과 선택 기능(2, 3순위)을 명확하게 구분
- 개발 구현 기준 제공: 기능별 입력/처리/출력 조건과 예외 규칙을 구체화하여 개발 오차 감소
- 인증·입금·상태 전이 명시: JWT 인증, PortOne 가상계좌·입금 통지, 수주/작업지시 상태 전이, 역할별 접근 제어를 사전에 정립

### 3.2.3 필수 포함 항목 및 작성 방법

- 요구사항 식별 ID: `USER-01`, `CLIENT-01`, `PRODUCT-01`, `ORDER-01`, `Quotation-01`, `PLAN-01`, `LINE-01`, `DELIVERY-01`, `PAYMENT-01`, `STAT-01`, `AI-01` 등 도메인별 고유 코드 부여
- 구분: 사용자, 거래처, 품목관리, 견적, 수주, 생산관리, 라인, 배송, 입금, 통계, AI 등 도메인 명시
- 기능명 및 대상 역할: `SUPER_ADMIN`, `ORDER_MANAGER`, `PRODUCTION_MANAGER`, `INQUIRY_MANAGER` 구분
- 우선순위: 1순위(필수 구현), 2순위(주요 권장), 3순위(추가 선택) 구분
- 상세 내용: 기능의 세부 동작 조건 및 입력 데이터 검증 규칙 명시
- RBAC 권한 매트릭스: 도메인·기능별 역할 허용 여부(O/X) 표

- 요구사항 기능 명세서: [docs/01_planning/02_prd.md](01_planning/02_prd.md)

---

## 3.3 코드 컨벤션 정의서 (Code Convention Document)

### 3.3.1 정의

팀원 전체가 하나의 개발자가 작성한 것처럼 일관된 코딩 스타일, 네이밍 규칙, 디렉토리 구조, 백엔드 레이어드 아키텍처 원칙(DTO/Entity 엄격 분리, 생성자 주입, 트랜잭션 경계 설정), Git 협업 규약을 명문화하여 준수하기 위한 개발 표준 규약 문서.

### 3.3.2 작성 필요성 및 목적

- 팀원 간 코드 가독성 제고: 통일된 서식과 명명법을 적용하여 타 팀원의 코드를 이해하는 시간과 인지 부하 감소
- 리뷰 생산성 극대화: 사소한 포맷팅이나 스타일 논쟁을 배제하고 핵심 비즈니스 로직과 설계 품질 검증에 집중
- 런타임 결함 예방: DTO와 Entity의 엄격한 분리, 생성자 주입 강제, `@Transactional(readOnly)` 최적화를 통해 순환 참조·지연 로딩 오류·메모리 누수를 원천 방지

### 3.3.3 필수 포함 항목

- 기본 언어 서식: 들여쓰기(공백 4칸), 최대 줄 너비(120자), K&R 중괄호 스타일, 와일드카드 import 금지
- 식별자 명명 규칙: 도메인 중심 패키지(`net.likelion.bebc25.prodflow.*`), 클래스 역할별 접미사(Controller, Service, Repository, Dto, Entity), 메서드 동사 규칙, 데이터베이스 스네이크 케이스
- 레이어드 아키텍처 및 구현 표준: 계층별 책임 분리, DTO와 Entity의 엄격한 분리, 불변 생성자 주입, 선언적 트랜잭션 최적화, 전역 예외 처리 표준
- 깃 협업 및 리뷰 컨벤션: Conventional Commits 커밋 규격, 브랜치 전략(`dev`, `feature/*`, `fix/*`), PR 자가 점검 체크리스트

- 코드 컨벤션 정의서: [docs/01_planning/03_convention.md](01_planning/03_convention.md)

---

## 3.4 시스템 아키텍처 구성도 (System Architecture Diagram)

### 3.4.1 정의

React SPA 클라이언트부터 Spring Boot REST API, MySQL 데이터베이스, AWS 클라우드 인프라, PortOne 결제/입금, 배송조회·공휴일·주소 API 및 AI 업무 조회 서비스 간의 전체적인 시스템 구조와 데이터 흐름을 시각화한 구성도.

### 3.4.2 작성 필요성 및 목적

- 클라이언트-서버 분리 아키텍처 이해: React 프론트엔드와 Spring Boot 백엔드 간 비동기 REST API 통신 정책 명확화
- AWS 클라우드 배포 구조 파악: S3 + CloudFront 정적 사이트 호스팅, EC2 + Docker Compose 백엔드 배포, S3 도면·미디어 스토리지 구성 확인
- 데이터 흐름 정립: JWT 인증, 가상계좌 입금 통지, 도면 S3 업로드, 배송조회·알림, AI 업무 조회 흐름 명시

### 3.4.3 필수 포함 항목

- 프론트엔드 호스팅 영역: AWS S3, CloudFront (CDN), 브라우저 React SPA
- 백엔드 애플리케이션 영역: AWS EC2, Nginx 리버스 프록시, Docker Compose, Spring Boot REST API (8080 포트)
- 데이터베이스 영역: MySQL (RDS 또는 컨테이너, 3306 포트), 볼륨/영속성 보장
- 외부 연동 및 스토리지 영역: AWS S3 도면 버킷, PortOne, 주소·공휴일·배송조회 API, SMS/이메일, AI API
- CI/CD: GitHub Actions — `main` 푸시 시 Docker 이미지 빌드·푸시 후 EC2 `docker compose` 재기동

- 시스템 아키텍처 구성도: [docs/02_design/01_architecture.md](02_design/01_architecture.md)

---

## 3.5 데이터베이스 ERD (Entity Relationship Diagram)

### 3.5.1 정의

시스템에서 사용하는 데이터 테이블 구조, 컬럼 데이터 타입, PK/FK 제약조건 및 테이블 간의 연관 관계(1:1, 1:N, N:M)를 규정한 개체 관계도.

### 3.5.2 작성 필요성 및 목적

- 데이터 무결성 확보: 견적·수주 계약 당시 값 보존, 모델코드·견적번호·수주번호 유일성, 입금 거래 식별값 중복 방지
- JPA 엔티티 연관관계 매핑 및 최적화: 다대일/일대다 매핑을 사전에 정립하고 N+1 및 다중 컬렉션 페치 조인 한계를 피한 조회 구조 확립

### 3.5.3 필수 포함 항목

- 테이블명 및 컬럼 명세: PK, FK, Not Null, Unique, Default 제약조건 명시
- 핵심 도메인 테이블 예시: `user`, `refresh_token`, `client`, `code_group`, `common_code`, `product`, `product_drawing`, `quotation`, `quotation_item`, `customer_order`, `order_product`, `payment`, `virtual_account`, `payment_history`, `work_order`, `production_plan`, `work_order_history`, `line`, `carrier`, `shipment`, `holiday`, `ai_conversation`, `ai_message` 등
- 작성 방식: 마크다운 내부 Mermaid `erDiagram` 문법으로 시각적 다이어그램 렌더링

### 3.5.4 Mermaid ERD 주요 문법

- Mermaid ERD 공식 문법 문서: [https://docs.mermaidviewer.com/diagrams/er.html](https://docs.mermaidviewer.com/diagrams/er.html)
- Mermaid ERD 관계 표현 기호 규칙:
  - 1:1 관계 (필수 1 대 1): `||--||` (양쪽 모두 1개 필수)
  - 1:1 관계 (필수 1 대 선택 0 또는 1): `||--o|` (한쪽은 1개 필수, 반대쪽은 0개 또는 1개)
  - 1:N 관계 (필수 1 대 1 이상 N): `||--|{` (한쪽은 1개 필수, 반대쪽은 1개 이상 필수)
  - 1:N 관계 (필수 1 대 0 이상 N): `||--o{` (한쪽은 1개 필수, 반대쪽은 0개 이상 선택)
  - N:M 관계 (1 이상 N 대 1 이상 M): `}|--|{` (양쪽 모두 1개 이상 필수)
  - N:M 관계 (1 이상 N 대 0 이상 M): `}|--o{` (한쪽은 1개 이상 필수, 반대쪽은 0개 이상 선택)
- 관계선 기호 가이드:
  - `||`: 정확히 1개 (Exactly one)
  - `|{`: 1개 이상 (One or more)
  - `o{`: 0개 이상 (Zero or more)
  - `o|`: 0개 또는 1개 (Zero or one)
- 기호 구성 요소 해설:
  - Straight Line (`|`): 최소 1개 이상 존재해야 함 (Mandatory 필수 조건)
  - Circle (`o`): 0개일 수 있음 (Optional 선택 조건, 없어도 됨)
  - Crow Foot (`}` 또는 `{`): 다수 (Many, N개)를 의미

- 데이터베이스 ERD: [docs/02_design/02_erd.md](02_design/02_erd.md)

---

## 3.6 화면 설계서 (UI Wireframe)

### 3.6.1 정의

사용자 관점에서의 ProdFlow 화면 레이아웃, 목록·상세·등록 폼, 견적/수주 흐름, 대시보드·AI 화면 및 화면 간 이동을 시각적으로 구성한 화면 뼈대 설계 문서.

### 3.6.2 작성 필요성 및 목적

- AI 바이브 코딩 연계 최적화: 시각적 와이어프레임과 구성 요소 명세를 AI에게 전달하여 신속하고 일관된 React 컴포넌트 생성 유도
- 기획 오차 감소: 거래처·품목·견적·수주·입금·작업지시·라인·배송 화면 배치를 미리 정의하여 프론트엔드 재작업 방지

### 3.6.3 필수 포함 항목

- 주요 화면 목록: 로그인, 대시보드, 내 정보, 계정 관리, 거래처, 생산품목, 견적서, 수주, 입금, 작업지시서, 생산라인, 생산 일정 캘린더, 배송, AI 챗봇
- 출력 데이터 항목(Output Data) 및 화면 제어/동작 규칙(Behavior Rules) 수록
- 역할별 메뉴·버튼 노출 차이 명시

### 3.6.4 와이어프레임 도구 및 산출물 관리 가이드

- tldraw 웹사이트: [https://www.tldraw.com/](https://www.tldraw.com/)
- Excalidraw 웹사이트: [https://excalidraw.com/](https://excalidraw.com/)
- 제작 팁:
  - 복잡한 픽셀 단위 작업 대신 사각형 박스와 텍스트를 활용한 초간단 와이어프레임 또는 종이 손스케치를 캡처하여 활용
  - 이미지 파일은 `docs/images/` 폴더에 저장하고 마크다운에서 상대 경로로 링크하여 관리

- 화면 설계서: [docs/02_design/03_ui_wireframe.md](02_design/03_ui_wireframe.md)

---

## 3.7 REST API 명세서 및 더미 데이터 규격서 (API Specification)

### 3.7.1 정의

프론트엔드와 백엔드가 JSON 비동기 통신을 수행하기 위해 사전에 합의하는 엔드포인트 URI, HTTP 메서드, 요청/응답 DTO 및 프론트 더미 데이터 규격 문서.

### 3.7.2 작성 필요성 및 목적

- 병렬 개발 보장: 백엔드 API 완성을 기다리지 않고, 프론트엔드가 정의된 더미 JSON 데이터를 활용해 UI와 기능을 먼저 완성(바이브 코딩) 가능
- 손쉬운 연동 전환: 실제 API 완성 시 더미 데이터를 실제 API 호출 엔드포인트로 즉시 교체하여 연동 소요 시간 극소화
- 데이터 규격 통일: 공통 DTO 포맷과 전역 예외 응답 규격을 사전에 정의하여 프론트엔드 예외 처리 공통화

### 3.7.3 필수 포함 항목

- 공통 응답 규격: 단건 및 페이징 목록 DTO 응답, 전역 에러 포맷
- 인증/사용자 API: 로그인, Refresh Token 갱신, 로그아웃, 비밀번호 변경, 내 정보, 계정 CRUD(최고 관리자)
- 거래처 API: 등록·목록·상세·수정·검색·엑셀 일괄 등록
- 생산품목 API: 등록·목록·상세·수정·검색·상태 변경·도면 업로드/조회·모델코드 중복 검사·기종(common-codes)
- 견적/수주 API: 견적 작성·승인·거절·취소·납기 재산정, 수주 등록·목록·상세·수량 수정·상태 전이
- 입금 API: 결제 건·가상계좌·입금 통지 처리·수동 입금·입금 내역 조회
- 생산/라인 API: 작업지시 목록·상세·수정·상태 변경, 생산계획, 라인 CRUD·상태
- 배송 API: 운송장 등록, 배송조회, 배송 상태 전이, 거래처 알림
- 대시보드/통계 API: 수주·생산·납기·입금 지표, 품목별 비중, 납기 위험
- AI API: 업무 조회 대화·메시지

- REST API 명세서: [docs/02_design/04_api_specification.md](02_design/04_api_specification.md)

---

## 3.8 트러블슈팅 및 기술 문제 해결 기록 (Troubleshooting Log)

### 3.8.1 정의

개발 및 배포 과정에서 직면한 런타임 오류, 쿼리 병목, 인증·권한 이슈, 외부 연동 실패, 배포 파이프라인 장애 등 기술적 문제의 발생 원인, 해결 방법, 시사점을 기록하는 문서.

### 3.8.2 작성 필요성 및 목적

- 문제 재발 방지: 작성된 해결 기록을 공유하여 팀원들이 동일한 버그로 시간을 낭비하는 현상 차단
- 기술적 자산화: JPA N+1/페치 조인, JWT·RBAC, PortOne 입금 멱등 처리, S3 도면 업로드, Docker/GitHub Actions 배포, AI 조회 SQL 검증 등 실무 수준의 기술 문제 해결 경험을 면접 및 포트폴리오 핵심 자산으로 활용

### 3.8.3 필수 포함 항목 및 작성 템플릿

- 작성자별 식별자 규칙: 동시 편집 충돌 방지를 위한 `TS-[작성자영문]-[순번]` 태그 사용  
  (예: `TS-DONGHOON-01`, `TS-SEUNGEON-01`, `TS-DOOHEE-01`, `TS-BYEONGCHAN-01`, `TS-SEUNGYEONG-01`, `TS-MINKYO-01`)
- 복사용 표준 템플릿:

````markdown
## TS-[작성자영문]-[순번]: [발생한 문제 및 에러 제목]

### 1) 발생 현상 및 에러
- 런타임 현상 설명 및 발생한 에러 로그 원문/메시지 수록.

### 2) 원인 분석
- 해당 문제나 버그가 발생한 근본 원인 및 코드 흐름 분석.

### 3) 해결 방법
- 문제를 해결하기 위해 변경하거나 새로 적용한 해결 코드 반영.

```java
// 해결 핵심 소스 코드 반영
```

### 4) 시사점 및 배운 점
- 버그 해결을 통해 새로 배우게 된 원리나 향후 재발 방지를 위한 시사점 작성.
````

- 트러블슈팅 작성 샘플: [docs/03_reports/troubleshooting.md](03_reports/troubleshooting.md)
