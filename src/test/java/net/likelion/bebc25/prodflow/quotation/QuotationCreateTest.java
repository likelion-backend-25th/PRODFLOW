package net.likelion.bebc25.prodflow.quotation;

import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.client.repository.ClientRepository;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.commoncode.entity.CodeGroup;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;
import net.likelion.bebc25.prodflow.productionline.entity.Line;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationItemDto;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationItemRepository;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import net.likelion.bebc25.prodflow.quotation.service.QuotationService;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:quotation;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,LINE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "spring.jpa.properties.hibernate.show_sql=false",
        "jwt.secret=cXVvdGF0aW9uLXRlc3Qtc2VjcmV0LW11c3QtYmUtMzItYnl0ZXM=",
        "jwt.access-token-expiration=60000", "jwt.refresh-token-expiration=120000"
})
class QuotationCreateTest {
    @Autowired
    EntityManager em;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    QuotationService service;
    @Autowired
    Validator validator;
    @Autowired
    QuotationRepository quotationRepository;
    @Autowired
    QuotationItemRepository itemRepository;
    @Autowired
    ClientRepository clientRepository;
    Long userId;
    Long clientId;
    Long productId;

    @BeforeEach
    void setUp() {
        CustomUserDetails principal = tx().execute(status -> {
            for (String entity : new String[]{"ProductionPlan", "WorkOrder", "OrderProduct", "CustomerOrder",
                    "QuotationItem", "Quotation", "Client", "Product", "Line", "CommonCode", "CodeGroup", "User", "holiday"}) {
                em.createQuery("delete from " + entity).executeUpdate();
            }
            User user = User.builder().name("서도윤").email("writer@example.com")
                    .phone("010-1234-5678").password("encoded").role(UserRole.ORDER_MANAGER).build();
            user.changePassword("encoded");
            em.persist(user);
            userId = user.getId();
            Client client = new Client("기존 회사", "대표자", "client@example.com", "02-1234-5678",
                    new Address("12345", "서울시 테스트로 1", "2층"), user);
            em.persist(client);
            clientId = client.getId();
            CodeGroup group = instantiate(CodeGroup.class);
            ReflectionTestUtils.setField(group, "groupCode", "DEVICE");
            ReflectionTestUtils.setField(group, "groupName", "기종");
            em.persist(group);
            CommonCode device = instantiate(CommonCode.class);
            ReflectionTestUtils.setField(device, "codeGroup", group);
            ReflectionTestUtils.setField(device, "code", "PHONE");
            ReflectionTestUtils.setField(device, "name", "테스트 기종");
            ReflectionTestUtils.setField(device, "sortOrder", 1);
            ReflectionTestUtils.setField(device, "status", CodeStatus.ACTIVE);
            em.persist(device);
            Product product = Product.create("TEST-001", "테스트 품목", device, "설명", 1000L);
            em.persist(product);
            productId = product.getId();
            em.persist(new Line("라인 A", 1000));
            return new CustomUserDetails(user);
        });
        // JWT 인증 필터와 동일하게 CustomUserDetails와 사용자 권한을 등록한다.
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }


    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsQuotationWithVatAndAuditWithoutReservingProduction() {
        var response = service.create(request(List.of(item(100, 1000L))), userId);

        assertThat(response.status()).isEqualTo(QuotationStatus.SENT);
        assertThat(response.totalAmount()).isEqualTo(110000L);
        assertThat(response.supplyAmount()).isEqualTo(100000L);
        assertThat(response.vatAmount()).isEqualTo(10000L);
        assertThat(response.depositAmount()).isEqualTo(55000L);
        assertThat(response.clientEmployee()).isEqualTo("견적 담당자");
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
        assertThat(quotationRepository.count()).isEqualTo(1);
        assertThat(itemRepository.count()).isEqualTo(1);

        var quotation = quotationRepository.findById(response.id()).orElseThrow();
        String expectedNo = "QT-" + today().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + response.id();
        assertThat(response.quotationNo()).isEqualTo(expectedNo);
        assertThat(quotation.getQuotationNo()).isEqualTo(expectedNo);
        assertThat(quotation.getCreatedBy()).isEqualTo("서도윤");
        assertThat(quotation.getUpdatedBy()).isEqualTo("서도윤");
        tx().executeWithoutResult(status -> {
            assertThat(em.createQuery("select count(w) from WorkOrder w", Long.class).getSingleResult()).isZero();
            assertThat(em.createQuery("select count(p) from ProductionPlan p", Long.class).getSingleResult()).isZero();
        });
    }

    @Test
    void rejectsRepeatedItemsInsteadOfMergingThem() {
        var request = request(List.of(item(100, 1000L), item(100, 1000L)));
        assertThatThrownBy(() -> service.create(request, userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("중복");
        assertThat(quotationRepository.count()).isZero();
        assertThat(itemRepository.count()).isZero();
    }

    @Test
    void requiresAtLeastOneHundredForEachItem() {
        var tooSmall = request(List.of(item(99, 1000L)));
        assertThat(validator.validate(tooSmall))
                .anyMatch(violation -> violation.getPropertyPath().toString().contains("quantity"));
        assertThatThrownBy(() -> service.create(tooSmall, userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("100개");
        assertThat(validator.validate(request(List.of(item(100, 1000L))))).isEmpty();

        // 합계가 100개여도 각각 최소 수량 미만이면 거부한다.
        assertThatThrownBy(() -> service.create(request(List.of(item(60, 1000L), item(40, 1000L))), userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("100개");
        assertThat(quotationRepository.count()).isZero();
    }

    @Test
    void rollsBackNewClientWhenProductDoesNotExist() {
        var request = newClientRequest("새 회사", List.of(new QuotationItemDto.CreateRequest(999999L, 100, 1000L)));
        assertThatThrownBy(() -> service.create(request, userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("생산품목");
        assertThat(clientRepository.count()).isEqualTo(1);
        assertThat(quotationRepository.count()).isZero();
        assertThat(itemRepository.count()).isZero();
    }

    @Test
    void reusesSameCompanyWithoutOverwritingItsContact() {
        var response = service.create(newClientRequest("기존 회사", List.of(item(100, 1000L))), userId);
        assertThat(response.clientId()).isEqualTo(clientId);
        assertThat(response.clientEmail()).isEqualTo("client@example.com");
        assertThat(clientRepository.count()).isEqualTo(1);
    }

    @Test
    void savesNewClientAndQuotationInOneTransaction() {
        var response = service.create(newClientRequest("새 회사", List.of(item(100, 1000L))), userId);
        assertThat(response.clientCompany()).isEqualTo("새 회사");
        assertThat(clientRepository.count()).isEqualTo(2);
        assertThat(quotationRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsUnknownWriter() {
        assertThatThrownBy(() -> service.create(request(List.of(item(100, 1000L))), Long.MAX_VALUE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThat(quotationRepository.count()).isZero();
    }

    @Test
    void rechecksRoleAccountStateAndPasswordChangeInDatabase() {
        for (String reason : new String[]{"role", "inactive", "password"}) {
            tx().executeWithoutResult(status -> {
                User user = em.find(User.class, userId);
                user.activate();
                user.changePassword("encoded");
                user.update("관리자", "writer@example.com", "010-1234-5678", UserRole.ORDER_MANAGER);
                switch (reason) {
                    case "role" ->
                            user.update("조회 담당", "writer@example.com", "010-1234-5678", UserRole.INQUIRY_MANAGER);
                    case "inactive" -> user.deactivate();
                    case "password" -> user.updatePassword("temporary");
                }
            });
            assertThatThrownBy(() -> service.create(request(List.of(item(100, 1000L))), userId))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        assertThat(quotationRepository.count()).isZero();
    }

    @Test
    void rejectsStoppedProductsAndInvalidInput() {
        for (var items : List.of(List.of(item(100, -1L)), List.<QuotationItemDto.CreateRequest>of())) {
            assertThatThrownBy(() -> service.create(request(items), userId))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        var blankContact = new QuotationDto.CreateRequest(clientId, dueDate(), null, false,
                List.of(item(100, 1000L)), null, "");
        assertThat(validator.validate(blankContact)).isNotEmpty();
        assertThatThrownBy(() -> service.create(blankContact, userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("담당자");

        tx().executeWithoutResult(status -> em.find(Product.class, productId).changeStatus(ProductStatus.DISCONTINUED));
        assertThatThrownBy(() -> service.create(request(List.of(item(100, 1000L))), userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("생산 중단");
        assertThat(quotationRepository.count()).isZero();
    }

    @Test
    void roundsVatAndOddDepositToWonAndSupportsVatIncludedPrices() {
        var response = service.create(request(List.of(item(101, 1L))), userId);
        assertThat(response.totalAmount()).isEqualTo(111L);
        assertThat(response.depositAmount()).isEqualTo(56L);
        assertThat(response.balanceAmount()).isEqualTo(55L);
        assertThat(response.validUntil()).isEqualTo(today().plusDays(14));

        var vatIncluded = new QuotationDto.CreateRequest(clientId, dueDate(), null, true,
                List.of(item(101, 1L)), null, "견적 담당자");
        var includedResponse = service.create(vatIncluded, userId);
        assertThat(includedResponse.totalAmount()).isEqualTo(101L);
        assertThat(includedResponse.supplyAmount()).isEqualTo(92L);
        assertThat(includedResponse.vatAmount()).isEqualTo(9L);
    }

    @Test
    void rejectsAmountOverflowWithoutLeavingPartialData() {
        assertThatThrownBy(() -> service.create(request(List.of(item(100, Long.MAX_VALUE))), userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("금액");
        assertThat(quotationRepository.count()).isZero();
        assertThat(itemRepository.count()).isZero();
    }

    @Test
    void validatesQuotationExpiryAndRequestedDueDate() {
        for (LocalDate expiry : List.of(today().plusDays(6), today().plusDays(15))) {
            var request = new QuotationDto.CreateRequest(clientId, dueDate(), expiry, false,
                    List.of(item(100, 1000L)), null, "견적 담당자");
            assertThatThrownBy(() -> service.create(request, userId))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("유효기간");
        }
        var pastDue = new QuotationDto.CreateRequest(clientId, today().minusDays(1), null, false,
                List.of(item(100, 1000L)), null, "견적 담당자");
        assertThatThrownBy(() -> service.create(pastDue, userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("희망 납기");
    }

    @Test
    void issuedContactAndProductSnapshotsSurviveMasterDataChanges() {
        var response = service.create(request(List.of(item(100, 1000L))), userId);
        tx().executeWithoutResult(status -> {
            ReflectionTestUtils.setField(em.find(Client.class, clientId), "email", "changed@example.com");
            ReflectionTestUtils.setField(em.find(Client.class, clientId), "phone", "010-9999-9999");
            ReflectionTestUtils.setField(em.find(Product.class, productId), "productName", "변경된 품목");
            ReflectionTestUtils.setField(em.find(Product.class, productId), "currentPrice", 9999L);
        });
        var quotation = quotationRepository.findById(response.id()).orElseThrow();
        assertThat(quotation.getEmail()).isEqualTo("client@example.com");
        assertThat(quotation.getPhone()).isEqualTo("02-1234-5678");
        var item = itemRepository.findByQuotationIdOrderByIdAsc(response.id()).getFirst();
        assertThat(item.getProductName()).isEqualTo("테스트 품목");
        assertThat(item.getUnitPrice()).isEqualTo(1000L);
    }

    private QuotationDto.CreateRequest request(List<QuotationItemDto.CreateRequest> items) {
        return new QuotationDto.CreateRequest(clientId, dueDate(), null, false, items, null, "견적 담당자");
    }

    private QuotationDto.CreateRequest newClientRequest(String company, List<QuotationItemDto.CreateRequest> items) {
        var client = new QuotationDto.ClientRequest(company, "대표자", "new@example.com", "010-1111-2222",
                "12345", "서울 테스트로 1", "3층");
        return new QuotationDto.CreateRequest(null, dueDate(), null, false, items, client, "견적 담당자");
    }

    private QuotationItemDto.CreateRequest item(int quantity, Long unitPrice) {
        return new QuotationItemDto.CreateRequest(productId, quantity, unitPrice);
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    private LocalDate dueDate() {
        return today().plusMonths(2);
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
