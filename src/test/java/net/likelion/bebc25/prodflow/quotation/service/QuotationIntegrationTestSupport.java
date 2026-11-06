package net.likelion.bebc25.prodflow.quotation.service;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
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

// 커밋·외부 연동 테스트의 공통 준비. 일반 롤백 테스트와 별도로 실행.
@SpringBootTest
abstract class QuotationIntegrationTestSupport {
    @Autowired
    EntityManager em;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    QuotationService service;
    @Autowired
    net.likelion.bebc25.prodflow.customer_order.service.CustomerOrderService orderService;
    @Autowired
    net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleService orderScheduleService;
    @Autowired
    QuotationPdfService pdfService;
    @Autowired
    net.likelion.bebc25.prodflow.quotation.service.QuotationPdfStorageService pdfStorageService;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    net.likelion.bebc25.prodflow.common.storage.StorageClient storageClient;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    net.likelion.bebc25.prodflow.mail.service.MailService mailService;
    @Autowired
    net.likelion.bebc25.prodflow.quotation.service.QuotationSendService sendService;
    @Autowired
    net.likelion.bebc25.prodflow.client_notification.repository.ClientNotiLogRepository notificationRepository;
    protected final java.util.Map<String, net.likelion.bebc25.prodflow.common.storage.StorageClient.StoredFile> files
            = new java.util.concurrent.ConcurrentHashMap<>();
    @Autowired
    QuotationRescheduleService rescheduleService;
    @Autowired
    net.likelion.bebc25.prodflow.quotation.service.QuotationStatusService statusService;
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
        files.clear();
        org.mockito.Mockito.doAnswer(this::storePdf).when(storageClient).upload(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyMap());
        org.mockito.Mockito.when(storageClient.read(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    var file = files.get(invocation.getArgument(0, String.class));
                    if (file == null) {
                        throw new IllegalStateException("저장된 파일 없음");
                    }
                    return file;
                });
        org.mockito.Mockito.when(storageClient.createPresignedGetUrl(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("https://example.com/preview.pdf");
        CustomUserDetails principal = tx().execute(status -> {
            // 공통 설정에서 적재한 시드도 외래키의 자식부터 정리한다.
            for (String entity : new String[]{"PaymentHistory", "VirtualAccount", "Payment", "Shipment",
                    "WorkOrderHistory", "LineNotification", "OrderNotification", "LineCapacityLog",
                    "AiMessage", "AiConversation", "StatusHistory", "RefreshToken", "ProductDrawing",
                    "ClientNotiLog", "ProductionPlan", "WorkOrder", "OrderProduct", "CustomerOrder",
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


    protected Object storePdf(org.mockito.invocation.InvocationOnMock invocation) throws java.io.IOException {
        java.io.InputStream input = invocation.getArgument(1);
        java.util.Map<String, String> metadata = invocation.getArgument(4);
        files.put(invocation.getArgument(0),
                new net.likelion.bebc25.prodflow.common.storage.StorageClient.StoredFile(
                        input.readAllBytes(), java.util.Map.copyOf(metadata)));
        return null;
    }

    protected void assertSendConflict(Long quotationId,
            net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto.Request request) {
        assertThatThrownBy(() -> sendService.send(quotationId, request, userId))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(409));
        assertThat(quotationRepository.findById(quotationId).orElseThrow().getStatus()).isEqualTo(QuotationStatus.DRAFT);
    }

    protected Long createStatusFixture(QuotationStatus source, LocalDate validUntil) {
        var draft = service.create(request(List.of(item(100, 1000L))), userId);
        tx().executeWithoutResult(status -> {
            var quotation = em.find(Quotation.class, draft.id());
            ReflectionTestUtils.setField(quotation, "status", source);
            ReflectionTestUtils.setField(quotation, "validUntil", validUntil);
            quotation.changePdfObjectKey("quotations/status-test.pdf");
        });
        return draft.id();
    }

    protected void assertStatusConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode().value()).isEqualTo(409));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    protected void authenticateAs(UserRole role) {
        CustomUserDetails principal = tx().execute(status -> {
            User user = em.find(User.class, userId);
            user.update(user.getName(), user.getEmail(), user.getPhone(), role, user.getStatus());
            return new CustomUserDetails(user);
        });
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    protected List<Object> registerConcurrently(
            net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.CreateRequest first,
            net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.CreateRequest second) throws Exception {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            List<java.util.concurrent.Future<Object>> futures = new java.util.ArrayList<>();
            for (var request : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    try {
                        start.await();
                        return orderService.create(request, userId);
                    } catch (RuntimeException ex) {
                        return ex;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            start.countDown();
            return List.of(futures.get(0).get(20, java.util.concurrent.TimeUnit.SECONDS),
                    futures.get(1).get(20, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    protected QuotationDto.DetailResponse approvedForOrder() {
        tx().executeWithoutResult(status -> {
            if (em.createQuery("select count(c) from CommonCode c where c.codeGroup.groupCode = 'ORDER_STATUS'", Long.class)
                    .getSingleResult() == 0) {
                var group = instantiate(CodeGroup.class);
                ReflectionTestUtils.setField(group, "groupCode", "ORDER_STATUS");
                ReflectionTestUtils.setField(group, "groupName", "수주 상태");
                em.persist(group);
                var code = instantiate(CommonCode.class);
                ReflectionTestUtils.setField(code, "codeGroup", group);
                ReflectionTestUtils.setField(code, "code", "WAITING");
                ReflectionTestUtils.setField(code, "name", "대기");
                ReflectionTestUtils.setField(code, "sortOrder", 1);
                ReflectionTestUtils.setField(code, "status", CodeStatus.ACTIVE);
                em.persist(code);
            }
        });
        var quote = createSentQuotation(List.of(item(200, 1000L)));
        tx().executeWithoutResult(status -> em.find(Quotation.class, quote.id()).approve());
        return service.getQuotationById(quote.id());
    }

    protected net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.CreateRequest orderRequest(
            QuotationDto.DetailResponse quote, int quantity, LocalDate dueDate, boolean companyAddress,
            net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.ShippingAddress address) {
        return new net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.CreateRequest(quote.id(),
                List.of(new net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto.ItemRequest(
                        quote.items().getFirst().id(), quantity)), dueDate, companyAddress, address,
                "수신자", "010-1234-5678", net.likelion.bebc25.prodflow.customer_order.enums.NotifyChannel.EMAIL, "업무 메모");
    }

    protected QuotationDto.DetailResponse createSentQuotation(List<QuotationItemDto.CreateRequest> items) {
        var created = service.create(request(items), userId);
        tx().executeWithoutResult(status -> em.find(Quotation.class, created.id()).sent());
        return service.getQuotationById(created.id());
    }
    protected QuotationDto.CreateRequest request(List<QuotationItemDto.CreateRequest> items) {
        return new QuotationDto.CreateRequest(clientId, dueDate(), null, false, items, null);
    }

    protected QuotationDto.CreateRequest newClientRequest(String company, List<QuotationItemDto.CreateRequest> items) {
        var client = new QuotationDto.ClientRequest(company, "신규 담당자", "new@example.com", "010-1111-2222",
                "12345", "서울 테스트로 1", "3층");
        return new QuotationDto.CreateRequest(null, dueDate(), null, false, items, client);
    }

    protected QuotationItemDto.CreateRequest item(int quantity, Long unitPrice) {
        return new QuotationItemDto.CreateRequest(productId, quantity, unitPrice);
    }

    protected LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    protected LocalDate dueDate() {
        return today().plusMonths(2);
    }

    protected TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    protected static <T> T instantiate(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
