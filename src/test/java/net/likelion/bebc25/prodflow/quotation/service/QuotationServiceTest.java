package net.likelion.bebc25.prodflow.quotation.service;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationItemDto;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class QuotationServiceTest {
    @Autowired
    private EntityManager em;

    @Autowired
    private QuotationService quotationService;

    private Long userId;
    private Long clientId;
    private Long productId;
    private LocalDate dueDate;

    // 견적 작성에 필요한 데이터 준비
    @BeforeEach
    void setUp() {
        // 과거 시드 수주의 일정이 계산에 영향을 주지 않도록 처리. 테스트 종료 시 롤백.
        CommonCode completed = em.find(CommonCode.class, 12L);
        em.createQuery("update CustomerOrder o set o.statusCode = :completed where o.statusCode.codeGroup.id = 2")
                .setParameter("completed", completed).executeUpdate();
        dueDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(30);

        User user = User.builder().name("견적 작성자").email("quotation-service@test.com")
                .password("1234").phone("010-1111-2222").role(UserRole.ORDER_MANAGER).build();
        user.changePassword("변경된 비밀번호");
        em.persist(user);
        userId = user.getId();

        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        Client client = new Client("견적 서비스 테스트 회사", "김담당", "client@test.com", "02-1234-5678",
                new Address("12345", "서울시 테스트로 1", "2층"), user);
        em.persist(client);
        clientId = client.getId();

        CommonCode device = em.find(CommonCode.class, 1L);
        Product product = Product.create("QUOTATION-SERVICE-TEST", "테스트 품목", device, "품목 설명", 1000L);
        em.persist(product);
        productId = product.getId();
        em.persist(new Line("견적 테스트 생산라인", 1000));

        em.flush();
        em.clear();
    }

    // 로그인 정보 정리
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("견적 초안 등록과 품목·총액 저장")
    void create() {
        QuotationDto.CreateRequest request = createRequest(100);
        QuotationDto.DetailResponse response = quotationService.create(request, userId);
        em.flush();
        em.clear();

        Quotation quotation = em.find(Quotation.class, response.id());
        QuotationItem item = em.find(QuotationItem.class, response.items().getFirst().id());
        assertThat(quotation).isNotNull();
        assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(quotation.getQuotationNo()).startsWith("QT-");
        assertThat(quotation.getTotalAmount()).isEqualTo(110000L);
        assertThat(quotation.getPdfObjectKey()).isNull();
        assertThat(item.getQuantity()).isEqualTo(100);
        assertThat(item.getQuotation().getId()).isEqualTo(quotation.getId());
    }

    @Test
    @DisplayName("같은 견적 초안 수정 저장")
    void updateDraft() {
        QuotationDto.DetailResponse created = quotationService.create(createRequest(100), userId);
        QuotationDto.DetailResponse updated = quotationService.updateDraft(created.id(), createRequest(200), userId);
        em.flush();
        em.clear();

        Quotation quotation = em.find(Quotation.class, created.id());
        QuotationItem item = em.find(QuotationItem.class, updated.items().getFirst().id());
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(quotation.getQuotationNo()).isEqualTo(created.quotationNo());
        assertThat(quotation.getTotalAmount()).isEqualTo(220000L);
        assertThat(item.getQuantity()).isEqualTo(200);
    }

    @Test
    @DisplayName("견적 상세 조회")
    void getQuotationById() {
        QuotationDto.DetailResponse created = quotationService.create(createRequest(100), userId);
        em.flush();
        em.clear();

        QuotationDto.DetailResponse detail = quotationService.getQuotationById(created.id());

        assertThat(detail.id()).isEqualTo(created.id());
        assertThat(detail.clientId()).isEqualTo(clientId);
        assertThat(detail.totalAmount()).isEqualTo(110000L);
        assertThat(detail.items()).hasSize(1);
    }

    @Test
    @DisplayName("품목별 100개 미만 견적 등록 실패")
    void rejectQuantityUnder100() {
        QuotationDto.CreateRequest request = createRequest(99);

        assertThatThrownBy(() -> quotationService.create(request, userId))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("발송한 견적은 초안 수정 불가")
    void rejectSentEdit() {
        QuotationDto.DetailResponse created = quotationService.create(createRequest(100), userId);
        Quotation quotation = em.find(Quotation.class, created.id());
        quotation.sent();
        em.flush();
        em.clear();
        QuotationDto.CreateRequest request = createRequest(200);

        assertThatThrownBy(() -> quotationService.updateDraft(created.id(), request, userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("초안");
    }

    // 기본 견적 요청 생성
    private QuotationDto.CreateRequest createRequest(int quantity) {
        QuotationItemDto.CreateRequest item = new QuotationItemDto.CreateRequest(productId, quantity, 1000L);
        return new QuotationDto.CreateRequest(clientId, dueDate, null, false, List.of(item), null);
    }
}
