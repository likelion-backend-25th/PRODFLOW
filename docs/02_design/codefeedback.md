# 1. 2팀(PRODFLOW) 백엔드 소스코드 검토 피드백

## 1.1 코드 컨벤션

### 1.1.1 패키지 명명 규칙 위반 (스네이크케이스 및 오타)

- 현황:
    - 도메인 패키지명에 언더스코어(snake_case)가 혼용되어 있음 (customer_order, client_notification, line_notification, order_notification,
      work_order).
    - 일부 도메인 하위 패키지 디렉터리에 철자 오타가 존재함 (line.serivce, work_order.serivce, line.repsitory, work_order.repsitory).
-
문제점: [컨벤션 3.1 패키지 명명 규칙](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/docs/01_planning/03_convention.md#31-패키지-명명-규칙)
위반 (소문자 알파벳 단어 결합 원칙, 언더스코어 및 대문자 사용 금지). IDE 자동 완성 및 클래스 임포트 시 혼란을 유발하고 프로젝트 구조의 일관성을 저해함.
- 대표 문제 사례:
    - [LineService.java](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/line/serivce/LineService.java) (
      패키지 경로: line.serivce)
    - [LineRepository.java](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/line/repsitory/LineRepository.java) (
      패키지 경로: line.repsitory)
    - [WorkOrderService.java](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/work_order/serivce/WorkOrderService.java) (
      패키지 경로: work_order.serivce)
    - [WorkOrderRepository.java](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/work_order/repsitory/WorkOrderRepository.java) (
      패키지 경로: work_order.repsitory)
- 조치: 오타가 발생한 패키지명(serivce -> service, repsitory -> repository)을 정상화하고, 언더스코어가 포함된 패키지명은 단일 소문자 단어(order, workorder,
  notification) 또는 점(.)으로 구분된 하위 계층으로 리팩토링.

### 1.1.2 컨트롤러 요청 매핑 경로 선행 슬래시 누락

- 현황: 일부 컨트롤러의 클래스 레벨 @RequestMapping 선언부에 시작 슬래시(/)가 누락되어 상대 경로 형태로 작성됨.
-
문제점: [컨벤션 3.6 API 명명 규칙](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/docs/01_planning/03_convention.md#36-api-명명-규칙)
위반 (공통 접두 URI /api/v1 표준). 프레임워크가 정상 처리하더라도 팀 내 URI 표기 일관성이 결여됨.
- 모범 사례:
    - [AuthController.java#L34](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/auth/controller/AuthController.java#L34)
      ```java
      // 시작 슬래시를 포함한 절대 경로 매핑 표준 준수
      @RestController
      @RequestMapping("/api/v1/auth")
      @RequiredArgsConstructor
      public class AuthController {
      ```
- 대표 문제 사례:
    - [ClientController.java#L31](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/client/controller/ClientController.java#L31)
      ```java
      @RestController
      @RequestMapping("api/v1/clients")
      @RequiredArgsConstructor
      public class ClientController {
      ```
    - [WorkOrderController.java#L32](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/work_order/controller/WorkOrderController.java#L32)
      ```java
      @RestController
      @RequestMapping("api/v1/work-orders")
      @RequiredArgsConstructor
      public class WorkOrderController {
      ```
- 조치: 모든 컨트롤러의 클래스 레벨 매핑 경로를 `@RequestMapping("/api/v1/...")` 형태로 일관되게 수정.

### 1.1.3 코드 한 줄 최대 길이(120자) 초과 및 개행 누락

- 현황: 메서드 파라미터가 다수 나열될 때 줄바꿈 없이 한 줄에 선언되어 120자를 초과(최대 145자)함.
-
문제점: [컨벤션 2.1 기본 서식 규칙](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/docs/01_planning/03_convention.md#21-기본-서식-규칙)
위반 (줄 너비 120자 제한 원칙). 가로 스크롤을 유발하여 코드 가독성 저하.
- 모범 사례:
    - [ClientDto.java#L38-L48](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/client/dto/ClientDto.java#L38-L48)
      ```java
      // 쉼표 뒤 줄바꿈을 통해 가독성 및 120자 제한 준수
      return new Response(
              client.getId(),
              client.getCompany(),
              client.getEmployee(),
              client.getPhone(),
              client.getEmail(),
              totalOrder,
              totalAmount,
              recentOrderedAt
      );
      ```
- 대표 문제 사례:
    - [WorkOrderService.java#L244](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/work_order/serivce/WorkOrderService.java#L244)
      ```java
      // 145자 단일 라인 선언으로 가독성 저하
      public Page<WorkOrderDto.PageResponse> findAll(Long lineId, String status, LocalDate from, LocalDate to, String keyword, Pageable pageable) {
      ```
- 조치:
    - 매개변수가 많을 경우 쉼표(,) 뒤에서 개행하고 들여쓰기를 적용하거나, 검색 조건들을 묶은 SearchCondition DTO(Record)로 매개변수를 추상화하여 간결하게 리팩토링.
      ```java
      public Page<WorkOrderDto.PageResponse> findAll(
              Long lineId,
              String status,
              LocalDate from,
              LocalDate to,
              String keyword,
              Pageable pageable
      ) {
      ```
    - IntelliJ IDEA 가이드라인 설정:
        - Settings -> Editor -> Code Style에서 Visual guides: 120만 켜두면 에디터 오른쪽에 은은한 세로선이 표시됨. 코드가 120자를 넘는지 시각적으로 확인하면서 작성
          가능함.
        - 파라미터가 3개 이상이거나 점(.), 쉼표(,) 뒤에서 길다 싶을 때 수동으로 엔터를 쳐서 의미 단위로 정렬하는 것을 권장.

---

### 1.2.2 BaseTimeEntity 미상속 및 엔티티별 개별 감사 필드 난립

- 현황:
    - Payment, User, Shipment, VirtualAccount 등 다수의 핵심 업무 엔티티가 BaseTimeEntity를 상속받지 않고 개별 필드로 감사 컬럼을 선언하거나 누락함.
    - Payment는 @CreationTimestamp createdAt 및 createdBy만 선언하여 updatedAt, updatedBy가 누락됨.
    - User는 @CreationTimestamp createdAt, @UpdateTimestamp updatedAt만 선언하여 작성자/수정자 추적이 불가능함.
    - Shipment는 생성일시, 수정일시 등 기본 감사 컬럼이 전체 누락됨.
- 문제점: 데이터베이스 전반의 이력 감사 정책이 파편화되고, 테이블마다 컬럼 명칭과 타입 규칙이 달라져 유지보수성 저하.
- 모범 사례:
    - [CustomerOrder.java#L33](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/customer_order/entity/CustomerOrder.java#L33)
      ```java
      public class CustomerOrder extends BaseTimeEntity {
      ```
    - [Product.java#L23](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/product/entity/Product.java#L23)
      ```java
      public class Product extends BaseTimeEntity {
      ```
- 대표 문제 사례:
    - [Payment.java#L57-L64](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/payment/entity/Payment.java#L57-L64)
    - [User.java#L49-L58](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/user/entity/User.java#L49-L58)
    - [Shipment.java#L21-L59](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/shipment/entity/Shipment.java#L21-L59)
- 조치: 개별 선언된 감사 컬럼(@CreationTimestamp, @UpdateTimestamp)을 제거하고, 엔티티 특성에 맞게 BaseEntity 또는 BaseTimeEntity를 상속하도록 일원화.

### 1.2.3 객체 연관관계 대신 단순 Long ID 외래키 선언

-
현황: [Shipment.java#L28-L29](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/shipment/entity/Shipment.java#L28-L29)
에서 수주(CustomerOrder) 엔티티를 객체 연관관계로 매핑하지 않고 `private Long orderId;`로 단순 숫자형 외래키 값만 선언함.
- 문제점: JPA의 핵심 이점인 객체 그래프 탐색(shipment.getOrder())이 불가능하고, 지연 로딩(LAZY) 및 페치 조인 최적화를 적용할 수 없으며 데이터베이스 차원의 외래키 무결성 제약이 훼손됨.
- 모범 사례:
    - [Payment.java#L33-L37](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/payment/entity/Payment.java#L33-L37)
      ```java
      // 엔티티 객체 매핑 및 외래키 제약조건 명시
      @ManyToOne(fetch = FetchType.LAZY)
      @JoinColumn(name = "order_id", nullable = false)
      private CustomerOrder order;
      ```
- 문제 클래스:
    - [Shipment.java#L28-L29](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/shipment/entity/Shipment.java#L28-L29)
      ```java
      @Column(name = "order_id", nullable = false, unique = true)
      private Long orderId;
      ```
- 조치: 단순 Long 필드를 JPA 연관관계인
  `@OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_id", nullable = false, unique = true)`로 변경하여 엔티티 탐색 지원.

---

## 1.3 서비스 트랜잭션 및 비즈니스 방어

### 1.3.1 서비스 클래스 레벨 기본 쓰기 트랜잭션(@Transactional) 선언 오류

-
현황: [ProductService.java#L27](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/product/service/ProductService.java#L27)
및 [ProductDrawingService.java#L22](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/product/service/ProductDrawingService.java#L22)
클래스 상단에 `@Transactional(readOnly = true)`가 아닌 `@Transactional`이 선언됨.
-
문제점: [컨벤션 4.4 선언적 트랜잭션 관리 표준](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/docs/01_planning/03_convention.md#44-선언적-트랜잭션transactional-관리-표준)
위반. 클래스 내의 모든 단순 조회 메서드(`search`, `getById` 등)까지 쓰기 트랜잭션으로 동작하여 불필요한 영속성 컨텍스트 스냅샷 생성 및 힙 메모리 낭비 유발.
- 모범 사례:
    - [ClientService.java#L30-L38](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/client/service/ClientService.java#L30-L38)
      ```java
      // 클래스 레벨 readOnly = true 선언 및 쓰기 메서드 @Transactional 개별 명시
      @Service
      @Transactional(readOnly = true)
      @RequiredArgsConstructor
      public class ClientService {
  
          @Transactional
          public ClientDto.DetailResponse createClient(...) {
      ```
    - [QuotationService.java#L48-L52](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/quotation/service/QuotationService.java#L48-L52)
- 대표 문제 사례:
    - [ProductService.java#L26-L29](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/product/service/ProductService.java#L26-L29)
      ```java
      @Service
      @Transactional
      @RequiredArgsConstructor
      public class ProductService {
      ```
    - [ProductDrawingService.java#L21-L24](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/product/service/ProductDrawingService.java#L21-L24)
- 조치: 클래스 상단에 `@Transactional(readOnly = true)`를 선언하고, 데이터 등록/수정/삭제를 수행하는 메서드에만 `@Transactional`을 개별 부여.

---

## 1.4 JPA 연관관계 및 쿼리 최적화

### 1.4.1 목록 조회 시 일반 JOIN 사용 및 지연 로딩 N+1 쿼리 발생

- 현황:
    - [WorkOrderRepository.java#L20-L46](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/work_order/repsitory/WorkOrderRepository.java#L20-L46)
      의 `search()` JPQL 쿼리에서 `join workOrder.orderProduct`, `join orderProduct.customerOrder`,
      `join customerOrder.client`처럼 일반 INNER JOIN만 작성되어 있고 `FETCH JOIN`이 누락됨. 또한 `line`, `user`, `commonCode`는 JOIN 절
      자체에 포함되어 있지 않음.
    - [WorkOrderRepository.java#L17](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/work_order/repsitory/WorkOrderRepository.java#L17)
      의 `findAllByOrderByPlannedEndDateAscIdDesc(pageable)`은 연관 엔티티 조인 없이 `WorkOrder`만 단독 조회함.
    -
    서비스([WorkOrderService.java#L252-L258](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/work_order/serivce/WorkOrderService.java#L252-L258))
    에서 목록 조회
    결과를 [WorkOrderDto.java#L57-L71](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/work_order/dto/WorkOrderDto.java#L57-L71)
    의 `PageResponse::from`으로 변환할 때, `orderProduct`, `customerOrder`, `client`, `line`, `user`, `commonCode` 총 6개의 연관
    엔티티를 getter로 직접 접근함.
- 문제점:
    - 전형적인 지연 로딩(LAZY) N+1 성능 결함. 페이징 단위인 10건만 조회하더라도 행마다 6개의 연관 엔티티를 초기화하기 위해 최대 60회의 추가 SELECT 쿼리가 연속 실행되어 데이터베이스 부하가
      증가하고 API 응답 속도가 저하됨.
    - 일반 JOIN은 WHERE 절 검색 조건 필터링에는 동작하지만 SELECT 절에는 오직 `WorkOrder` 엔티티만 적재되므로, 연관 엔티티 접근 시 프록시 객체가 개별 쿼리로 초기화됨.
- 모범 사례:
    - [LineRepository.java#L19-L43](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/line/repsitory/LineRepository.java#L19-L43)
      ```java
      // DTO 생성자 프로젝션(select new ...)을 통해 필요한 컬럼만 단일 쿼리로 조회 (N+1 원천 차단)
      @Query("""
              select new net.likelion.bebc25.prodflow.line.dto.LineDto$LineList(
                  l.id,
                  l.lineName,
                  l.status,
                  l.productionCapacity,
                  wo.workNo,
                  op.productName,
                  co.orderNo,
                  u.name
              )
              from Line l
              left join CommonCode cc
                  on cc.code = 'PRODUCE'
                 and cc.codeGroup.groupCode = 'WORK_ORDER_STATUS'
              left join WorkOrder wo
                  on wo.line = l
                 and wo.commonCode = cc
              left join OrderProduct op
                  on op = wo.orderProduct
              left join CustomerOrder co
                  on co = op.customerOrder
              left join User u
                  on u = wo.user
              order by l.id asc, wo.id asc
              """)
      List<LineDto.LineList> findLineList();
      ```
    - [OrderProductRepository.java#L14-L23](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/customer_order/repository/OrderProductRepository.java#L14-L23)
      ```java
      // JOIN FETCH를 통한 연관 엔티티 단일 쿼리 일괄 적재
      @Query("""
              SELECT op
              FROM OrderProduct op
              JOIN FETCH op.customerOrder co
              JOIN FETCH co.client
              JOIN FETCH co.statusCode
              WHERE op.product.id = :productId
              ORDER BY co.createdAt DESC
              """)
      List<OrderProduct> findRecentByProductId(@Param("productId") Long productId, Pageable pageable);
      ```
- 대표 문제 사례:
    - [WorkOrderRepository.java#L20-L46](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/work_order/repsitory/WorkOrderRepository.java#L20-L46)
      ```java
      // FETCH 키워드가 누락된 일반 JOIN (SELECT 시점 연관 엔티티 미로딩)
      @Query("""
              select workOrder
              from WorkOrder workOrder
              join workOrder.orderProduct orderProduct
              join orderProduct.customerOrder customerOrder
              join customerOrder.client client
              where (:lineId is null or workOrder.line.id = :lineId)
                and (:status is null or workOrder.commonCode.code = :status)
                and (:from is null or workOrder.workOrderDate >= :from)
                and (:to is null or workOrder.workOrderDate <= :to)
                and (
                      :keyword is null
                      or customerOrder.orderNo like concat('%', :keyword, '%')
                      or orderProduct.productName like concat('%', :keyword, '%')
                      or client.company like concat('%', :keyword, '%')
                      or workOrder.user.name like concat('%', :keyword, '%')
                )
              order by workOrder.plannedEndDate asc, workOrder.id desc
              """)
      Page<WorkOrder> search(
              @Param("lineId") Long lineId,
              @Param("status") String status,
              @Param("from") LocalDate from,
              @Param("to") LocalDate to,
              @Param("keyword") String keyword,
              Pageable pageable);
      ```
    - [WorkOrderDto.java#L57-L71](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/work_order/dto/WorkOrderDto.java#L57-L71)
      ```java
      // 매 행마다 6개 연관 엔티티를 getter로 순회 호출하여 60회 추가 SELECT 발생
      public static PageResponse from(WorkOrder workOrder) {
          return new PageResponse(
                  workOrder.getId(),
                  workOrder.getWorkNo(),
                  workOrder.getOrderProduct().getCustomerOrder().getOrderNo(),
                  workOrder.getOrderProduct().getCustomerOrder().getClient().getCompany(),
                  workOrder.getOrderProduct().getProductName(),
                  workOrder.getQuantity(),
                  workOrder.getLine().getLineName(),
                  workOrder.getUser().getName(),
                  workOrder.getWorkOrderDate(),
                  workOrder.getPlannedEndDate(),
                  workOrder.getCommonCode().getCode()
          );
      }
      ```
- 조치:
    - 페이징 목록 조회 시 연관 엔티티 6개를 단일 SQL로 한 번에 함께 조회하도록 `JOIN FETCH`를 선언하고, 페이징 계산을 위한 카운트 쿼리(`countQuery`)를 최적화하여 명시.
    - 검색 조건이 없을 때 호출되는 `findAllByOrderByPlannedEndDateAscIdDesc` 메서드 역시 연관관계를 일괄 조회하도록 `JOIN FETCH` 전용 메서드로 대체하거나
      `search(null, null, null, null, null, pageable)`로 호출 일원화.
  ```java
  // WorkOrderRepository.java
  // 1. 조건 검색: ToOne 연관 엔티티 일괄 Fetch Join 및 최적화된 카운트 쿼리 분리
  @Query(
      value = """
              SELECT wo
              FROM WorkOrder wo
              JOIN FETCH wo.orderProduct op
              JOIN FETCH op.customerOrder co
              JOIN FETCH co.client c
              JOIN FETCH wo.line l
              JOIN FETCH wo.user u
              JOIN FETCH wo.commonCode cc
              WHERE (:lineId IS NULL OR wo.line.id = :lineId)
                AND (:status IS NULL OR wo.commonCode.code = :status)
                AND (:from IS NULL OR wo.workOrderDate >= :from)
                AND (:to IS NULL OR wo.workOrderDate <= :to)
                AND (
                      :keyword IS NULL
                      OR co.orderNo LIKE CONCAT('%', :keyword, '%')
                      OR op.productName LIKE CONCAT('%', :keyword, '%')
                      OR c.company LIKE CONCAT('%', :keyword, '%')
                      OR u.name LIKE CONCAT('%', :keyword, '%')
                )
              ORDER BY wo.plannedEndDate ASC, wo.id DESC
              """,
      countQuery = """
              SELECT COUNT(wo)
              FROM WorkOrder wo
              JOIN wo.orderProduct op
              JOIN op.customerOrder co
              JOIN co.client c
              JOIN wo.user u
              WHERE (:lineId IS NULL OR wo.line.id = :lineId)
                AND (:status IS NULL OR wo.commonCode.code = :status)
                AND (:from IS NULL OR wo.workOrderDate >= :from)
                AND (:to IS NULL OR wo.workOrderDate <= :to)
                AND (
                      :keyword IS NULL
                      OR co.orderNo LIKE CONCAT('%', :keyword, '%')
                      OR op.productName LIKE CONCAT('%', :keyword, '%')
                      OR c.company LIKE CONCAT('%', :keyword, '%')
                      OR u.name LIKE CONCAT('%', :keyword, '%')
                )
              """
  )
  Page<WorkOrder> search(
          @Param("lineId") Long lineId,
          @Param("status") String status,
          @Param("from") LocalDate from,
          @Param("to") LocalDate to,
          @Param("keyword") String keyword,
          Pageable pageable
  );

  // 2. 전체 목록 조회: 동일하게 Fetch Join 및 countQuery 분리 적용
  @Query(
      value = """
              SELECT wo
              FROM WorkOrder wo
              JOIN FETCH wo.orderProduct op
              JOIN FETCH op.customerOrder co
              JOIN FETCH co.client c
              JOIN FETCH wo.line l
              JOIN FETCH wo.user u
              JOIN FETCH wo.commonCode cc
              ORDER BY wo.plannedEndDate ASC, wo.id DESC
              """,
      countQuery = "SELECT COUNT(wo) FROM WorkOrder wo"
  )
  Page<WorkOrder> findAllWithDetails(Pageable pageable);
  ```

### 1.4.2 동일 엔티티에 대한 다중 JpaRepository 분산 선언

- 현황:
    - [NotificationUserRepository.java#L11-L14](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/line_notification/repository/NotificationUserRepository.java#L11-L14)
      에서 `User` 엔티티를 대상으로 하는 `JpaRepository<User, Long>`을 별도 인터페이스로 생성하여 `findByRoleAndStatus`를 선언함.
- 문제점:
    - 이미 `user`
      패키지에 [UserRepository.java](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/user/repository/UserRepository.java)
      가 존재하므로, 도메인 모듈(line_notification) 편의를 위해 동일한 엔티티를 관리하는 리포지토리를 중복 생성하여 영속성 접근 책임이 분산되고 유지보수 시 혼선을 초래함.
- 조치:
    - `NotificationUserRepository`를 삭제하고, `findByRoleAndStatus` 메서드를 기존 `UserRepository`에 추가한 후, 알림 서비스에서
      `UserRepository`를 주입받아 사용하도록 통합(단일 엔티티-단일 리포지토리 원칙 준수).

---

## 1.5 DTO 및 계층 간 책임 분리

### 1.5.1 DTO 유효성 검증 어노테이션 및 컨트롤러 @Valid 누락

- 현황:
    - [ClientDto.java#L13-L21](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/client/dto/ClientDto.java#L13-L21)
      의 `Request` Record 필드(회사명, 담당자 및 외래키 userId 등)에 `@NotBlank`, `@NotNull` 등의 Bean Validation 어노테이션이 전혀 선언되지 않음.
    - [WorkOrderDto.java#L10-L18](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/work_order/dto/WorkOrderDto.java#L10-L18)
      의 `CreateRequest` Record 필드(orderProductId, lineId, assignedId 등 핵심 외래키)에 `@NotNull` 검증 어노테이션이 누락됨.
    - [ClientController.java#L46](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/client/controller/ClientController.java#L46)
      및 [WorkOrderController.java#L52](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/work_order/controller/WorkOrderController.java#L52)
      의 `@RequestBody` 파라미터에 `@Valid` 어노테이션이 누락됨.
- 문제점:
    - [컨벤션 4.1 계층 간 책임 분리 원칙](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/docs/01_planning/03_convention.md#41-계층-간-책임-분리-원칙)
      위반 (클라이언트 요청 파라미터 바인딩 및 `@Valid` 기반 유효성 검증 수행 원칙).
    - 클라이언트가 필수 외래키 식별자(userId, orderProductId 등)를 누락하여 null이 인입되었을 때, 1차 진입 관문인 컨트롤러에서 400 Bad Request(
      MethodArgumentNotValidException)로 조기에 걸러내지 못함.
    - 검증되지 않은 null 식별자가 서비스 계층을 거쳐 Spring Data JPA의 SimpleJpaRepository(findById) 내부까지 침투하여 IllegalArgumentException을
      유발하며, 어떤 필드가 누락되었는지 명확한 에러 메시지를 클라이언트에 제공하지 못함.
- 모범 사례:
    - [UserController.java#L33](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/user/controller/UserController.java#L33)
      ```java
      // @Valid 선언을 통해 Request DTO 유효성 검증 강제
      @PostMapping
      public ResponseEntity<UserDto.Response> createUser(
              @Valid @RequestBody UserDto.CreateRequest request
      ) {
      ```
    - [UserDto.java#L16-L35](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/user/dto/UserDto.java#L16-L35)
      ```java
      public record CreateRequest(
              @Email(message = "이메일 형식이 아닙니다.")
              @NotBlank(message = "이메일을 입력해주세요.")
              String email,
              @NotBlank(message = "임시비밀번호를 발급받아 주세요.")
              String temporaryPassword,
              @NotBlank(message = "이름을 입력해주세요.")
              String name,
              @NotBlank(message = "전화번호를 입력해주세요.")
              String phone,
              @NotNull(message = "권한을 설정해주세요.")
              UserRole role
      ) {}
      ```
- 대표 문제 사례:
    - [ClientController.java#L46](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/client/controller/ClientController.java#L46) & [ClientDto.java#L13-L21](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/client/dto/ClientDto.java#L13-L21)
      ```java
      // 컨트롤러 @Valid 누락
      public ResponseEntity<ClientDto.DetailResponse> createClient(
              @AuthenticationPrincipal CustomUserDetails userDetails,
              @RequestBody ClientDto.Request request
      ) {
      ```
      ```java
      // DTO 필수 외래키 userId 및 일반 필드 검증 누락
      public record Request(
              String company,
              String employee,
              String email,
              String phone,
              Long userId,
              Address address
      ) {}
      ```
    - [WorkOrderController.java#L52](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/work_order/controller/WorkOrderController.java#L52) & [WorkOrderDto.java#L10-L18](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/work_order/dto/WorkOrderDto.java#L10-L18)
      ```java
      // DTO 필수 외래키 orderProductId, lineId, assignedId 검증 누락
      public record CreateRequest(
              Long orderProductId,
              Long lineId,
              Long assignedId,
              int quantity,
              LocalDate plannedStartDate,
              LocalDate plannedEndDate
      ) {}
      ```
- 조치:
    - Request DTO의 모든 필수 입력 필드 및 외래키 ID에 `@NotBlank`, `@NotNull`, `@Positive` 등 명확한 Bean Validation 어노테이션과 에러 메시지(
      message)를 선언.
    - Controller 메서드 파라미터에 `@Valid`를 부여하여 클라이언트 요청 진입 단계에서 400 Bad Request로 유효하지 않은 요청을 원천 차단.

### 1.5.2 RESTful API 규격 위반 (수정 API에 POST 사용)

-
현황: [ClientController.java#L59](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/client/controller/ClientController.java#L59)
에서 거래처 정보 수정 API를 `@PostMapping("/{id}")`로 선언함.
-
문제점: [컨벤션 3.6 API 명명 규칙](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/docs/01_planning/03_convention.md#36-api-명명-규칙)
위반 (RESTful API 표준 미준수). HTTP 메서드 시맨틱에 맞지 않으며 프론트엔드 및 외부 연동 시 혼선을 초래함. 수정일 경우 `@PutMapping("/{id}")` 또는 부분 수정을 위한
`@PatchMapping("/{id}")`를 사용해야 함.
- 모범 사례:
    - [UserController.java#L86-L91](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/user/controller/UserController.java#L86-L91)
      ```java
      // 수정 API에 표준 HTTP 메서드인 @PutMapping 선언
      @PutMapping("/{id}")
      public ResponseEntity<UserDto.Response> updateUser(
              @PathVariable Long id,
              @Valid @RequestBody UserDto.UpdateRequest request
      ) {
      ```
    - [ProductController.java#L181-L186](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/product/controller/ProductController.java#L181-L186)
- 문제 클래스:
    - [ClientController.java#L59](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/client/controller/ClientController.java#L59)
      ```java
      @PostMapping("/{id}")
      public ResponseEntity<ClientDto.DetailResponse> updateClient(){}
      ```
- 조치: 리소스 수정을 위한 표준 HTTP 메서드인 `@PutMapping("/{id}")` 또는 부분 수정을 위한 `@PatchMapping("/{id}")`로 변경.

---

## 1.6 전역 예외 처리 및 에러 응답 규격 정비

### 1.6.1 GlobalExceptionHandler 응답 바디 불일치 및 예외 상태 코드 오류

- 현황:
    - [GlobalExceptionHandler.java#L64-L68](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/global/exception/GlobalExceptionHandler.java#L64-L68)
      의 `handleAuthException`
      및 [GlobalExceptionHandler.java#L143-L148](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/global/exception/GlobalExceptionHandler.java#L143-L148)
      의 `handleResponseStatus`에서 표준 에러 객체(`ApiErrorResponse`)가 아닌 임의의 `Map<String, String>`(
      `Map.of("code", ..., "message", ...)`)을 반환함.
    - [GlobalExceptionHandler.java#L123-L129](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/global/exception/GlobalExceptionHandler.java#L123-L129)
      에서 비즈니스 로직 상의 객체 상태 불일치(예: 이미 완료된 주문, 수량 부족 등)를 나타내는 `IllegalStateException`을 무조건 권한 부족 오류인 `403 Forbidden`(
      ErrorCode.FORBIDDEN_OPERATION)으로 일괄 매핑함.
- 문제점:
    - 다른 모든 예외는 `ApiErrorResponse(status, message, code, errors)` 객체로 반환되는데 반해, 인증 예외와 ResponseStatus 예외만 `Map` 객체로 반환되어
      프론트엔드(TypeScript)에서 에러 응답 역직렬화 시 타입 불일치 및 런타임 오류 유발.
    - 객체 상태 오류인 `IllegalStateException`에 `403 Forbidden` 상태 코드가 반환되어 클라이언트 개발자가 접근 권한 문제로 오인하게 만듦 (비즈니스 상태 충돌은 400 Bad
      Request 또는 409 Conflict가 적합).
- 모범 사례:
    - [GlobalExceptionHandler.java#L70-L77](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/global/exception/GlobalExceptionHandler.java#L70-L77)
      ```java
      // 표준 에러 응답 객체(ApiErrorResponse)로 일관되게 반환
      @ExceptionHandler(MethodArgumentNotValidException.class)
      public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
          ApiErrorResponse response = ex.getMessage().isEmpty()
                  ? ApiErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE)
                  : ApiErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, ex.getMessage());
          return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus()).body(response);
      }
      ```
- 대표 문제 사례:
    - [GlobalExceptionHandler.java#L64-L68](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/global/exception/GlobalExceptionHandler.java#L64-L68)
      ```java
      // Map 반환으로 인한 프론트엔드 타입 규격 파괴
      @ExceptionHandler(AuthException.class)
      public ResponseEntity<Map<String, String>> handleAuthException(AuthException ex) {
          return ResponseEntity.status(ex.getStatus())
                  .body(Map.of("code", ex.getCode(), "message", ex.getMessage()));
      }
      ```
    - [GlobalExceptionHandler.java#L123-L129](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/global/exception/GlobalExceptionHandler.java#L123-L129)
      ```java
      // IllegalStateException을 권한 부족(403 FORBIDDEN)으로 잘못 매핑
      @ExceptionHandler(IllegalStateException.class)
      public ResponseEntity<ApiErrorResponse> handleIllegalStateException(IllegalStateException ex) {
          ApiErrorResponse response = ex.getMessage().isEmpty()
                  ? ApiErrorResponse.of(ErrorCode.FORBIDDEN_OPERATION)
                  : ApiErrorResponse.of(ErrorCode.FORBIDDEN_OPERATION, ex.getMessage());
          return ResponseEntity.status(ErrorCode.FORBIDDEN_OPERATION.getHttpStatus()).body(response);
      }
      ```
- 조치:
    - `GlobalExceptionHandler`의 모든 핸들러 반환 타입을 `ApiErrorResponse`로 일원화하여 프론트엔드와의 에러 응답 인터페이스 규격을 일치화.
    - `IllegalStateException`을 비즈니스 규칙 위반 또는 상태 충돌에 부합하는 `400 Bad Request`(또는 `409 Conflict`) 상태 코드로 정정 매핑.

---

## 1.7 인증 및 보안 식별 표준화

### 1.7.1 SecurityContext 인증 객체 사용자 식별자 접근 표준화

-
현황: [CustomUserDetails.java#L50-L52](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/global/security/principal/CustomUserDetails.java#L50-L52)
에서 Spring Security의 표준 식별자 메서드인 `getUsername()`이 사용자 계정(email)이 아닌 직원 실명(`name`)을 반환하도록 오버라이딩되어 있음.
- 문제점: Spring Security 표준 규격에서 username은 고유 식별자(Unique ID)여야 함에도 동명이인이 발생할 수 있는 name을 반환하여, 감사 로그 주입(AuditorAware)이나 보안
  컨텍스트 식별 시 식별 모호성 발생.
- 문제 클래스:
    - [CustomUserDetails.java#L50-L52](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/main/java/net/likelion/bebc25/prodflow/global/security/principal/CustomUserDetails.java#L50-L52)
      ```java
      @Override
      public String getUsername() {
          return name; // 직원 이름 반환 (표준 규격 위반)
      }
      ```
- 조치: `getUsername()`은 고유 식별자인 `email`을 반환하도록 표준화하고, 직원의 실명은 별도 메서드(`getName()`)로 제공.
  ```java
  @Override
  public String getUsername() {
      return email;
  }

  public String getName() {
      return name;
  }
  ```

---

## 1.8 테스트 케이스 결함 및 보완 필요

### 1.8.1 테스트 환경 설정(application.properties) 및 데이터소스 격리 부재

- 현황:
    - `src/test/resources` 디렉터리가 부재하여, 테스트 구동 시 메인의 `application.yaml`을 그대로 참조함.
    - 현재 GitHub Actions CI 환경에서는 Docker MySQL 9.7 컨테이너와 Repository Secrets를 강제 주입하여 임시로 통과시키고 있으나, 이는 외부 인프라에 종속된 전형적인
      테스트 격리(Test Isolation) 실패 상태임.
- 문제점:
    - 로컬에 `prodflow-app` 계정과 암호가 동일하게 세팅되어 있지 않거나 환경변수가 등록되지 않은 신규 개발자의 PC에서 `./gradlew test`를 실행하면,
      `Access denied for user 'prodflow-app'@'localhost'` 및 `DialectFactoryImpl` 오류가 발생하여 전체 테스트가 실패하고 빌드조차 불가능함.
    - 이전 정규 수업 및 1·2차 프로젝트에서는 SQL(MyBatis 등)을 직접 작성하는 방식이었기 때문에 MySQL 전용 문법 및 함수 호환성 문제로 실제 MySQL을 띄워 테스트를 진행했었음.
    - 하지만 이번 3차 심화 프로젝트는 JPA(Hibernate)를 기반으로 개발하므로 하이버네이트 방언(Dialect) 엔진이 각 데이터베이스에 맞는 SQL을 자동으로 생성 및 변환해 줌.
    - 따라서 이제는 외부 MySQL 인프라(로컬 데몬 기동, 계정 생성, 포트 점유 등)에 종속될 필요 없이, 어떤 환경에서든 사전 설정 없이 빠르게 독립 실행되는 H2 인메모리 데이터소스로 전환하는 것이 표준
      개발 원칙임.
    - H2 인메모리 의존성이 있어도, `@SpringBootTest`는 `@DataJpaTest`와 달리 자동으로 H2로 전환해주지 않고 메인의 MySQL 설정을 그대로 읽으므로 테스트 전용 데이터소스 선언이
      강제됨.
- 문제 클래스:
    - [ProdflowApplicationTests.java#L10](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/test/java/net/likelion/bebc25/prodflow/ProdflowApplicationTests.java#L10)
    - [DashboardServiceTest.java#L16](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/test/java/net/likelion/bebc25/prodflow/dashboard/DashboardServiceTest.java#L16)
    - `src/test/resources` (디렉터리 및 테스트 프로퍼티 미존재)
- 조치: `src/test/resources/application.properties`를 신규 생성하여 메인 `application.yaml`의 설정을 그대로 상속받고 테스트에 필요한 H2 격리 데이터소스 및 더미
  프로퍼티만 선택적으로 오버라이드(Merge).
  ```properties
  # 1. H2 인메모리 격리 데이터소스 설정 (테스트 시 MySQL 대체)
  spring.datasource.driver-class-name=org.h2.Driver
  spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER,LINE
  spring.datasource.username=sa
  spring.datasource.password=
  spring.jpa.hibernate.ddl-auto=create-drop

  # 2. 스프링 부트의 H2 기본 설정값을 사용하지 않고 위의 설정을 사용하도록 강제
  spring.test.database.replace=none

  # 3. 더미 값 주입
  jwt.secret=cXVvdGF0aW9uLXRlc3Qtc2VjcmV0LW11c3QtYmUtMzItYnl0ZXM=
  jwt.access-token-expiration=60000
  jwt.refresh-token-expiration=120000
  spring.mail.username=test@prodflow.local
  spring.mail.password=testpassword
  holiday.api.service-key=test-dummy-holiday-key
  ```

### 1.8.2 개별 테스트 클래스 단위의 임시 프로퍼티 하드코딩

- 현황: 공통 테스트 프로퍼티 부재 및 로컬 MySQL 편의를 위해 개별 테스트 클래스 상단에 `@SpringBootTest(properties = {...})`를 사용하여 하드코딩 함.
- 문제점:
    - [QuotationCreateTest.java#L51-L60](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/test/java/net/likelion/bebc25/prodflow/quotation/QuotationCreateTest.java#L51-L60)
      의 경우 H2 DB 및 JWT 설정을 하드코딩하여 테스트 환경 설정이 중복 파편화됨.
    - [WorkOrderServiceTest.java#L46-L49](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/test/java/net/likelion/bebc25/prodflow/work_order/WorkOrderServiceTest.java#L46-L49)
      의 경우 로컬 MySQL 전용 설정으로 클래스 상단에
      `@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=never"})`를 하드코딩해
      두어, 빈 H2 인메모리 환경 구동 시 테이블을 생성하지 않고 검증(validate)을 시도하다 `SchemaManagementException`이 발생하며 실패함.
- 문제 클래스:
    - [QuotationCreateTest.java#L51-L60](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/test/java/net/likelion/bebc25/prodflow/quotation/QuotationCreateTest.java#L51-L60)
    - [WorkOrderServiceTest.java#L46-L49](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/test/java/net/likelion/bebc25/prodflow/work_order/WorkOrderServiceTest.java#L46-L49)
- 조치: 개별 테스트 클래스 상단의 `properties = {...}` 선언을 완전히 제거하고, `src/test/resources/application.properties`의
  `ddl-auto=create-drop` 표준 설정을 따르도록 정비하여 테스트 환경을 일원화함.

### 1.8.3 데이터베이스 정렬 기준(Collation) 차이에 따른 테스트 단언문 결함

- 현황: 상태 코드별 통계 조회 시 문자열 오름차순(`ORDER BY sc.code`) 정렬 결과를 하드코딩된 순서(`containsExactly`)로 엄격하게 검증함.
- 문제점:
    - 쿼리의 `ORDER BY sc.code` 정렬 시, H2 및 표준 자바(ASCII)에서는 `PACKING`의 'I'(ASCII 73)가 `PACK_DONE`의 '_'(ASCII 95)보다 작아
      `PACKING`이 먼저 정렬되어 반환됨.
    - 반면 로컬 MySQL(UCA 9.0 기반 `utf8mb4_0900_ai_ci`)에서는 언더바(`_`) 같은 특수문자 가중치 처리 차이로 `PACK_DONE`이 먼저 정렬되어 로컬 환경에서만 우연히 통과했던
      것임.
    - 결과적으로 H2 환경 구동
      시 [OrderStatisticsServiceTest.java#L228](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/test/java/net/likelion/bebc25/prodflow/dashboard/OrderStatisticsServiceTest.java#L228)
      의 `getOrderStatusStatistics_statusArray` 테스트에서 정렬 순서 불일치(`AssertionFailedError`)가 발생함.
- 문제 클래스:
    - [OrderStatisticsServiceTest.java#L228](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/test/java/net/likelion/bebc25/prodflow/dashboard/OrderStatisticsServiceTest.java#L228)
- 조치: 데이터베이스 엔진별 Collation(문자열 정렬 규칙) 차이에 테스트가 종속되지 않도록, 순서까지 엄격히 일치시키는 `containsExactly` 대신 순서와 무관하게 모든 상태 코드가 포함되었는지를
  검증하는 `containsExactlyInAnyOrder(...)`로 단언문을 변경할 것을 권장함.
  ```java
  assertThat(result)
          .extracting(DashboardDto.OrderStatusStatisticsResponse::statusCode)
          .containsExactlyInAnyOrder(
                  "CANCELLED", "COMPLETED", "IN_PRODUCTION", "ON_HOLD",
                  "PACK_DONE", "PACKING", "PRODUCTION_READY", "READY_TO_SHIP",
                  "SHIPPING", "WAITING"
          );
  ```

### 1.8.4 외부 공공데이터 API 의존에 따른 테스트 취약성

- 현황: 성탄절 등 공휴일 제외 로직을 테스트할 때 `holidayRepository.findAll()`로 DB에 적재된 공휴일을 조회하여 검증함.
- 문제점:
    - 서버 기동 시 `HolidayInitializer`가 외부 공공데이터포털 API를 직접 호출하여 공휴일을 DB에 적재하도록 설계되어 있음.
    - 실제 유효한 외부 API 키(`HOLIDAY_API_KEY`)가 없는 격리된 테스트 환경이나 외부 정부 사이트 장애/점검 시에는 공휴일 수집이 실패하여 `holiday` 테이블이 0건으로 남음.
    - 이로
      인해 [WorkOrderServiceTest.java#L171](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/test/java/net/likelion/bebc25/prodflow/work_order/WorkOrderServiceTest.java#L171)
      의 `skipHoliday` 테스트 실행 시 `nextHoliday()`의 `Optional.orElseThrow()`에서 `NoSuchElementException: No value present`
      예외가 발생함.
- 문제 클래스:
    - [WorkOrderServiceTest.java#L171](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/test/java/net/likelion/bebc25/prodflow/work_order/WorkOrderServiceTest.java#L171)
- 조치: 외부 API 서버 및 키 유효성에 테스트가 종속되지 않도록, `WorkOrderServiceTest`의 `@BeforeEach`에서 검증에 필요한 공휴일(성탄절 등)을 직접
  `holidayRepository.save()`하여 테스트 픽스처(Given)를 스스로 구성하거나, `src/main/resources/data.sql`에 기본 공휴일 시드 데이터를 사전에 등록할 것을 권장함.
  ```java
  @BeforeEach
  void setUpHoliday() {
      if (holidayRepository.count() == 0) {
          holidayRepository.save(new Holiday(LocalDate.of(2026, 12, 25), "크리스마스"));
      }
  }
  ```

### 1.8.5 핵심 업무 도메인 단위/통합 테스트 케이스 부재

- 현황:
    - 결제(`payment`), 수주(`customer_order`), 회원(`user`), 인증(`auth`), AI(`ai`) 등 핵심 도메인 관련 테스트 파일이 `src/test/java`에 존재하지
      않음.
- 문제점: 수주 등록, 계약금 입금 후 작업지시서 확정, RBAC 권한 제어, 비밀번호 변경 등 핵심 비즈니스 로직에 대한 테스트가 제대로 진행되지 않음.
- 조치: `UserServiceTest`, `CustomerOrderServiceTest`, `PaymentServiceTest` 등 핵심 비즈니스 로직에 대한 JUnit 5 단위/슬라이스 테스트 케이스를
  우선적으로 작성하여 검증 체계 마련.

### 1.8.6 테스트 패키지 명명 규칙 불일치

- 현황: 메인 패키지명은 `line_notification` (스네이크케이스)인데, 테스트
  패키지는 [lineNotification](https://github.com/likelion-backend-25th/PRODFLOW/tree/dev/src/test/java/net/likelion/bebc25/prodflow/lineNotification) (
  카멜케이스)으로 선언되어 있음.
- 문제점: 메인 소스와 테스트 소스 간 패키지 구조 불일치로 인하여 default 접근 제한자(동일 패키지 접근 가능) 활용이 불가능하고 테스트 코드 탐색 생산성 저하.
- 문제 클래스:
    - [LineNotificationServiceTest.java](https://github.com/likelion-backend-25th/PRODFLOW/blob/dev/src/test/java/net/likelion/bebc25/prodflow/lineNotification/LineNotificationServiceTest.java)
- 조치: `line_notification`을 `linenotification` 으로 메인 패키지 리팩토링 시 테스트 패키지 명칭도 동일하게 일치화.

---

## 1.9 조치 권장 순서

1. 빌드 및 테스트 환경 정상화 (최우선)

- `src/test/resources/application.properties` 신규 생성을 통해 메인 설정 상속 및 H2 인메모리 격리 데이터소스 병합 설정 (로컬 및 CI 전체 테스트 실패 즉시 해결)
- 오타 패키지명(`line.serivce`, `work_order.serivce`, `line.repsitory`, `work_order.repsitory`) 정상화 및 테스트 패키지(
  `lineNotification`) 일치화

2. 엔티티 상속 구조 및 JPA 정합성 정비

- `BaseTimeEntity`(시간)와 `BaseEntity`(시간+작성자) 2단계 분리 및 `@Setter` 제거
- `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, `@LastModifiedBy` 표준 감사 어노테이션 및 `AuditorAware` 스프링 빈 연동
- Payment, User, Shipment 등 누락된 엔티티에 감사 상속 적용 및 Shipment의 `orderId` 객체 연관관계 매핑 전환
- `WorkOrderRepository` 목록 조회 시 일반 JOIN으로 인한 지연 로딩 N+1 해소 (DTO 생성자 프로젝션 또는 Fetch Join 및 카운트 쿼리 분리)
- `NotificationUserRepository`를 삭제하고 `UserRepository`로 조회 메서드 통합 (단일 리포지토리 원칙 준수)

3. 서비스 트랜잭션, 유효성 검증 및 예외 처리

- ProductService, ProductDrawingService 클래스 상단 트랜잭션을 `@Transactional(readOnly = true)`로 전환
- ClientDto, WorkOrderDto의 Request Record에 Bean Validation(@NotBlank, @NotNull) 선언 및 Controller 파라미터에 `@Valid` 추가
- ClientController의 수정 API를 `@PatchMapping` 또는 `@PutMapping`으로 변경 및 시작 슬래시(`/api/v1`) 누락 보정
- GlobalExceptionHandler의 Map 반환을 ApiErrorResponse로 통일하고 IllegalStateException 매핑 정상화(403 -> 400/409)

4. 인증 식별자 표준화 및 테스트 케이스 보강

- `CustomUserDetails.getUsername()`을 고유 식별자인 `email` 반환으로 표준화
- 수주(customer_order), 결제(payment), 회원(user) 핵심 업무 도메인 단위/통합 테스트 케이스 신규 작성

