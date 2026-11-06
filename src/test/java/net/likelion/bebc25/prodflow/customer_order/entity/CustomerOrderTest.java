package net.likelion.bebc25.prodflow.customer_order.entity;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class CustomerOrderTest {
    @Autowired
    private EntityManager em;

    private Long newQuotationId;
    private Long newOrderId;
    private Long newOrderProductId;

    // 테스트 데이터 준비
    @BeforeEach
    void setUp() {
        // 1. 직원·거래처 등록
        User user = User.builder().name("수주 담당자").email("entity@test.com")
                .password("1234").phone("010-1111-2222").role(UserRole.ORDER_MANAGER).build();
        em.persist(user);

        Client client = new Client("테스트 회사", "김담당", "client@test.com", "02-1234-5678",
                new Address("12345", "서울시 테스트로 1", "2층"), user);
        em.persist(client);

        // 2. 품목·승인 견적 등록
        CommonCode device = em.find(CommonCode.class, 1L);
        Product product = Product.create("ENTITY-001", "테스트 품목", device, "품목 설명", 1000L);
        em.persist(product);

        LocalDate dueDate = LocalDate.now().plusDays(30);
        Quotation quotation = new Quotation("QT-ENTITY-001", client, client.getEmployee(), user,
                dueDate, dueDate, dueDate.minusDays(7), dueDate.minusDays(3), LocalDate.now().plusDays(14),
                false, QuotationStatus.APPROVED, 220000L);
        em.persist(quotation);

        newQuotationId = quotation.getId();

        // 3. 수주·수주 품목 등록
        CommonCode waiting = em.find(CommonCode.class, 6L);
        CustomerOrder order = new CustomerOrder("PF-ENTITY-001", quotation, client, client.getEmployee(),
                client.getEmail(), client.getPhone(), "서울시 테스트로 1 2층", user,
                dueDate, dueDate, dueDate.minusDays(7), dueDate.minusDays(3), LocalDate.now().plusDays(3),
                "서울시 배송로 2 3층", "홍수신", "010-3333-4444", NotifyChannel.EMAIL,
                "등록 메모", waiting, false, 220000L);
        em.persist(order);

        newOrderId = order.getId();

        OrderProduct orderProduct = new OrderProduct(order, product, product.getProductName(), 200, 1000L);
        em.persist(orderProduct);

        newOrderProductId = orderProduct.getId();

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("수주 등록 및 초기 상태")
    void create() {
        CustomerOrder findOrder = em.find(CustomerOrder.class, newOrderId);

        assertThat(findOrder).isNotNull();
        assertThat(findOrder.getQuotation().getId()).isEqualTo(newQuotationId);
        assertThat(findOrder.getClient().getCompany()).isEqualTo("테스트 회사");
        assertThat(findOrder.getTotalAmount()).isEqualTo(220000L);
        assertThat(findOrder.getStatusCode().getCode()).isEqualTo("WAITING");
        assertThat(findOrder.getConfirmationStatus()).isEqualTo(ConfirmationStatus.PENDING);
        assertThat(findOrder.getPaymentStatus()).isEqualTo(OrderPaymentStatus.UNPAID);
        assertThat(findOrder.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("수주 조회와 1차 캐시")
    void read() {
        CustomerOrder findOrder = em.find(CustomerOrder.class, newOrderId);
        CustomerOrder findOrder2 = em.find(CustomerOrder.class, newOrderId);

        assertThat(findOrder).isSameAs(findOrder2);
        assertThat(findOrder.getOrderNo()).isEqualTo("PF-ENTITY-001");
    }

    @Test
    @DisplayName("수주 메모 수정")
    void update() {
        CustomerOrder order = em.find(CustomerOrder.class, newOrderId);
        order.changeMemo("수정된 메모");

        em.flush();
        em.clear();

        CustomerOrder updatedOrder = em.find(CustomerOrder.class, newOrderId);
        assertThat(updatedOrder.getMemo()).isEqualTo("수정된 메모");
    }

    @Test
    @DisplayName("수주 배송 정보 수정")
    void changeShipping() {
        CustomerOrder order = em.find(CustomerOrder.class, newOrderId);
        order.changeShipping("부산시 새주소 10", "새 수신자", "010-5555-6666", NotifyChannel.SMS);

        em.flush();
        em.clear();

        CustomerOrder updatedOrder = em.find(CustomerOrder.class, newOrderId);
        assertThat(updatedOrder.getShippingAddress()).isEqualTo("부산시 새주소 10");
        assertThat(updatedOrder.getReceiverName()).isEqualTo("새 수신자");
        assertThat(updatedOrder.getReceiverPhone()).isEqualTo("010-5555-6666");
        assertThat(updatedOrder.getNotifyChannel()).isEqualTo(NotifyChannel.SMS);
    }

    @Test
    @DisplayName("생산대기 변경 시 수주 확정")
    void changeProgress() {
        CustomerOrder order = em.find(CustomerOrder.class, newOrderId);
        CommonCode productionReady = em.find(CommonCode.class, 7L);
        order.changeProgress(productionReady);

        em.flush();
        em.clear();

        CustomerOrder updatedOrder = em.find(CustomerOrder.class, newOrderId);
        assertThat(updatedOrder.getStatusCode().getCode()).isEqualTo("PRODUCTION_READY");
        assertThat(updatedOrder.getConfirmationStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("전액 입금 상태 변경")
    void markPaid() {
        CustomerOrder order = em.find(CustomerOrder.class, newOrderId);
        order.markPaid();

        em.flush();
        em.clear();

        CustomerOrder updatedOrder = em.find(CustomerOrder.class, newOrderId);
        assertThat(updatedOrder.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);
        assertThat(updatedOrder.getStatusCode().getCode()).isEqualTo("WAITING");
    }

    @Test
    @DisplayName("수주 품목 연관관계와 VAT 별도 금액")
    void orderProductMapping() {
        OrderProduct orderProduct = em.find(OrderProduct.class, newOrderProductId);

        assertThat(orderProduct.getCustomerOrder().getId()).isEqualTo(newOrderId);
        assertThat(orderProduct.getProduct().getProductName()).isEqualTo("테스트 품목");
        assertThat(orderProduct.getQuantity()).isEqualTo(200);
        assertThat(orderProduct.lineAmount()).isEqualTo(220000L);
    }

    @Test
    @DisplayName("수주 품목 삭제 후 수주 삭제")
    void delete() {
        OrderProduct orderProduct = em.find(OrderProduct.class, newOrderProductId);
        em.remove(orderProduct);
        em.flush(); // 수주를 참조하는 품목을 먼저 삭제

        CustomerOrder order = em.find(CustomerOrder.class, newOrderId);
        em.remove(order);
        em.flush();
        em.clear();

        assertThat(em.find(OrderProduct.class, newOrderProductId)).isNull();
        assertThat(em.find(CustomerOrder.class, newOrderId)).isNull();
        assertThat(em.find(Quotation.class, newQuotationId)).isNotNull();
    }
}
