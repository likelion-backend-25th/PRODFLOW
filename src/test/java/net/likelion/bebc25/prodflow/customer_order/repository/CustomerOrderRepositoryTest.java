package net.likelion.bebc25.prodflow.customer_order.repository;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.customer_order.enums.NotifyChannel;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class CustomerOrderRepositoryTest {
    @Autowired
    private EntityManager em;

    @Autowired
    private CustomerOrderRepository customerOrderRepository;

    private Long newQuotationId;
    private Long newOrderId;

    // 테스트 데이터 준비
    @BeforeEach
    void setUp() {
        // 1. 직원·거래처·품목 등록
        User user = User.builder().name("수주 담당자").email("repository@test.com")
                .password("1234").phone("010-1111-2222").role(UserRole.ORDER_MANAGER).build();
        em.persist(user);
        Client client = new Client("레포지토리 테스트 회사", "김담당", "client@test.com", "02-1234-5678",
                new Address("12345", "서울시 테스트로 1", "2층"), user);
        em.persist(client);
        CommonCode device = em.find(CommonCode.class, 1L);
        Product product = Product.create("REPOSITORY-001", "검색용 테스트 품목", device, "품목 설명", 1000L);
        em.persist(product);

        // 2. 승인 견적 등록
        LocalDate dueDate = LocalDate.now().plusDays(30);
        Quotation quotation = new Quotation("QT-REPOSITORY-001", client, client.getEmployee(), user,
                dueDate, dueDate, dueDate.minusDays(7), dueDate.minusDays(3), LocalDate.now().plusDays(14),
                false, QuotationStatus.APPROVED, 220000L);
        em.persist(quotation);
        newQuotationId = quotation.getId();

        // 3. 레포지토리로 수주 저장
        CommonCode waiting = em.find(CommonCode.class, 6L);
        CustomerOrder order = new CustomerOrder("PF-REPOSITORY-001", quotation, client, client.getEmployee(),
                client.getEmail(), client.getPhone(), "서울시 테스트로 1 2층", user,
                dueDate, dueDate, dueDate.minusDays(7), dueDate.minusDays(3), LocalDate.now().plusDays(3),
                "서울시 배송로 2 3층", "홍수신", "010-3333-4444", NotifyChannel.EMAIL,
                "등록 메모", waiting, false, 220000L);
        CustomerOrder savedOrder = customerOrderRepository.save(order);
        newOrderId = savedOrder.getId();
        OrderProduct orderProduct = new OrderProduct(savedOrder, product, product.getProductName(), 200, 1000L);
        em.persist(orderProduct);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("레포지토리로 저장한 수주 조회")
    void saveAndFindById() {
        CustomerOrder findOrder = customerOrderRepository.findById(newOrderId).orElseThrow();

        assertThat(findOrder.getOrderNo()).isEqualTo("PF-REPOSITORY-001");
        assertThat(findOrder.getQuotation().getId()).isEqualTo(newQuotationId);
        assertThat(findOrder.getClient().getCompany()).isEqualTo("레포지토리 테스트 회사");
    }

    @Test
    @DisplayName("견적 ID로 연결된 수주 조회")
    void findByQuotationId() {
        CustomerOrder findOrder = customerOrderRepository.findByQuotationId(newQuotationId).orElseThrow();

        assertThat(findOrder.getId()).isEqualTo(newOrderId);
        assertThat(customerOrderRepository.findByQuotationId(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("품목명으로 수주 검색")
    void searchByProductName() {
        Page<CustomerOrder> result = customerOrderRepository.searchOrders(
                "%검색용%", false, null, null, null, false, PageRequest.of(0, 8));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getId()).isEqualTo(newOrderId);
    }

    @Test
    @DisplayName("확정·진행·입금 상태를 함께 검색")
    void searchByStatuses() {
        Page<CustomerOrder> result = customerOrderRepository.searchOrders(
                "%pf-repository-001%", false, ConfirmationStatus.PENDING, 6L, OrderPaymentStatus.UNPAID,
                false, PageRequest.of(0, 8));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getStatusCode().getId()).isEqualTo(6L);
        assertThat(result.getContent().getFirst().getStatusCode().getCodeGroup().getId()).isEqualTo(2L);

        // 생산대기 코드로 검색하면 아직 대기 중인 수주는 조회되지 않음
        Page<CustomerOrder> noMatch = customerOrderRepository.searchOrders(
                "%pf-repository-001%", false, null, 7L, null, false, PageRequest.of(0, 8));
        assertThat(noMatch.getContent()).isEmpty();
    }

    @Test
    @DisplayName("수주 목록 페이지네이션")
    void pagination() {
        Page<CustomerOrder> firstPage = customerOrderRepository.searchOrders(
                "%pf-repository-001%", false, null, null, null, false, PageRequest.of(0, 1));
        Page<CustomerOrder> nextPage = customerOrderRepository.searchOrders(
                "%pf-repository-001%", false, null, null, null, false, PageRequest.of(1, 1));

        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(firstPage.getTotalPages()).isEqualTo(1);
        assertThat(nextPage.getContent()).isEmpty();
        assertThat(nextPage.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("수주별 품목 목록 조회")
    void findItems() {
        List<OrderProduct> items = customerOrderRepository.findItems(List.of(newOrderId));

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getProductName()).isEqualTo("검색용 테스트 품목");
        assertThat(items.getFirst().getQuantity()).isEqualTo(200);
        assertThat(items.getFirst().getCustomerOrder().getId()).isEqualTo(newOrderId);
    }

    @Test
    @DisplayName("그룹 ID 2의 수주 진행 상태 목록 조회")
    void findStatuses() {
        List<CommonCode> statuses = customerOrderRepository.findStatuses(CodeStatus.ACTIVE);

        assertThat(statuses).isNotEmpty();
        assertThat(statuses.getFirst().getCode()).isEqualTo("WAITING");
        assertThat(statuses.getFirst().getCodeGroup().getId()).isEqualTo(2L);
        assertThat(customerOrderRepository.countOrderStatus(6L)).isEqualTo(1);
        assertThat(customerOrderRepository.countOrderStatus(1L)).isZero();
    }

    @Test
    @DisplayName("완료 수주는 진행 중 탭에서 제외")
    void searchCompletedOrders() {
        CustomerOrder order = customerOrderRepository.findById(newOrderId).orElseThrow();
        CommonCode completed = em.find(CommonCode.class, 12L);
        order.changeProgress(completed);
        em.flush();
        em.clear();

        Page<CustomerOrder> open = customerOrderRepository.searchOrders(
                "%pf-repository-001%", false, null, null, null, false, PageRequest.of(0, 8));
        Page<CustomerOrder> done = customerOrderRepository.searchOrders(
                "%pf-repository-001%", true, null, null, null, false, PageRequest.of(0, 8));

        assertThat(open.getContent()).isEmpty();
        assertThat(done.getContent()).hasSize(1);
        assertThat(done.getContent().getFirst().getId()).isEqualTo(newOrderId);
    }
}
