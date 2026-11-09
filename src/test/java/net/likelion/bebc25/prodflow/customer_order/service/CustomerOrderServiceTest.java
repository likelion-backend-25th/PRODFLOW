package net.likelion.bebc25.prodflow.customer_order.service;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDetailDto;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderListDto;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.enums.NotifyChannel;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListSortType;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderListTab;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.entity.QuotationItem;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class CustomerOrderServiceTest {
    @Autowired
    private EntityManager em;

    @Autowired
    private CustomerOrderService customerOrderService;

    private Long newUserId;
    private Long newQuotationId;
    private Long newQuotationItemId;
    private LocalDate today;
    private LocalDate dueDate;

    // 테스트 데이터 준비
    @BeforeEach
    void setUp() {
        // 기존 시드 수주의 납기가 새 수주 등록 테스트에 영향을 주지 않도록 분리.
        // 이 변경은 테스트가 끝나면 롤백된다.
        CommonCode completed = em.find(CommonCode.class, 12L);
        em.createQuery("update CustomerOrder o set o.statusCode = :completed where o.statusCode.codeGroup.id = 2")
                .setParameter("completed", completed).executeUpdate();

        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        dueDate = today.plusDays(30);

        // 1. 수주 등록 권한을 가진 직원 등록
        User user = User.builder().name("수주 담당자").email("service@test.com")
                .password("1234").phone("010-1111-2222").role(UserRole.ORDER_MANAGER).build();
        user.changePassword("변경된 비밀번호");
        em.persist(user);

        newUserId = user.getId();

        // 서비스의 권한 검사를 위한 로그인 정보 설정
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        // 2. 거래처·품목 등록
        Client client = new Client("서비스 테스트 회사", "김담당", "client@test.com", "02-1234-5678",
                new Address("12345", "서울시 테스트로 1", "2층"), user);
        em.persist(client);
        CommonCode device = em.find(CommonCode.class, 1L);
        Product product = Product.create("SERVICE-001", "서비스 테스트 품목", device, "품목 설명", 1000L);
        em.persist(product);

        // 3. 승인 견적·견적 품목 등록
        Quotation quotation = new Quotation("QT-SERVICE-001", client, client.getEmployee(), user,
                dueDate, dueDate, today.plusDays(3), today.plusDays(4), today.plusDays(14),
                false, QuotationStatus.APPROVED, 220000L);
        em.persist(quotation);
        newQuotationId = quotation.getId();
        QuotationItem quotationItem = new QuotationItem(quotation, product, product.getProductName(), "테스트 기종", 200, 1000L);
        em.persist(quotationItem);
        newQuotationItemId = quotationItem.getId();

        // 4. 납기를 계산할 생산라인 등록
        Line line = new Line("테스트 생산라인", 1000);
        em.persist(line);

        em.flush();
        em.clear();
    }

    // 로그인 정보 정리
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("승인 견적으로 수주 등록")
    void create() {
        CustomerOrderDto.CreateRequest request = createRequest(100);
        CustomerOrderDto.CreateResponse response = customerOrderService.create(request, newUserId);

        em.flush();
        em.clear();

        CustomerOrder findOrder = em.find(CustomerOrder.class, response.id());
        assertThat(findOrder).isNotNull();
        assertThat(findOrder.getQuotation().getId()).isEqualTo(newQuotationId);
        assertThat(findOrder.getOrderNo()).startsWith("PF-");
        assertThat(findOrder.getTotalAmount()).isEqualTo(110000L);
        assertThat(findOrder.getEstimatedDueDate()).isEqualTo(dueDate);
        assertThat(findOrder.getDepositDueDate()).isEqualTo(today.plusDays(3));
        assertThat(findOrder.getShippingAddress()).isEqualTo("(12345) 서울시 테스트로 1 2층");
        assertThat(findOrder.getStatusCode().getCode()).isEqualTo("WAITING");

        // 수주 수량을 줄여도 승인 견적 수량은 유지
        QuotationItem quotationItem = em.find(QuotationItem.class, newQuotationItemId);
        assertThat(quotationItem.getQuantity()).isEqualTo(200);
    }

    @Test
    @DisplayName("직접 입력한 배송지로 수주 등록")
    void createWithShippingAddress() {
        CustomerOrderDto.ShippingAddress address = new CustomerOrderDto.ShippingAddress("54321", "부산시 배송로 10", "3층");
        CustomerOrderDto.CreateRequest request = new CustomerOrderDto.CreateRequest(newQuotationId,
                List.of(new CustomerOrderDto.ItemRequest(newQuotationItemId, 100)), dueDate,
                false, address, "홍수신", "010-3333-4444", NotifyChannel.EMAIL, "직접 배송");

        CustomerOrderDto.CreateResponse response = customerOrderService.create(request, newUserId);
        em.flush();
        em.clear();

        CustomerOrder findOrder = em.find(CustomerOrder.class, response.id());
        assertThat(findOrder.getShippingAddress()).isEqualTo("(54321) 부산시 배송로 10 3층");
        assertThat(findOrder.getClient().getAddress().getRoadAddress()).isEqualTo("서울시 테스트로 1");
    }

    @Test
    @DisplayName("품목 수량 100개 미만은 등록 실패")
    void rejectQuantityUnder100() {
        CustomerOrderDto.CreateRequest request = createRequest(99);

        assertThatThrownBy(() -> customerOrderService.create(request, newUserId))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("견적 수량을 초과하면 등록 실패")
    void rejectQuantityOverQuotation() {
        CustomerOrderDto.CreateRequest request = createRequest(201);

        assertThatThrownBy(() -> customerOrderService.create(request, newUserId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("승인되지 않은 견적은 등록 실패")
    void rejectNonApprovedQuotation() {
        Quotation quotation = em.find(Quotation.class, newQuotationId);
        quotation.sent();
        em.flush();
        em.clear();
        CustomerOrderDto.CreateRequest request = createRequest(100);

        assertThatThrownBy(() -> customerOrderService.create(request, newUserId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("승인");
    }

    @Test
    @DisplayName("같은 견적의 수주 중복 등록 실패")
    void rejectDuplicateOrder() {
        CustomerOrderDto.CreateRequest request = createRequest(100);
        customerOrderService.create(request, newUserId);
        em.flush();
        em.clear();

        assertThatThrownBy(() -> customerOrderService.create(request, newUserId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("이미 등록된 수주");
    }

    @Test
    @DisplayName("등록한 수주 목록 조회")
    void getOrders() {
        CustomerOrderDto.CreateResponse response = customerOrderService.create(createRequest(100), newUserId);
        em.flush();
        em.clear();
        CustomerOrderListDto.SearchCondition condition = new CustomerOrderListDto.SearchCondition(
                OrderListTab.OPEN, "서비스 테스트", null, 6L, null, OrderListSortType.CREATED_AT);

        PageResponse<CustomerOrderListDto.Response> result = customerOrderService.getOrders(condition, PageRequest.of(0, 8));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(response.id());
        assertThat(result.getContent().getFirst().statusName()).isEqualTo("대기");
    }

    @Test
    @DisplayName("수주 상세의 기본 정보·품목·VAT 별도 금액 조회")
    void getOrder() {
        CustomerOrder order = createDetailOrder(false);
        Long orderId = order.getId();
        em.flush();
        em.clear();

        CustomerOrderDetailDto.Response response = customerOrderService.getOrder(orderId);

        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.quotationId()).isEqualTo(newQuotationId);
        assertThat(response.userId()).isEqualTo(newUserId);
        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.client().contactName()).isEqualTo("등록 당시 담당자");
        assertThat(response.dueDate()).isEqualTo(dueDate);
        assertThat(response.shippingAddress()).isEqualTo("서울시 배송로 2 3층");
        assertThat(response.totalQuantity()).isEqualTo(200);
        assertThat(response.totalAmount()).isEqualTo(220000);
        assertThat(response.supplyAmount()).isEqualTo(200000);
        assertThat(response.vatAmount()).isEqualTo(20000);
        assertThat(response.items()).hasSize(1);
        CustomerOrderDetailDto.ItemResponse item = response.items().getFirst();
        assertThat(item.unitPrice()).isEqualTo(1000);
        assertThat(item.supplyAmount()).isEqualTo(200000);
        assertThat(item.vatAmount()).isEqualTo(20000);
        assertThat(item.lineAmount()).isEqualTo(220000);
        assertThat(response.productionCompletedAt()).isNull();
    }

    @Test
    @DisplayName("VAT 포함 수주 상세의 공급가액과 부가세 반올림")
    void getOrderWithVatIncluded() {
        CustomerOrder order = createDetailOrder(true);
        em.flush();
        em.clear();

        CustomerOrderDetailDto.Response response = customerOrderService.getOrder(order.getId());

        assertThat(response.vatIncluded()).isTrue();
        assertThat(response.totalAmount()).isEqualTo(200000);
        assertThat(response.supplyAmount()).isEqualTo(181818);
        assertThat(response.vatAmount()).isEqualTo(18182);
        assertThat(response.items().getFirst().lineAmount()).isEqualTo(200000);
    }

    @Test
    @DisplayName("현재 거래처·품목 정보가 바뀌어도 수주에 저장된 정보 유지")
    void getOrderWithStoredContactAndPrices() {
        CustomerOrder order = createDetailOrder(false);
        Client client = order.getClient();
        client.update(client.getCompany(), "변경된 담당자", "changed@test.com", "02-9999-9999",
                new Address("54321", "변경된 주소", "3층"), order.getUser());
        QuotationItem quotationItem = em.find(QuotationItem.class, newQuotationItemId);
        Product product = quotationItem.getProduct();
        product.update("변경된 현재 품목명", product.getDevice(), "변경된 설명", 999999L);
        em.flush();
        em.clear();

        CustomerOrderDetailDto.Response response = customerOrderService.getOrder(order.getId());

        assertThat(response.client().contactName()).isEqualTo("등록 당시 담당자");
        assertThat(response.client().email()).isEqualTo("snapshot@test.com");
        assertThat(response.client().phone()).isEqualTo("02-1111-2222");
        assertThat(response.client().address()).isEqualTo("등록 당시 회사 주소");
        assertThat(response.items().getFirst().productName()).isEqualTo("서비스 테스트 품목");
        assertThat(response.items().getFirst().unitPrice()).isEqualTo(1000);
    }

    @Test
    @DisplayName("완료·취소 상태에서도 수주 상세 조회")
    void getOrderRegardlessOfStatus() {
        CustomerOrder order = createDetailOrder(false);
        order.changeProgress(em.find(CommonCode.class, 12L));
        em.flush();
        em.clear();

        CustomerOrderDetailDto.Response completed = customerOrderService.getOrder(order.getId());
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.delay()).isNull();

        CustomerOrder storedOrder = em.find(CustomerOrder.class, order.getId());
        storedOrder.changeProgress(em.find(CommonCode.class, 15L));
        em.flush();
        em.clear();

        CustomerOrderDetailDto.Response cancelled = customerOrderService.getOrder(order.getId());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.delay()).isNull();
    }

    @Test
    @DisplayName("없는 수주의 상세 조회는 404 예외")
    void rejectUnknownOrder() {
        assertThatThrownBy(() -> customerOrderService.getOrder(Long.MAX_VALUE))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("수주를 찾을 수 없습니다.");
                });
    }

    @Test
    @DisplayName("해당 수주의 이력만 조회하고 처리 당시 이름과 사유를 유지한다")
    void getHistories() {
        CustomerOrder order = createDetailOrder(false);
        User user = order.getUser();
        StatusHistory history = new StatusHistory(HistoryEntityType.ORDER, order.getId(), "NONE", "WAITING", user);
        history.writeNote("등록 사유");
        em.persist(history);
        em.persist(new StatusHistory(HistoryEntityType.WORK_ORDER, order.getId(), "PREPARE", "PRODUCE", user));
        em.persist(new StatusHistory(HistoryEntityType.ORDER, 1L, "WAITING", "CANCELLED", user));
        user.update("변경된 직원 이름", user.getEmail(), user.getPhone(), user.getRole(), user.getStatus());
        em.flush();
        em.clear();

        List<CustomerOrderDto.HistoryResponse> responses = customerOrderService.getHistories(order.getId());

        assertThat(responses).hasSize(1);
        CustomerOrderDto.HistoryResponse response = responses.getFirst();
        assertThat(response.id()).isEqualTo(history.getId());
        assertThat(response.fromStatus()).isEqualTo("NONE");
        assertThat(response.toStatus()).isEqualTo("WAITING");
        assertThat(response.userId()).isEqualTo(newUserId);
        assertThat(response.userName()).isEqualTo("수주 담당자");
        assertThat(response.note()).isEqualTo("등록 사유");
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("이력이 없는 수주는 빈 목록을 반환한다")
    void getHistoriesWithoutHistory() {
        CustomerOrder order = createDetailOrder(false);
        em.flush();
        em.clear();

        assertThat(customerOrderService.getHistories(order.getId())).isEmpty();
    }

    @Test
    @DisplayName("시스템 처리 이력은 직원 ID 없이 저장된 이름으로 조회한다")
    void getSystemHistory() {
        CustomerOrder order = createDetailOrder(false);
        StatusHistory history = new StatusHistory(HistoryEntityType.ORDER, order.getId(), "WAITING", "CANCELLED", order.getUser());
        em.persist(history);
        em.flush();
        em.createQuery("update StatusHistory h set h.user = null, h.userName = :name where h.id = :id")
                .setParameter("name", "SYSTEM").setParameter("id", history.getId()).executeUpdate();
        em.clear();

        List<CustomerOrderDto.HistoryResponse> responses = customerOrderService.getHistories(order.getId());

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().userId()).isNull();
        assertThat(responses.getFirst().userName()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("없는 수주의 이력 조회는 404 예외")
    void rejectHistoriesOfUnknownOrder() {
        assertThatThrownBy(() -> customerOrderService.getHistories(Long.MAX_VALUE))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("수주를 찾을 수 없습니다.");
                });
    }

    // 상세 조회 전용 수주·품목 준비. 결제·작업지시는 생성하지 않는다.
    private CustomerOrder createDetailOrder(boolean vatIncluded) {
        Quotation quotation = em.find(Quotation.class, newQuotationId);
        QuotationItem quotationItem = em.find(QuotationItem.class, newQuotationItemId);
        User user = em.find(User.class, newUserId);
        CustomerOrder order = new CustomerOrder("PF-DETAIL-001", quotation, quotation.getClient(),
                "등록 당시 담당자", "snapshot@test.com", "02-1111-2222", "등록 당시 회사 주소", user,
                dueDate, dueDate, today.plusDays(3), today.plusDays(4), today.plusDays(3),
                "서울시 배송로 2 3층", "홍수신", "010-3333-4444", NotifyChannel.EMAIL,
                "상세 조회 메모", em.find(CommonCode.class, 6L), vatIncluded, vatIncluded ? 200000L : 220000L);
        em.persist(order);
        em.persist(new OrderProduct(order, quotationItem.getProduct(), quotationItem.getProductName(), 200, 1000L));
        return order;
    }

    // 기본 등록 요청 생성
    private CustomerOrderDto.CreateRequest createRequest(int quantity) {
        return new CustomerOrderDto.CreateRequest(newQuotationId,
                List.of(new CustomerOrderDto.ItemRequest(newQuotationItemId, quantity)),
                dueDate, true, null, "홍수신", "010-3333-4444", NotifyChannel.EMAIL, "등록 메모");
    }
}
