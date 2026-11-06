package net.likelion.bebc25.prodflow.quotation.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.product.entity.Product;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class QuotationTest {
    @Autowired
    private EntityManager em;

    private Long quotationId;
    private Long productId;

    // 직원·거래처·품목·초안 준비
    @BeforeEach
    void setUp() {
        User user = User.builder().name("견적 작성자").email("quotation-entity@test.com")
                .password("1234").phone("010-1111-2222").role(UserRole.ORDER_MANAGER).build();
        em.persist(user);

        Client client = new Client("엔티티 테스트 회사", "김담당", "client@test.com", "02-1234-5678",
                new Address("12345", "서울시 테스트로 1", "2층"), user);
        em.persist(client);

        CommonCode device = em.find(CommonCode.class, 1L);
        Product product = Product.create("QUOTATION-ENTITY-TEST", "테스트 품목", device, "품목 설명", 1000L);
        em.persist(product);
        productId = product.getId();

        LocalDate dueDate = LocalDate.now().plusDays(30);
        Quotation quotation = new Quotation("QT-ENTITY-TEST", client, client.getEmployee(), user,
                dueDate, dueDate, dueDate.minusDays(7), dueDate.minusDays(3), LocalDate.now().plusDays(14),
                false, QuotationStatus.DRAFT, 220000L);
        em.persist(quotation);
        quotationId = quotation.getId();

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("견적 품목 등록과 연관관계 조회")
    void saveAndFindItem() {
        Quotation quotation = em.find(Quotation.class, quotationId);
        Product product = em.find(Product.class, productId);
        QuotationItem item = new QuotationItem(quotation, product, "테스트 품목", "테스트 기종", 200, 1000L);
        em.persist(item);

        em.flush();
        em.clear();

        QuotationItem findItem = em.find(QuotationItem.class, item.getId());
        assertThat(findItem).isNotNull();
        assertThat(findItem.getQuotation().getId()).isEqualTo(quotationId);
        assertThat(findItem.getProduct().getId()).isEqualTo(productId);
        assertThat(findItem.getQuantity()).isEqualTo(200);
        assertThat(findItem.getLineAmount()).isEqualTo(220000L);
        assertThat(findItem.getCreatedAt()).isNotNull();
        assertThat(findItem.getQuotation().getStatus()).isEqualTo(QuotationStatus.DRAFT);
    }

    @Test
    @DisplayName("같은 견적에 같은 품목 중복 등록 실패")
    void rejectDuplicateItem() {
        Quotation quotation = em.find(Quotation.class, quotationId);
        Product product = em.find(Product.class, productId);
        QuotationItem item = new QuotationItem(quotation, product, "테스트 품목", "테스트 기종", 100, 1000L);
        em.persist(item);
        em.flush();
        em.clear();

        Quotation findQuotation = em.find(Quotation.class, quotationId);
        Product findProduct = em.find(Product.class, productId);
        QuotationItem duplicateItem = new QuotationItem(findQuotation, findProduct, "테스트 품목", "테스트 기종", 100, 1000L);

        assertThatThrownBy(() -> {
            em.persist(duplicateItem);
            em.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("견적번호 변경 시 변경 감지로 저장")
    void changeQuotationNo() {
        Quotation quotation = em.find(Quotation.class, quotationId);
        quotation.changeQuotationNo("QT-ENTITY-UPDATED");
        em.flush();
        em.clear();

        Quotation findQuotation = em.find(Quotation.class, quotationId);
        assertThat(findQuotation.getQuotationNo()).isEqualTo("QT-ENTITY-UPDATED");
        assertThat(findQuotation.getCreatedAt()).isNotNull();
        assertThat(findQuotation.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("발송한 견적 승인")
    void approveSentQuotation() {
        Quotation quotation = em.find(Quotation.class, quotationId);
        quotation.sent();
        quotation.approve();
        em.flush();
        em.clear();

        Quotation findQuotation = em.find(Quotation.class, quotationId);
        assertThat(findQuotation.getStatus()).isEqualTo(QuotationStatus.APPROVED);
    }

    @Test
    @DisplayName("초안 견적은 승인 불가")
    void rejectDraftApproval() {
        Quotation quotation = em.find(Quotation.class, quotationId);

        assertThatThrownBy(quotation::approve).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("품목별 최소 수량 100개 검증")
    void rejectQuantityUnder100() {
        Quotation quotation = em.find(Quotation.class, quotationId);
        Product product = em.find(Product.class, productId);

        assertThatThrownBy(() -> new QuotationItem(quotation, product, "테스트 품목", "테스트 기종", 99, 1000L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
