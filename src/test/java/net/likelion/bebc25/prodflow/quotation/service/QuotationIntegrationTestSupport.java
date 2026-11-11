package net.likelion.bebc25.prodflow.quotation.service;

import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import net.likelion.bebc25.prodflow.client_notification.repository.ClientNotiLogRepository;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.client.repository.ClientRepository;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.common.storage.StorageClient;
import net.likelion.bebc25.prodflow.commoncode.entity.CodeGroup;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.enums.NotifyChannel;
import net.likelion.bebc25.prodflow.customer_order.service.CustomerOrderService;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.mail.service.MailService;
import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationItemDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationItemRepository;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationPdfService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationPdfStorageService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationRescheduleService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationSendService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationStatusService;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

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
    CustomerOrderService orderService;
    @Autowired
    QuotationScheduleService orderScheduleService;
    @Autowired
    QuotationPdfService pdfService;
    @Autowired
    QuotationPdfStorageService pdfStorageService;
    @MockitoBean
    StorageClient storageClient;
    @MockitoBean
    MailService mailService;
    @Autowired
    QuotationSendService sendService;
    @Autowired
    ClientNotiLogRepository notificationRepository;
    protected final Map<String, StorageClient.StoredFile> files
            = new ConcurrentHashMap<>();
    @Autowired
    QuotationRescheduleService rescheduleService;
    @Autowired
    QuotationStatusService statusService;
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
        doAnswer(this::storePdf).when(storageClient).upload(
                anyString(), any(),
                anyString(), anyLong(),
                anyMap());
        when(storageClient.read(anyString()))
                .thenAnswer(invocation -> {
                    StorageClient.StoredFile file = files.get(invocation.getArgument(0, String.class));
                    if (file == null) {
                        throw new IllegalStateException("저장된 파일 없음");
                    }
                    return file;
                });
        when(storageClient.createPresignedGetUrl(
                anyString(), any()))
                .thenReturn("https://example.com/preview.pdf");
        CustomUserDetails principal = tx().execute(status -> {
            // 공통 설정에서 적재한 시드도 외래키의 자식부터 정리한다.
            for (String entity : new String[]{"PaymentNotificationRequest", "PaymentHistory", "VirtualAccount",
                    "Payment", "Shipment",
                    "WorkOrderHistory", "LineNotification", "OrderNotification", "LineCapacityLog",
                    "AiMessage", "AiConversation", "StatusHistory", "RefreshToken", "ProductDrawing",
                    "ClientNotiLog", "ProductionPlan", "WorkOrder", "OrderProduct", "CustomerOrder",
                    "QuotationItem", "Quotation", "Client", "Product", "Line", "CommonCode", "CodeGroup", "User",
                            "Holiday"}) {
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

    protected Object storePdf(org.mockito.invocation.InvocationOnMock invocation) throws IOException {
        InputStream input = invocation.getArgument(1);
        Map<String, String> metadata = invocation.getArgument(4);
        files.put(invocation.getArgument(0),
                new StorageClient.StoredFile(
                        input.readAllBytes(), Map.copyOf(metadata)));
        return null;
    }

    protected void assertSendConflict(Long quotationId,
            QuotationSendDto.Request request) {
        assertThatThrownBy(() -> sendService.send(quotationId, request, userId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(409));
        assertThat(quotationRepository.findById(quotationId).orElseThrow().getStatus()).isEqualTo(QuotationStatus
                .DRAFT);
    }

    protected Long createStatusFixture(QuotationStatus source, LocalDate validUntil) {
        QuotationDto.DetailResponse draft = service.create(request(List.of(item(100, 1000L))), userId);
        tx().executeWithoutResult(status -> {
            Quotation quotation = em.find(Quotation.class, draft.id());
            ReflectionTestUtils.setField(quotation, "status", source);
            ReflectionTestUtils.setField(quotation, "validUntil", validUntil);
            quotation.changePdfObjectKey("quotations/status-test.pdf");
        });
        return draft.id();
    }

    protected void assertStatusConflict(ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
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
            CustomerOrderDto.CreateRequest first,
            CustomerOrderDto.CreateRequest second) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = new ArrayList<>();
            for (CustomerOrderDto.CreateRequest request : List.of(first, second)) {
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
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS),
                    futures.get(1).get(20, TimeUnit.SECONDS));
        }
    }

    protected QuotationDto.DetailResponse approvedForOrder() {
        tx().executeWithoutResult(status -> {
            if (em.createQuery("select count(c) from CommonCode c where c.codeGroup.groupCode = 'ORDER_STATUS'",
                    Long.class)
                    .getSingleResult() == 0) {
                CodeGroup group = instantiate(CodeGroup.class);
                ReflectionTestUtils.setField(group, "groupCode", "ORDER_STATUS");
                ReflectionTestUtils.setField(group, "groupName", "수주 상태");
                em.persist(group);
                CommonCode code = instantiate(CommonCode.class);
                ReflectionTestUtils.setField(code, "codeGroup", group);
                ReflectionTestUtils.setField(code, "code", "WAITING");
                ReflectionTestUtils.setField(code, "name", "대기");
                ReflectionTestUtils.setField(code, "sortOrder", 1);
                ReflectionTestUtils.setField(code, "status", CodeStatus.ACTIVE);
                em.persist(code);
            }
        });
        QuotationDto.DetailResponse quote = createSentQuotation(List.of(item(200, 1000L)));
        tx().executeWithoutResult(status -> em.find(Quotation.class, quote.id()).approve());
        return service.getQuotationById(quote.id());
    }

    protected CustomerOrderDto.CreateRequest orderRequest(
            QuotationDto.DetailResponse quote, int quantity, LocalDate dueDate, boolean companyAddress,
            CustomerOrderDto.ShippingAddress address) {
        return new CustomerOrderDto.CreateRequest(quote.id(),
                List.of(new CustomerOrderDto.ItemRequest(
                        quote.items().getFirst().id(), quantity)), dueDate, companyAddress, address,
                "수신자", "010-1234-5678", NotifyChannel.EMAIL, "업무 메모");
    }

    protected QuotationDto.DetailResponse createSentQuotation(List<QuotationItemDto.CreateRequest> items) {
        QuotationDto.DetailResponse created = service.create(request(items), userId);
        tx().executeWithoutResult(status -> em.find(Quotation.class, created.id()).sent());
        return service.getQuotationById(created.id());
    }
    protected QuotationDto.CreateRequest request(List<QuotationItemDto.CreateRequest> items) {
        return new QuotationDto.CreateRequest(clientId, dueDate(), null, false, items, null);
    }

    protected QuotationDto.CreateRequest newClientRequest(String company, List<QuotationItemDto.CreateRequest> items) {
        QuotationDto.ClientRequest client = new QuotationDto.ClientRequest(company, "신규 담당자", "new@example.com",
                "010-1111-2222",
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
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
