package net.likelion.bebc25.prodflow.quotation;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.client.repository.ClientRepository;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.commoncode.entity.CodeGroup;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationRescheduleDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationItemDto;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationItemRepository;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import net.likelion.bebc25.prodflow.quotation.service.QuotationService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationPdfService;
import net.likelion.bebc25.prodflow.quotation.controller.QuotationController;
import net.likelion.bebc25.prodflow.quotation.service.QuotationRescheduleService;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;

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
    QuotationPdfService pdfService;
    @Autowired
    net.likelion.bebc25.prodflow.quotation.service.QuotationPdfStorageService pdfStorageService;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    net.likelion.bebc25.prodflow.common.storage.StorageClient storageClient;
    @Autowired
    QuotationRescheduleService rescheduleService;
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
                    "QuotationItem", "Quotation", "Client", "Product", "Line", "CommonCode", "CodeGroup", "User", "Holiday"}) {
                em.createQuery("delete from " + entity).executeUpdate();
            }
            User user = User.builder().name("서도윤").email("writer@example.com")
                    .phone("010-1234-5678").password("encoded").role(UserRole.ORDER_MANAGER).build();
            user.changePassword("encoded");
            em.persist(user);
            userId = user.getId();
            Client client = new Client("기존 회사", "거래처 담당자", "client@example.com", "02-1234-5678",
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

        assertThat(response.status()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(response.totalAmount()).isEqualTo(110000L);
        assertThat(response.supplyAmount()).isEqualTo(100000L);
        assertThat(response.vatAmount()).isEqualTo(10000L);
        assertThat(response.depositAmount()).isEqualTo(55000L);
        assertThat(response.clientName()).isEqualTo("거래처 담당자");
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
        assertThat(quotationRepository.count()).isEqualTo(1);
        assertThat(itemRepository.count()).isEqualTo(1);

        var quotation = quotationRepository.findById(response.id()).orElseThrow();
        assertThat(quotation.getTotalAmount()).isEqualTo(response.totalAmount());
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
                .isInstanceOf(ConstraintViolationException.class).hasMessageContaining("100개");
        assertThat(validator.validate(request(List.of(item(100, 1000L))))).isEmpty();

        // 합계가 100개여도 각각 최소 수량 미만이면 거부한다.
        assertThatThrownBy(() -> service.create(request(List.of(item(60, 1000L), item(40, 1000L))), userId))
                .isInstanceOf(ConstraintViolationException.class).hasMessageContaining("100개");
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
        assertThat(response.clientName()).isEqualTo("거래처 담당자");
        assertThat(clientRepository.count()).isEqualTo(1);
    }

    @Test
    void savesNewClientAndQuotationInOneTransaction() {
        var response = service.create(newClientRequest("새 회사", List.of(item(100, 1000L))), userId);
        assertThat(response.clientCompany()).isEqualTo("새 회사");
        assertThat(response.clientName()).isEqualTo("신규 담당자");
        assertThat(clientRepository.findById(response.clientId()).orElseThrow().getEmployee())
                .isEqualTo(response.clientName());
        assertThat(clientRepository.count()).isEqualTo(2);
        assertThat(quotationRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsUnknownWriter() {
        assertThatThrownBy(() -> service.create(request(List.of(item(100, 1000L))), Long.MAX_VALUE))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessage("작성자 계정을 찾을 수 없습니다.");
        assertThat(quotationRepository.count()).isZero();
    }

    @Test
    void rechecksAccountStateAndPasswordChangeInDatabase() {
        for (String reason : new String[]{"inactive", "password"}) {
            tx().executeWithoutResult(status -> {
                User user = em.find(User.class, userId);
                user.activate();
                user.changePassword("encoded");
                user.update("관리자", "writer@example.com", "010-1234-5678", UserRole.ORDER_MANAGER, user.getStatus());
                switch (reason) {
                    case "inactive" -> user.deactivate();
                    case "password" -> user.updatePassword("temporary");
                }
            });
            assertThatThrownBy(() -> service.create(request(List.of(item(100, 1000L))), userId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage(reason.equals("inactive")
                            ? "사용 중인 계정만 견적을 작성할 수 있습니다." : "비밀번호를 먼저 변경해주세요.");
        }
        assertThat(quotationRepository.count()).isZero();
    }

    @Test
    void superAdminAndOrderManagerCanCreateQuotation() {
        for (UserRole role : List.of(UserRole.SUPER_ADMIN, UserRole.ORDER_MANAGER)) {
            authenticateAs(role);
            var response = service.create(request(List.of(item(100, 1000L))), userId);
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.status()).isEqualTo(QuotationStatus.DRAFT);
        }
        assertThat(quotationRepository.count()).isEqualTo(2);
        assertThat(itemRepository.count()).isEqualTo(2);
    }

    @Test
    void otherRolesCannotCreateQuotationOrLeavePartialData() {
        for (UserRole role : List.of(UserRole.PRODUCTION_MANAGER, UserRole.INQUIRY_MANAGER)) {
            authenticateAs(role);
            assertThatThrownBy(() -> service.create(
                    newClientRequest("권한 검증 회사", List.of(item(100, 1000L))), userId))
                    .isInstanceOf(AccessDeniedException.class);
            assertThat(quotationRepository.count()).isZero();
            assertThat(itemRepository.count()).isZero();
            assertThat(clientRepository.count()).isEqualTo(1);
        }
    }

    private void authenticateAs(UserRole role) {
        CustomUserDetails principal = tx().execute(status -> {
            User user = em.find(User.class, userId);
            user.update(user.getName(), user.getEmail(), user.getPhone(), role, user.getStatus());
            return new CustomUserDetails(user);
        });
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @Test
    void rejectsMissingAndInvalidNestedInputsBeforeSaving() {
        var invalidRequests = List.of(
                new QuotationDto.CreateRequest(clientId, dueDate(), null, false, null, null),
                new QuotationDto.CreateRequest(clientId, null, null, false, List.of(item(100, 1000L)), null),
                request(java.util.Arrays.asList((QuotationItemDto.CreateRequest) null)),
                request(List.of(new QuotationItemDto.CreateRequest(null, 100, 1000L))),
                request(List.of(new QuotationItemDto.CreateRequest(0L, 100, 1000L))),
                request(List.of(new QuotationItemDto.CreateRequest(productId, 100, null))));
        assertThatThrownBy(() -> service.create(null, userId))
                .isInstanceOf(ConstraintViolationException.class);
        for (var invalid : invalidRequests) {
            assertThatThrownBy(() -> service.create(invalid, userId))
                    .isInstanceOf(ConstraintViolationException.class);
        }
        assertThat(quotationRepository.count()).isZero();
        assertThat(itemRepository.count()).isZero();
        assertThat(clientRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsStoppedProductsAndInvalidInput() {
        for (var items : List.of(List.of(item(100, -1L)), List.<QuotationItemDto.CreateRequest>of())) {
            assertThatThrownBy(() -> service.create(request(items), userId))
                    .isInstanceOf(ConstraintViolationException.class);
        }
        var blankContact = new QuotationDto.CreateRequest(null, dueDate(), null, false,
                List.of(item(100, 1000L)), new QuotationDto.ClientRequest("회사", "",
                "client@example.com", "010-1111-2222", "12345", "서울 테스트로", "3층"));
        assertThat(validator.validate(blankContact)).isNotEmpty();
        assertThatThrownBy(() -> service.create(blankContact, userId))
                .isInstanceOf(ConstraintViolationException.class).hasMessageContaining("담당자");

        tx().executeWithoutResult(status -> em.find(Product.class, productId).changeStatus(ProductStatus.DISCONTINUED));
        assertThatThrownBy(() -> service.create(request(List.of(item(100, 1000L))), userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("생산 중단");
        assertThat(quotationRepository.count()).isZero();
    }

    @Test
    void roundsVatAndOddDepositToWonAndSupportsVatIncludedPrices() {
        var response = service.create(request(List.of(item(101, 1L))), userId);
        assertThat(response.totalAmount()).isEqualTo(111L);
        assertThat(quotationRepository.findById(response.id()).orElseThrow().getTotalAmount()).isEqualTo(111L);
        assertThat(response.depositAmount()).isEqualTo(56L);
        assertThat(response.balanceAmount()).isEqualTo(55L);
        assertThat(response.validUntil()).isEqualTo(today().plusDays(14));

        var vatIncluded = new QuotationDto.CreateRequest(clientId, dueDate(), null, true,
                List.of(item(101, 1L)), null);
        var includedResponse = service.create(vatIncluded, userId);
        assertThat(includedResponse.totalAmount()).isEqualTo(101L);
        assertThat(quotationRepository.findById(includedResponse.id()).orElseThrow().getTotalAmount()).isEqualTo(101L);
        assertThat(includedResponse.supplyAmount()).isEqualTo(92L);
        assertThat(includedResponse.vatAmount()).isEqualTo(9L);
    }

    @Test
    void storesTotalAsSumOfIndividuallyRoundedLines() {
        Long secondProductId = tx().execute(status -> {
            Product original = em.find(Product.class, productId);
            Product second = Product.create("TEST-002", "두 번째 품목", original.getDevice(), "설명", 1L);
            em.persist(second);
            return second.getId();
        });
        var response = service.create(request(List.of(item(105, 1L),
                new QuotationItemDto.CreateRequest(secondProductId, 105, 1L))), userId);
        assertThat(response.totalAmount()).isEqualTo(232L);
        assertThat(quotationRepository.findById(response.id()).orElseThrow().getTotalAmount()).isEqualTo(232L);
        assertThat(itemRepository.findByQuotationIdOrderByIdAsc(response.id()))
                .extracting(item -> item.getLineAmount()).containsExactly(116L, 116L);
    }

    @Test
    void rejectsTotalOverflowEvenWhenEachLineFitsWithoutLeavingNewClient() {
        Long secondProductId = tx().execute(status -> {
            Product original = em.find(Product.class, productId);
            Product second = Product.create("TEST-002", "두 번째 품목", original.getDevice(), "설명", 1L);
            em.persist(second);
            return second.getId();
        });
        var request = newClientRequest("총액 초과 회사", List.of(item(100, 50000000000000000L),
                new QuotationItemDto.CreateRequest(secondProductId, 100, 50000000000000000L)));
        assertThatThrownBy(() -> service.create(request, userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("총액");
        assertThat(quotationRepository.count()).isZero();
        assertThat(itemRepository.count()).isZero();
        assertThat(clientRepository.count()).isEqualTo(1);
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
        for (int days : new int[]{6, 8, 9, 10, 11, 12, 13, 15}) {
            LocalDate expiry = today().plusDays(days);
            var request = new QuotationDto.CreateRequest(clientId, dueDate(), expiry, false,
                    List.of(item(100, 1000L)), null);
            assertThatThrownBy(() -> service.create(request, userId))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("유효기간");
        }
        var pastDue = new QuotationDto.CreateRequest(clientId, today().minusDays(1), null, false,
                List.of(item(100, 1000L)), null);
        assertThatThrownBy(() -> service.create(pastDue, userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("희망 납기");
    }

    @Test
    void acceptsOnlySevenOrFourteenDayExpiry() {
        for (int days : new int[]{7, 14}) {
            LocalDate expiry = today().plusDays(days);
            var request = new QuotationDto.CreateRequest(clientId, dueDate(), expiry, false,
                    List.of(item(100, 1000L)), null);

            var response = service.create(request, userId);

            assertThat(response.validUntil()).isEqualTo(expiry);
            assertThat(quotationRepository.findById(response.id()).orElseThrow().getValidUntil()).isEqualTo(expiry);
        }
    }

    @Test
    void issuedContactAndProductSnapshotsSurviveMasterDataChanges() {
        var response = service.create(request(List.of(item(100, 1000L))), userId);
        tx().executeWithoutResult(status -> {
            ReflectionTestUtils.setField(em.find(Client.class, clientId), "employee", "변경된 담당자");
            ReflectionTestUtils.setField(em.find(Client.class, clientId), "email", "changed@example.com");
            ReflectionTestUtils.setField(em.find(Client.class, clientId), "phone", "010-9999-9999");
            ReflectionTestUtils.setField(em.find(Product.class, productId), "productName", "변경된 품목");
            ReflectionTestUtils.setField(em.find(Product.class, productId), "currentPrice", 9999L);
        });
        var quotation = quotationRepository.findById(response.id()).orElseThrow();
        assertThat(quotation.getClientName()).isEqualTo("거래처 담당자");
        assertThat(quotation.getClientEmail()).isEqualTo("client@example.com");
        assertThat(quotation.getTotalAmount()).isEqualTo(response.totalAmount());
        assertThat(quotation.getClientPhone()).isEqualTo("02-1234-5678");
        var item = itemRepository.findByQuotationIdOrderByIdAsc(response.id()).getFirst();
        assertThat(item.getProductName()).isEqualTo("테스트 품목");
        assertThat(item.getUnitPrice()).isEqualTo(1000L);
    }

    @Test
    void detailReturnsSavedAmountsSnapshotsAndScheduleWithoutUpdatingQuotation() {
        for (boolean vatIncluded : new boolean[]{false, true}) {
            var request = new QuotationDto.CreateRequest(clientId, dueDate(), null, vatIncluded,
                    List.of(item(101, 1000L)), null);
            var created = service.create(request, userId);

            tx().executeWithoutResult(status -> {
                ReflectionTestUtils.setField(em.find(Client.class, clientId), "email", "changed@example.com");
                ReflectionTestUtils.setField(em.find(Product.class, productId), "productName", "변경된 품목");
                ReflectionTestUtils.setField(em.find(Product.class, productId), "currentPrice", 9999L);
                em.createQuery("delete from Line").executeUpdate();
            });

            // 생산라인이 없어도 저장된 일정 조회 가능
            var stored = quotationRepository.findById(created.id()).orElseThrow();
            var detail = service.getQuotationById(created.id());
            // DB 시간 정밀도 차이를 제외한 생성·조회 응답 비교
            assertThat(detail).usingRecursiveComparison()
                    .ignoringFields("createdAt", "updatedAt").isEqualTo(created);
            assertThat(detail.createdAt()).isEqualTo(stored.getCreatedAt());
            assertThat(detail.updatedAt()).isEqualTo(stored.getUpdatedAt());
            assertThat(service.getQuotationById(created.id())).isEqualTo(detail);

            // 다음 생성 시 사용할 생산라인 복원
            tx().executeWithoutResult(status -> em.persist(new Line("라인 A", 1000)));
        }
    }

    @Test
    void detailRejectsUnknownQuotation() {
        assertThatThrownBy(() -> service.getQuotationById(Long.MAX_VALUE))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("존재하지 않는 견적서");
    }

    @Test
    void reschedulePreviewUsesStoredQuantityWithoutChangingQuotationOrReservingProduction() {
        var created = createSentQuotation(List.of(item(2500, 1000L)));
        var before = service.getQuotationById(created.id());

        var preview = rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(null));

        assertThat(preview.feasible()).isTrue();
        assertThat(preview.requestedDueDate()).isNull();
        assertThat(preview.plannedEndDate()).isAfter(preview.plannedStartDate());
        assertThat(service.getQuotationById(created.id())).isEqualTo(before);
        assertThat(quotationRepository.count()).isEqualTo(1);
        assertThat(itemRepository.count()).isEqualTo(1);
        tx().executeWithoutResult(status -> {
            assertThat(em.createQuery("select count(w) from WorkOrder w", Long.class).getSingleResult()).isZero();
            assertThat(em.createQuery("select count(p) from ProductionPlan p", Long.class).getSingleResult()).isZero();
        });
    }

    @Test
    void rescheduleChangesOnlyFourDatesAndPreservesIssuedItemsEvenIfProductIsDiscontinued() {
        var created = createSentQuotation(List.of(item(100, 1000L)));
        var before = service.getQuotationById(created.id());
        tx().executeWithoutResult(status ->
                ReflectionTestUtils.setField(em.find(Product.class, productId), "status", ProductStatus.DISCONTINUED));
        var earliest = rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(null));

        var updated = rescheduleService.updateSchedule(created.id(),
                new QuotationRescheduleDto.UpdateRequest(earliest.earliestDueDate()));

        assertThat(updated.requestedDueDate()).isEqualTo(earliest.earliestDueDate()).isNotEqualTo(before.requestedDueDate());
        assertThat(updated.estimatedDueDate()).isEqualTo(earliest.earliestDueDate());
        assertThat(updated.plannedStartDate()).isEqualTo(earliest.plannedStartDate());
        assertThat(updated.plannedEndDate()).isEqualTo(earliest.plannedEndDate());
        assertThat(updated).usingRecursiveComparison()
                .ignoringFields("requestedDueDate", "estimatedDueDate", "plannedStartDate", "plannedEndDate", "updatedAt")
                .isEqualTo(before);
        assertThat(service.getQuotationById(created.id())).usingRecursiveComparison()
                .ignoringFields("updatedAt").isEqualTo(updated);
        assertThat(quotationRepository.count()).isEqualTo(1);
    }

    @Test
    void impossibleRescheduleKeepsOriginalSchedule() {
        var created = createSentQuotation(List.of(item(100, 1000L)));
        var before = service.getQuotationById(created.id());

        var preview = rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(today()));
        assertThat(preview.feasible()).isFalse();
        assertThat(preview.plannedStartDate()).isNull();
        assertThatThrownBy(() -> rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(today())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("희망 납기");
        assertThat(service.getQuotationById(created.id())).isEqualTo(before);
    }

    @Test
    void rescheduleRejectsEveryNonSentStatus() {
        for (QuotationStatus quotationStatus : QuotationStatus.values()) {
            if (quotationStatus == QuotationStatus.SENT) {
                continue;
            }
            var created = createSentQuotation(List.of(item(100, 1000L)));
            tx().executeWithoutResult(status ->
                    ReflectionTestUtils.setField(em.find(Quotation.class, created.id()), "status", quotationStatus));
            assertThatThrownBy(() -> rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(null)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("발송완료");
            assertThatThrownBy(() -> rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(dueDate())))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("발송완료");
        }
    }

    @Test
    void rescheduleChecksExpiryDateEvenBeforeStatusIsExpiredAndAllowsLastValidDay() {
        var created = createSentQuotation(List.of(item(100, 1000L)));
        tx().executeWithoutResult(status ->
                ReflectionTestUtils.setField(em.find(Quotation.class, created.id()), "validUntil", today().minusDays(1)));
        assertThatThrownBy(() -> rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("유효기간");
        assertThatThrownBy(() -> rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(dueDate())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("유효기간");

        tx().executeWithoutResult(status ->
                ReflectionTestUtils.setField(em.find(Quotation.class, created.id()), "validUntil", today()));
        assertThat(rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(dueDate())).validUntil())
                .isEqualTo(today());
    }

    @Test
    void rescheduleRechecksStatusAndCapacityAfterPreview() {
        var created = createSentQuotation(List.of(item(100, 1000L)));
        var preview = rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(null));
        tx().executeWithoutResult(status -> em.find(Quotation.class, created.id()).approve());
        assertThatThrownBy(() -> rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(preview.earliestDueDate())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("발송완료");

        tx().executeWithoutResult(status -> {
            em.find(Quotation.class, created.id()).sent();
            em.createQuery("delete from Line").executeUpdate();
        });
        var before = service.getQuotationById(created.id());
        assertThatThrownBy(() -> rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(preview.earliestDueDate())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("가동 가능한 생산라인");
        assertThat(service.getQuotationById(created.id())).isEqualTo(before);
    }

    @Test
    void rescheduleRejectsMissingQuotationAndDatesBeyondNextYear() {
        assertThatThrownBy(() -> rescheduleService.previewSchedule(Long.MAX_VALUE, new QuotationRescheduleDto.PreviewRequest(null)))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> rescheduleService.updateSchedule(Long.MAX_VALUE, new QuotationRescheduleDto.UpdateRequest(dueDate())))
                .isInstanceOf(NoSuchElementException.class);
        var created = createSentQuotation(List.of(item(100, 1000L)));
        LocalDate beyond = LocalDate.of(today().getYear() + 2, 1, 1);
        assertThatThrownBy(() -> rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(beyond)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("다음 해");
        assertThatThrownBy(() -> rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(beyond)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("다음 해");
    }

    @Test
    void rescheduleRequiresOrderManagerOrSuperAdminRole() {
        var created = createSentQuotation(List.of(item(100, 1000L)));
        for (UserRole role : List.of(UserRole.PRODUCTION_MANAGER, UserRole.INQUIRY_MANAGER)) {
            authenticateAs(role);
            assertThatThrownBy(() -> rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(null)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> rescheduleService.updateSchedule(created.id(), new QuotationRescheduleDto.UpdateRequest(dueDate())))
                    .isInstanceOf(AccessDeniedException.class);
        }
        authenticateAs(UserRole.SUPER_ADMIN);
        assertThat(rescheduleService.previewSchedule(created.id(), new QuotationRescheduleDto.PreviewRequest(null)).feasible()).isTrue();
    }

    @Test
    void createsDraftAndGeneratesPdfWithoutChangingStatusOrReservingProduction() throws Exception {
        var created = service.create(request(List.of(item(100, 1000L))), userId);
        byte[] pdf = pdfService.generate(created);
        try (var reader = new org.openpdf.text.pdf.PdfReader(pdf)) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
        }
        assertThat(quotationRepository.findById(created.id()).orElseThrow().getStatus())
                .isEqualTo(QuotationStatus.DRAFT);
        assertThat(quotationRepository.count()).isEqualTo(1);
        Long workOrderCount = tx().execute(status -> em.createQuery("select count(w) from WorkOrder w", Long.class)
                .getSingleResult());
        Long planCount = tx().execute(status -> em.createQuery("select count(p) from ProductionPlan p", Long.class)
                .getSingleResult());
        assertThat(workOrderCount).isZero();
        assertThat(planCount).isZero();
        assertThatThrownBy(() -> rescheduleService.previewSchedule(created.id(),
                new QuotationRescheduleDto.PreviewRequest(null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("발송완료");
    }

    @Test
    void pdfFailureAfterControllerSaveDoesNotRollBackDraftOrNewClient() {
        var failingPdf = org.mockito.Mockito.mock(QuotationPdfService.class);
        org.mockito.Mockito.when(failingPdf.generate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("PDF 생성 실패"));
        var failingStorage = new net.likelion.bebc25.prodflow.quotation.service.QuotationPdfStorageService(
                quotationRepository, service, failingPdf, storageClient, transactionManager);
        var controller = new QuotationController(service, null, null, failingStorage);
        var principal = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        var failure = org.junit.jupiter.api.Assertions.assertThrows(
                net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException.class,
                () -> controller.createPdf(principal,
                        newClientRequest("PDF 실패 거래처", List.of(item(100, 1000L)))));
        Long draftId = failure.getQuotationId();
        assertThat(quotationRepository.findById(draftId).orElseThrow().getStatus()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(itemRepository.findByQuotationIdOrderByIdAsc(draftId)).hasSize(1);
        assertThat(clientRepository.count()).isEqualTo(2);
        assertThat(pdfService.generate(service.getQuotationById(draftId))).isNotEmpty();
        assertThat(quotationRepository.count()).isEqualTo(1);
    }

    @Test
    void otherRolesCannotGenerateDraftPdf() {
        var draft = service.create(request(List.of(item(100, 1000L))), userId);
        for (UserRole role : List.of(UserRole.PRODUCTION_MANAGER, UserRole.INQUIRY_MANAGER)) {
            authenticateAs(role);
            assertThatThrownBy(() -> pdfService.generate(draft)).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void uploadsPdfAndStoresKeyOnlyAfterSuccess() {
        var draft = service.create(request(List.of(item(100, 1000L))), userId);
        String key = "quotations/" + draft.quotationNo() + ".pdf";
        tx().executeWithoutResult(status -> em.find(Quotation.class, draft.id()).changePdfObjectKey(key));
        org.mockito.Mockito.doAnswer(invocation -> {
            var independent = new TransactionTemplate(transactionManager);
            independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            String persistedKey = independent.execute(status ->
                    quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey());
            assertThat(persistedKey).isNull();
            return null;
        }).when(storageClient).upload(org.mockito.ArgumentMatchers.eq(key), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.when(storageClient.createPresignedGetUrl(
                org.mockito.ArgumentMatchers.eq(key), org.mockito.ArgumentMatchers.any()))
                .thenReturn("https://example.com/preview.pdf");
        var preview = pdfStorageService.prepare(draft.id());
        assertThat(preview.quotationId()).isEqualTo(draft.id());
        assertThat(preview.previewUrl()).isEqualTo("https://example.com/preview.pdf");
        assertThat(quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey()).isEqualTo(key);
        var order = org.mockito.Mockito.inOrder(storageClient);
        order.verify(storageClient).delete(key);
        order.verify(storageClient).upload(org.mockito.ArgumentMatchers.eq(key), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("application/pdf"), org.mockito.ArgumentMatchers.longThat(length -> length > 0));
        org.mockito.Mockito.clearInvocations(storageClient);
        assertThat(pdfStorageService.getPreview(draft.id()).previewUrl()).isEqualTo(preview.previewUrl());
        org.mockito.Mockito.verify(storageClient, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(storageClient, org.mockito.Mockito.never()).upload(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        service.updateDraft(draft.id(), request(List.of(item(200, 1000L))), userId);
        assertThat(quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey()).isNull();
        assertThatThrownBy(() -> pdfStorageService.getPreview(draft.id()))
                .isInstanceOf(net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException.class);
    }

    @Test
    void deletionAndUploadFailureClearPreviouslyStoredKeyAndRetryKeepsSameDraft() {
        var draft = service.create(request(List.of(item(100, 1000L))), userId);
        String key = "quotations/" + draft.quotationNo() + ".pdf";
        tx().executeWithoutResult(status -> em.find(Quotation.class, draft.id()).changePdfObjectKey(key));
        org.mockito.Mockito.doThrow(new RuntimeException("삭제 실패")).when(storageClient).delete(key);
        org.mockito.Mockito.doThrow(new RuntimeException("업로드 실패")).when(storageClient).upload(
                org.mockito.ArgumentMatchers.eq(key), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> pdfStorageService.prepare(draft.id()))
                    .isInstanceOf(net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException.class);
            assertThat(quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey()).isNull();
            assertThatThrownBy(() -> pdfStorageService.getPreview(draft.id()))
                    .isInstanceOf(net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException.class);
        }
        org.mockito.Mockito.doNothing().when(storageClient).upload(org.mockito.ArgumentMatchers.eq(key),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.when(storageClient.createPresignedGetUrl(
                org.mockito.ArgumentMatchers.eq(key), org.mockito.ArgumentMatchers.any())).thenReturn("https://example.com/retry.pdf");
        assertThat(pdfStorageService.prepare(draft.id()).previewUrl()).isEqualTo("https://example.com/retry.pdf");
        assertThat(quotationRepository.count()).isEqualTo(1);
        assertThat(quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey()).isEqualTo(key);
    }

    @Test
    void pdfGenerationFailureCommitsNullKeyWithoutDeletingStoredFile() {
        var draft = service.create(request(List.of(item(100, 1000L))), userId);
        tx().executeWithoutResult(status -> em.find(Quotation.class, draft.id())
                .changePdfObjectKey("quotations/old.pdf"));
        var failingPdf = org.mockito.Mockito.mock(QuotationPdfService.class);
        org.mockito.Mockito.when(failingPdf.generate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("한 페이지 초과"));
        var failingService = new net.likelion.bebc25.prodflow.quotation.service.QuotationPdfStorageService(
                quotationRepository, service, failingPdf, storageClient, transactionManager);
        assertThatThrownBy(() -> failingService.prepare(draft.id()))
                .isInstanceOfSatisfying(net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException.class,
                        failure -> assertThat(failure.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));
        assertThat(quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey()).isNull();
        org.mockito.Mockito.verifyNoInteractions(storageClient);
    }

    @Test
    void urlSigningFailureKeepsKeyNullEvenAfterUpload() {
        var draft = service.create(request(List.of(item(100, 1000L))), userId);
        org.mockito.Mockito.when(storageClient.createPresignedGetUrl(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("URL 서명 실패"));
        assertThatThrownBy(() -> pdfStorageService.prepare(draft.id()))
                .isInstanceOf(net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException.class);
        assertThat(quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey()).isNull();
        assertThat(quotationRepository.count()).isEqualTo(1);
    }

    @Test
    void storageOperationsRequirePermissionAndCannotOverwriteSentPdf() {
        var draft = service.create(request(List.of(item(100, 1000L))), userId);
        authenticateAs(UserRole.INQUIRY_MANAGER);
        assertThatThrownBy(() -> pdfStorageService.prepare(draft.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> pdfStorageService.getPreview(draft.id())).isInstanceOf(AccessDeniedException.class);
        authenticateAs(UserRole.ORDER_MANAGER);
        tx().executeWithoutResult(status -> {
            var quotation = em.find(Quotation.class, draft.id());
            quotation.changePdfObjectKey("quotations/sent.pdf");
            quotation.sent();
        });
        assertThatThrownBy(() -> pdfStorageService.prepare(draft.id())).isInstanceOf(IllegalArgumentException.class);
        assertThat(quotationRepository.findById(draft.id()).orElseThrow().getPdfObjectKey()).isEqualTo("quotations/sent.pdf");
        org.mockito.Mockito.verifyNoInteractions(storageClient);
    }

    @Test
    void updatesSameDraftAndPersistsChangedVatAmountsAndItemsAcrossRepeatedEdits() throws Exception {
        var original = service.create(request(List.of(item(100, 1000L))), userId);
        for (boolean included : new boolean[]{true, false}) {
            var request = new QuotationDto.CreateRequest(clientId, dueDate().plusDays(1), null, included,
                    List.of(item(250, 2000L)), null);
            var updated = service.updateDraft(original.id(), request, userId);
            assertThat(updated.id()).isEqualTo(original.id());
            assertThat(updated.quotationNo()).isEqualTo(original.quotationNo());
            assertThat(updated.userId()).isEqualTo(original.userId());
            assertThat(updated.status()).isEqualTo(QuotationStatus.DRAFT);
            assertThat(updated.totalQuantity()).isEqualTo(250);
            assertThat(updated.totalAmount()).isEqualTo(included ? 500000L : 550000L);
            assertThat(updated.clientName()).isEqualTo("거래처 담당자");
            assertThat(updated.requestedDueDate()).isEqualTo(dueDate().plusDays(1));
            assertThat(service.getQuotationById(original.id()).totalAmount()).isEqualTo(updated.totalAmount());
            assertThat(itemRepository.findByQuotationIdOrderByIdAsc(original.id())).hasSize(1);
            assertThat(quotationRepository.count()).isEqualTo(1);
            try (var reader = new org.openpdf.text.pdf.PdfReader(pdfService.generate(updated))) {
                assertThat(new org.openpdf.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1))
                        .contains("거래처 담당자", included ? "500,000" : "550,000");
            }
        }
    }

    @Test
    void replacesRemovedAndAddedDraftProductsAndPreservesOriginalWriter() {
        Long otherProductId = tx().execute(status -> {
            Product other = Product.create("TEST-002", "다른 품목", em.find(Product.class, productId).getDevice(),
                    "설명", 3000L);
            em.persist(other);
            return other.getId();
        });
        var original = service.create(request(List.of(item(100, 1000L))), userId);
        service.updateDraft(original.id(), request(List.of(item(100, 1000L),
                new QuotationItemDto.CreateRequest(otherProductId, 100, 3000L))), userId);
        assertThat(itemRepository.findByQuotationIdOrderByIdAsc(original.id())).hasSize(2);

        Long editorId = tx().execute(status -> {
            User editor = User.builder().name("다른 직원").email("editor@example.com")
                    .phone("010-2222-3333").password("encoded").role(UserRole.ORDER_MANAGER).build();
            editor.changePassword("encoded");
            em.persist(editor);
            return editor.getId();
        });
        var updated = service.updateDraft(original.id(), request(List.of(
                new QuotationItemDto.CreateRequest(otherProductId, 200, 3000L))), editorId);
        assertThat(updated.userId()).isEqualTo(original.userId());
        assertThat(updated.totalAmount()).isEqualTo(660000L);
        assertThat(itemRepository.findByQuotationIdOrderByIdAsc(original.id()))
                .extracting(item -> item.getProduct().getId()).containsExactly(otherProductId);
    }

    @Test
    void invalidDraftEditRollsBackQuotationItemsAndNewClient() {
        var original = service.create(request(List.of(item(100, 1000L))), userId);
        var before = service.getQuotationById(original.id());
        var itemsBefore = itemRepository.findByQuotationIdOrderByIdAsc(original.id());
        var invalid = newClientRequest("롤백 거래처", List.of(
                new QuotationItemDto.CreateRequest(Long.MAX_VALUE, 200, 2000L)));
        assertThatThrownBy(() -> service.updateDraft(original.id(), invalid, userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("생산품목");
        assertThat(service.getQuotationById(original.id())).isEqualTo(before);
        assertThat(itemRepository.findByQuotationIdOrderByIdAsc(original.id()))
                .extracting(item -> item.getId()).containsExactly(itemsBefore.getFirst().getId());
        assertThat(clientRepository.count()).isEqualTo(1);
        assertThat(quotationRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsEveryNonDraftStatusAndMissingQuotationOnEdit() {
        var original = service.create(request(List.of(item(100, 1000L))), userId);
        for (QuotationStatus status : QuotationStatus.values()) {
            if (status == QuotationStatus.DRAFT) {
                continue;
            }
            tx().executeWithoutResult(transaction ->
                    ReflectionTestUtils.setField(em.find(Quotation.class, original.id()), "status", status));
            assertThatThrownBy(() -> service.updateDraft(original.id(), request(List.of(item(200, 2000L))), userId))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("초안");
            assertThat(service.getQuotationById(original.id()).totalAmount()).isEqualTo(original.totalAmount());
        }
        assertThatThrownBy(() -> service.updateDraft(Long.MAX_VALUE, request(List.of(item(100, 1000L))), userId))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void draftEditRechecksRoleAccountAndNestedInputs() {
        var original = service.create(request(List.of(item(100, 1000L))), userId);
        for (UserRole role : List.of(UserRole.PRODUCTION_MANAGER, UserRole.INQUIRY_MANAGER)) {
            authenticateAs(role);
            assertThatThrownBy(() -> service.updateDraft(original.id(), request(List.of(item(200, 2000L))), userId))
                    .isInstanceOf(AccessDeniedException.class);
        }
        authenticateAs(UserRole.ORDER_MANAGER);
        assertThatThrownBy(() -> service.updateDraft(original.id(), request(List.of(item(50, 1000L))), userId))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.updateDraft(original.id(), request(List.of(item(100, 1000L))), Long.MAX_VALUE))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        tx().executeWithoutResult(status -> em.find(User.class, userId).deactivate());
        assertThatThrownBy(() -> service.updateDraft(original.id(), request(List.of(item(200, 2000L))), userId))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(service.getQuotationById(original.id()).totalAmount()).isEqualTo(original.totalAmount());
    }

    @Test
    void pdfFailureAfterEditKeepsCommittedChangesAndCanRegenerateWithoutNewDraft() {
        var original = service.create(new QuotationDto.CreateRequest(clientId, dueDate(), today().plusDays(7),
                false, List.of(item(100, 1000L)), null), userId);
        var failingPdf = org.mockito.Mockito.mock(QuotationPdfService.class);
        org.mockito.Mockito.when(failingPdf.generate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("PDF 실패"));
        var failingStorage = new net.likelion.bebc25.prodflow.quotation.service.QuotationPdfStorageService(
                quotationRepository, service, failingPdf, storageClient, transactionManager);
        var controller = new QuotationController(service, null, null, failingStorage);
        var principal = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThatThrownBy(() -> controller.updatePdf(original.id(), principal,
                request(List.of(item(300, 2000L)))))
                .isInstanceOfSatisfying(
                        net.likelion.bebc25.prodflow.quotation.exception.QuotationPdfPreparationException.class,
                        failure -> assertThat(failure.getQuotationId()).isEqualTo(original.id()));
        var updated = service.getQuotationById(original.id());
        assertThat(updated.totalAmount()).isEqualTo(660000L);
        assertThat(updated.validUntil()).isEqualTo(original.validUntil());
        assertThat(updated.quotationNo()).isEqualTo(original.quotationNo());
        assertThat(quotationRepository.count()).isEqualTo(1);
        assertThat(pdfService.generate(updated)).isNotEmpty();
    }

    @Test
    void switchingDraftClientCopiesSelectedClientsEmployeeAndKeepsQuotationNumber() {
        var original = service.create(request(List.of(item(100, 1000L))), userId);
        var updated = service.updateDraft(original.id(), newClientRequest("변경 거래처", List.of(item(200, 1000L))), userId);
        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.quotationNo()).isEqualTo(original.quotationNo());
        assertThat(updated.clientName()).isEqualTo("신규 담당자");
        assertThat(updated.clientEmail()).isEqualTo("new@example.com");
        assertThat(quotationRepository.findById(original.id()).orElseThrow().getClientName())
                .isEqualTo(clientRepository.findById(updated.clientId()).orElseThrow().getEmployee());
        assertThat(clientRepository.count()).isEqualTo(2);
        assertThat(quotationRepository.count()).isEqualTo(1);
    }

    @Test
    void supplierContactKeepsCreatedByAfterWriterNameChanges() throws Exception {
        var created = service.create(request(List.of(item(100, 1000L))), userId);
        assertThat(created.createdBy()).isEqualTo("서도윤");
        tx().executeWithoutResult(status ->
                ReflectionTestUtils.setField(em.find(User.class, userId), "name", "변경된 이름"));
        var detail = service.getQuotationById(created.id());
        assertThat(detail.userName()).isEqualTo("변경된 이름");
        assertThat(detail.createdBy()).isEqualTo("서도윤");
        try (var reader = new org.openpdf.text.pdf.PdfReader(pdfService.generate(detail))) {
            assertThat(new org.openpdf.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1))
                    .contains("담당자: 서도윤").doesNotContain("변경된 이름");
        }
    }

    private QuotationDto.DetailResponse createSentQuotation(List<QuotationItemDto.CreateRequest> items) {
        var created = service.create(request(items), userId);
        tx().executeWithoutResult(status -> em.find(Quotation.class, created.id()).sent());
        return service.getQuotationById(created.id());
    }
    private QuotationDto.CreateRequest request(List<QuotationItemDto.CreateRequest> items) {
        return new QuotationDto.CreateRequest(clientId, dueDate(), null, false, items, null);
    }

    private QuotationDto.CreateRequest newClientRequest(String company, List<QuotationItemDto.CreateRequest> items) {
        var client = new QuotationDto.ClientRequest(company, "신규 담당자", "new@example.com", "010-1111-2222",
                "12345", "서울 테스트로 1", "3층");
        return new QuotationDto.CreateRequest(null, dueDate(), null, false, items, client);
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
