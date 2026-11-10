package net.likelion.bebc25.prodflow.payment.service;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.enums.NotifyChannel;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.payment.dto.PaymentDto;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.*;
import net.likelion.bebc25.prodflow.payment.repository.PaymentHistoryRepository;
import net.likelion.bebc25.prodflow.payment.repository.PaymentRepository;
import net.likelion.bebc25.prodflow.payment.repository.VirtualAccountRepository;
import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto;
import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrderHistory;
import net.likelion.bebc25.prodflow.work_order.repository.ProductionPlanRepository;
import net.likelion.bebc25.prodflow.work_order.repository.WorkOrderHistoryRepository;
import net.likelion.bebc25.prodflow.work_order.service.WorkOrderService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:manual-deposit-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER,LINE")
@Transactional
class ManualDepositServiceTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentHistoryRepository paymentHistoryRepository;

    @Autowired
    private VirtualAccountRepository virtualAccountRepository;

    @Autowired
    private ProductionPlanRepository productionPlanRepository;

    @Autowired
    private StatusHistoryRepository statusHistoryRepository;

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private WorkOrderHistoryRepository workOrderHistoryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long orderId;
    private Long quotationId;
    private Long clientId;
    private Long actorId;
    private Long managerId;
    private List<Long> productIds;
    private User actor;
    private LocalDate start;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {

            actor = User.builder()
                    .name("현금 처리 직원")
                    .email("cash-actor@test.com")
                    .password("1234")
                    .phone("010-1111-2222")
                    .role(UserRole.ORDER_MANAGER)
                    .build();

            actor.changePassword("changed");
            em.persist(actor);
            actorId = actor.getId();

            User manager = User.builder()
                    .name("현금 생산 담당자")
                    .email("cash-manager@test.com")
                    .password("1234")
                    .phone("010-2222-3333")
                    .role(UserRole.PRODUCTION_MANAGER)
                    .build();

            em.persist(manager);
            managerId = manager.getId();

            Client client = new Client(
                    "현금 테스트 거래처",
                    "담당자",
                    "cash@test.com",
                    "02-1234-5678",
                    new Address("12345", "서울 테스트로", "1층"),
                    actor
            );

            em.persist(client);
            clientId = client.getId();

            start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(14);

            while (start.getDayOfWeek() == DayOfWeek.SATURDAY
                    || start.getDayOfWeek() == DayOfWeek.SUNDAY) {
                start = start.plusDays(1);
            }

            LocalDate end = start.plusDays(4);

            Quotation quotation = new Quotation(
                    "QT-CASH-TEST",
                    client,
                    client.getEmployee(),
                    actor,
                    end.plusDays(7),
                    end.plusDays(7),
                    start,
                    end,
                    end,
                    true,
                    QuotationStatus.APPROVED,
                    10001L
            );

            em.persist(quotation);
            quotationId = quotation.getId();

            CustomerOrder order = new CustomerOrder(
                    "PF-CASH-TEST",
                    quotation,
                    client,
                    client.getEmployee(),
                    client.getEmail(),
                    client.getPhone(),
                    "회사 주소",
                    actor,
                    end.plusDays(7),
                    end.plusDays(7),
                    start,
                    end,
                    LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(3),
                    "배송 주소",
                    "수신자",
                    "010-3333-4444",
                    NotifyChannel.EMAIL,
                    null,
                    em.find(CommonCode.class, 6L),
                    true,
                    10001L
            );

            em.persist(order);
            orderId = order.getId();

            Product first = Product.create(
                    "CASH-001",
                    "현금 품목 A",
                    em.find(CommonCode.class, 1L),
                    "설명",
                    100L
            );

            Product second = Product.create(
                    "CASH-002",
                    "현금 품목 B",
                    em.find(CommonCode.class, 1L),
                    "설명",
                    1L
            );

            em.persist(first);
            em.persist(second);

            productIds = List.of(first.getId(), second.getId());

            em.persist(new OrderProduct(
                    order, first, first.getProductName(), 100, 100L
            ));

            em.persist(new OrderProduct(
                    order, second, second.getProductName(), 1, 1L
            ));

            em.flush();
        });

        authenticate();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {

                em.createQuery(
                        "delete from WorkOrderHistory h where h.changedBy.id = :actorId"
                ).setParameter("actorId", actorId).executeUpdate();

                em.createQuery(
                        "delete from net.likelion.bebc25.prodflow.common.entity.StatusHistory h where h.user.id = :actorId"
                ).setParameter("actorId", actorId).executeUpdate();

                em.createQuery(
                        "update WorkOrder w set w.user = null where w.user.id = :managerId"
                ).setParameter("managerId", managerId).executeUpdate();

                List<Long> jobs = em.createQuery(
                        "select w.id from WorkOrder w where w.orderProduct.customerOrder.id = :id",
                        Long.class
                ).setParameter("id", orderId).getResultList();

                for (Long job : jobs) {
                    em.createQuery(
                                    "delete from net.likelion.bebc25.prodflow.common.entity.StatusHistory h where h.entity = :entity and h.entityId = :id"
                            ).setParameter("entity", HistoryEntityType.WORK_ORDER)
                            .setParameter("id", job)
                            .executeUpdate();
                }

                em.createQuery(
                                "delete from net.likelion.bebc25.prodflow.common.entity.StatusHistory h where h.entity = :entity and h.entityId = :id"
                        ).setParameter("entity", HistoryEntityType.ORDER)
                        .setParameter("id", orderId)
                        .executeUpdate();

                em.createQuery(
                        "delete from ProductionPlan p where p.workOrder.orderProduct.customerOrder.id = :id"
                ).setParameter("id", orderId).executeUpdate();

                em.createQuery(
                        "delete from WorkOrder w where w.orderProduct.customerOrder.id = :id"
                ).setParameter("id", orderId).executeUpdate();

                em.createQuery(
                        "delete from PaymentHistory h where h.payment.order.id = :id"
                ).setParameter("id", orderId).executeUpdate();

                em.createQuery(
                        "delete from VirtualAccount v where v.payment.order.id = :id"
                ).setParameter("id", orderId).executeUpdate();

                em.createQuery(
                        "delete from Payment p where p.order.id = :id"
                ).setParameter("id", orderId).executeUpdate();

                em.createQuery(
                        "delete from OrderProduct p where p.customerOrder.id = :id"
                ).setParameter("id", orderId).executeUpdate();

                em.createQuery(
                        "delete from CustomerOrder o where o.id = :id"
                ).setParameter("id", orderId).executeUpdate();

                em.createQuery(
                        "delete from Quotation q where q.id = :id"
                ).setParameter("id", quotationId).executeUpdate();

                em.createQuery(
                        "delete from Product p where p.id in :ids"
                ).setParameter("ids", productIds).executeUpdate();

                em.createQuery(
                        "delete from Client c where c.id = :id"
                ).setParameter("id", clientId).executeUpdate();

                em.createQuery(
                        "delete from User u where u.id in :ids"
                ).setParameter("ids", List.of(actorId, managerId)).executeUpdate();
            });
        }
    }

    @Test
    @DisplayName("현금 계약금과 상태 이력·작업지시·날짜별 계획을 함께 저장한다")
    void recordManualDeposit() {
        OffsetDateTime paidAt =
                OffsetDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(1);

        PaymentDto.ManualDepositResponse response =
                paymentService.recordManualDeposit(
                        new PaymentDto.ManualDepositRequest(
                                orderId, 5000L, paidAt
                        ),
                        actorId
                );

        em.flush();
        em.clear();

        assertThat(response.amount()).isEqualTo(5000);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.MD);
        assertThat(response.paidAt()).isEqualTo(paidAt);
        assertThat(response.orderStatus()).isEqualTo("CONFIRMED");
        assertThat(response.confirmationStatus().name()).isEqualTo("CONFIRMED");
        assertThat(response.paymentStatus())
                .isEqualTo(OrderPaymentStatus.PARTIALLY_PAID);
        assertThat(response.workOrderIds()).hasSize(2);

        Payment payment = paymentRepository
                .findById(response.paymentId())
                .orElseThrow();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);

        List<PaymentHistory> histories =
                paymentHistoryRepository.findByPayment_Order_Id(orderId);

        assertThat(histories).hasSize(1);
        assertThat(histories.getFirst().getVirtualAccount()).isNull();
        assertThat(virtualAccountRepository.findLatestByOrderId(orderId))
                .isEmpty();

        assertThat(
                statusHistoryRepository.findByEntityAndEntityIdOrderByCreatedAtDescIdDesc(
                        HistoryEntityType.ORDER, orderId
                )
        ).singleElement().satisfies(history -> {
            assertThat(history.getFromStatus()).isEqualTo("WAITING");
            assertThat(history.getToStatus()).isEqualTo("CONFIRMED");
            assertThat(history.getUserName()).isEqualTo("현금 처리 직원");
            assertThat(history.getNote()).contains("5000");
        });

        long quantity = 0;

        for (Long id : response.workOrderIds()) {
            WorkOrder job = em.find(WorkOrder.class, id);

            assertThat(job.getCommonCode().getCode()).isEqualTo("PREPARE");
            assertThat(job.getPlannedStartDate()).isAfterOrEqualTo(start);
            assertThat(
                    productionPlanRepository.findByWorkOrder_Id(id)
                            .stream()
                            .mapToInt(p -> p.getQuantity())
                            .sum()
            ).isEqualTo(job.getQuantity());

            quantity += job.getQuantity();
        }

        assertThat(quantity).isEqualTo(101);
    }

    // 수정 1: 기존 가상계좌가 있으면 수동 입금 거부
    @Test
    @DisplayName("활성 가상계좌가 있으면 PG 말소 확인 전 현금 계약금을 거부한다")
    void rejectManualDepositWhenActiveVirtualAccountExists() {

        Payment payment = paymentRepository.save(
                Payment.builder()
                        .order(em.find(CustomerOrder.class, orderId))
                        .paymentType(PaymentType.DEPOSIT)
                        .amount(5000L)
                        .portonePaymentId("EXISTING-CASH")
                        .build()
        );

        // READY 상태를 유지해서 활성 가상계좌 검사까지 진행
        VirtualAccount account = virtualAccountRepository.save(
                VirtualAccount.builder()
                        .payment(payment)
                        .bank(Bank.SHINHAN)
                        .accountNumber("EXISTING-ACCOUNT")
                        .accountName("기존 계좌")
                        .expiresAt(
                                OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
                                        .plusDays(30)
                        )
                        .build()
        );

        // 활성 가상계좌가 존재하면 수동 입금은 거부되어야 함
        assertThatThrownBy(
                () -> paymentService.recordManualDeposit(request(), actorId)
        ).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode().value()).isEqualTo(409);
            assertThat(exception.getReason()).contains("활성 가상계좌");
        });

        // 기존 데이터가 변경되지 않았는지 확인
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(account.getStatus()).isEqualTo(VirtualAccountStatus.ACTIVE);

        assertThat(
                paymentHistoryRepository.findByPayment_Order_Id(orderId)
        ).isEmpty();

        assertThat(
                em.find(CustomerOrder.class, orderId).getPaymentStatus()
        ).isEqualTo(OrderPaymentStatus.UNPAID);
    }

    @Test
    @DisplayName("계약금과 다른 금액·미래 입금일시는 거부한다")
    void rejectInvalidAmountOrTime() {

        assertThatThrownBy(
                () -> paymentService.recordManualDeposit(
                        new PaymentDto.ManualDepositRequest(
                                orderId, 4999L, null
                        ),
                        actorId
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("계약금 예정액");

        assertThatThrownBy(
                () -> paymentService.recordManualDeposit(
                        new PaymentDto.ManualDepositRequest(
                                orderId,
                                5000L,
                                OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
                                        .plusDays(1)
                        ),
                        actorId
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("미래");

        assertThat(
                paymentRepository.findByOrder_IdOrderByIdAsc(orderId)
        ).isEmpty();
    }

    @Test
    @DisplayName("취소 수주와 비활성 처리 직원은 입금을 반영하지 않는다")
    void rejectCancelledOrderOrInactiveActor() {

        em.find(CustomerOrder.class, orderId)
                .changeProgress(em.find(CommonCode.class, 15L));

        assertThatThrownBy(
                () -> paymentService.recordManualDeposit(request(), actorId)
        ).isInstanceOf(ResponseStatusException.class);

        actor.deactivate();

        assertThatThrownBy(
                () -> paymentService.recordManualDeposit(request(), actorId)
        ).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("동일 계약금을 다시 처리해도 결제·입금·작업지시는 추가하지 않는다")
    void rejectDuplicateDeposit() {

        paymentService.recordManualDeposit(request(), actorId);

        long count = paymentRepository.count();

        assertThatThrownBy(
                () -> paymentService.recordManualDeposit(request(), actorId)
        ).isInstanceOf(ResponseStatusException.class);

        assertThat(paymentRepository.count()).isEqualTo(count);

        assertThat(
                paymentHistoryRepository.findByPayment_Order_Id(orderId)
        ).hasSize(1);
    }

    @Test
    @DisplayName("입금이 늦은 선등록 대기 수주의 생산 자리를 먼저 확보한다")
    void reserveEarlierWaitingOrder() {

        CustomerOrder target = em.find(CustomerOrder.class, orderId);

        List<Line> lines =
                em.createQuery("select l from Line l", Line.class).getResultList();

        int capacity = lines.stream()
                .filter(line ->
                        line.getStatus() == LineStatus.AVAILABLE
                                || line.getStatus() == LineStatus.OPERATING
                )
                .mapToInt(Line::getProductionCapacity)
                .sum();

        Quotation quotation = new Quotation(
                "QT-CASH-EARLIER",
                target.getClient(),
                "담당자",
                actor,
                start.plusDays(7),
                start.plusDays(7),
                start,
                start,
                start.plusDays(7),
                true,
                QuotationStatus.APPROVED,
                (long) capacity
        );

        em.persist(quotation);

        CustomerOrder earlier = new CustomerOrder(
                "PF-CASH-EARLIER",
                quotation,
                target.getClient(),
                "담당자",
                "earlier@test.com",
                "02-1111-2222",
                "주소",
                actor,
                start.plusDays(7),
                start.plusDays(7),
                start,
                start,
                start,
                "배송지",
                "수신자",
                "010-3333-4444",
                NotifyChannel.EMAIL,
                null,
                em.find(CommonCode.class, 6L),
                true,
                (long) capacity
        );

        earlier.setCreatedAt(target.getCreatedAt().minusDays(1));
        em.persist(earlier);

        Product product = em.find(Product.class, productIds.getFirst());

        em.persist(
                new OrderProduct(
                        earlier,
                        product,
                        product.getProductName(),
                        capacity,
                        1L
                )
        );

        em.flush();

        PaymentDto.ManualDepositResponse response =
                paymentService.recordManualDeposit(request(), actorId);

        assertThat(response.workOrderIds())
                .allSatisfy(id ->
                        assertThat(
                                em.find(WorkOrder.class, id)
                                        .getPlannedStartDate()
                        ).isAfter(start)
                );

        assertThat(
                em.createQuery(
                        "select count(w) from WorkOrder w where w.orderProduct.customerOrder.id = :id",
                        Long.class
                ).setParameter("id", earlier.getId()).getSingleResult()
        ).isZero();

        assertThat(earlier.getStatusCode().getCode()).isEqualTo("WAITING");
    }

    @Test
    @DisplayName("같은 품목·라인의 여러 날을 한 장으로 묶고 날짜별 용량을 넘지 않는다")
    void groupMultipleDaysWithoutDoubleCountingReservedQuantity() {

        em.createQuery(
                        "update CustomerOrder o set o.statusCode = :completed where o.id <> :id"
                ).setParameter("completed", em.find(CommonCode.class, 12L))
                .setParameter("id", orderId)
                .executeUpdate();

        for (Line line :
                em.createQuery("select l from Line l", Line.class).getResultList()) {
            line.changeStatus(LineStatus.BROKEN);
        }

        Line line = new Line("현금 배정 라인", 70);
        em.persist(line);

        PaymentDto.ManualDepositResponse response =
                paymentService.recordManualDeposit(request(), actorId);

        assertThat(response.workOrderIds()).hasSize(2);

        WorkOrder first =
                em.find(WorkOrder.class, response.workOrderIds().getFirst());

        assertThat(first.getQuantity()).isEqualTo(100);
        assertThat(first.getPlannedStartDate())
                .isBefore(first.getPlannedEndDate());

        assertThat(
                productionPlanRepository.findByWorkOrder_Id(first.getId())
        ).hasSize(2);

        for (Long id : response.workOrderIds()) {
            productionPlanRepository.findByWorkOrder_Id(id)
                    .forEach(plan ->
                            assertThat(
                                    productionPlanRepository.sumQuantityByLineAndDate(
                                            line.getId(),
                                            plan.getPlanDate()
                                    )
                            ).isLessThanOrEqualTo(70)
                    );
        }
    }

    @Test
    @DisplayName("입금 처리 권한이 없는 직원은 계약금을 반영할 수 없다")
    void rejectUnauthorizedActor() {

        actor.update(
                actor.getName(),
                actor.getEmail(),
                actor.getPhone(),
                UserRole.INQUIRY_MANAGER,
                UserStatus.ACTIVE
        );

        authenticate();

        assertThatThrownBy(
                () -> paymentService.recordManualDeposit(request(), actorId)
        ).isInstanceOf(AccessDeniedException.class);

        assertThat(
                paymentRepository.findByOrder_IdOrderByIdAsc(orderId)
        ).isEmpty();
    }

    // 수정 2: 이미 입금된 결제의 중복 처리 거부
    @Test
    @DisplayName("수주가 대기여도 이미 입금완료된 계약금은 다시 처리하지 않는다")
    void rejectPreviouslyPaidPayment() {

        Payment payment = paymentRepository.save(
                Payment.builder()
                        .order(em.find(CustomerOrder.class, orderId))
                        .paymentType(PaymentType.DEPOSIT)
                        .amount(5000L)
                        .portonePaymentId("ALREADY-PAID")
                        .build()
        );

        payment.paidManually();

        // 정확한 오류 문구가 아닌 HTTP 409와 데이터 상태를 검증
        assertThatThrownBy(
                () -> paymentService.recordManualDeposit(request(), actorId)
        ).isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> assertThat(
                        exception.getStatusCode().value()
                ).isEqualTo(409)
        );

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);

        assertThat(
                paymentHistoryRepository.findByPayment_Order_Id(orderId)
        ).isEmpty();
    }

    @Test
    @DisplayName("활성 생산 현장 관리자가 없으면 입금을 반영하고 미배정 작업지시를 생성한다")
    void createUnassignedWorkWithoutActiveProductionManager() {

        for (User user :
                em.createQuery("select u from User u", User.class).getResultList()) {

            if (user.getRole() == UserRole.PRODUCTION_MANAGER) {
                user.deactivate();
            }
        }

        PaymentDto.ManualDepositResponse response =
                paymentService.recordManualDeposit(request(), actorId);

        em.flush();
        em.clear();

        assertThat(response.paymentStatus())
                .isEqualTo(OrderPaymentStatus.PARTIALLY_PAID);

        assertThat(response.workOrderIds())
                .isNotEmpty()
                .allSatisfy(id -> {

                    WorkOrder work = em.find(WorkOrder.class, id);

                    assertThat(work.getUser()).isNull();
                    assertThat(work.getCommonCode().getCode())
                            .isEqualTo("PREPARE");

                    assertThat(workOrderService.getDetail(id).assignedId())
                            .isNull();
                });

        User manager = em.find(User.class, managerId);
        manager.activate();

        Long workId = response.workOrderIds().getFirst();

        WorkOrderDto.DetailResponse updated =
                workOrderService.update(
                        workId,
                        new WorkOrderDto.UpdatedRequest(null, managerId),
                        actorId
                );

        assertThat(updated.assignedId()).isEqualTo(managerId);

        assertThat(
                workOrderHistoryRepository.findByWorkOrder_Id(workId)
        ).anySatisfy(history -> {
            assertThat(history.getPreviousUser()).isNull();
            assertThat(history.getNewUser().getId()).isEqualTo(managerId);
        });
    }

    @Test
    @DisplayName("선등록 수주 입금 시 나중 수주의 준비 작업을 이동하고 기존 작업지시 ID를 유지한다")
    void replanLaterRegisteredPrepareWork() {

        Line line = isolatedLine(100);

        WorkOrder later = otherWork(
                "LATER",
                100,
                line,
                em.find(User.class, managerId),
                start,
                "PREPARE"
        );

        Long laterId = later.getId();

        PaymentDto.ManualDepositResponse response =
                paymentService.recordManualDeposit(request(), actorId);

        em.flush();
        em.clear();

        WorkOrder moved = em.find(WorkOrder.class, laterId);

        assertThat(moved).isNotNull();
        assertThat(moved.getPlannedStartDate()).isAfter(start);
        assertThat(moved.getQuantity()).isEqualTo(100);

        assertThat(
                productionPlanRepository.findByWorkOrder_Id(laterId)
                        .stream()
                        .mapToInt(ProductionPlan::getQuantity)
                        .sum()
        ).isEqualTo(100);

        assertThat(response.workOrderIds())
                .allSatisfy(id ->
                        assertThat(
                                em.find(WorkOrder.class, id)
                                        .getPlannedStartDate()
                        ).isBeforeOrEqualTo(moved.getPlannedStartDate())
                );
    }

    @Test
    @DisplayName("나중 수주의 작업이라도 생산을 시작했으면 라인·날짜·생산계획 ID를 유지한다")
    void preserveStartedWorkAndPlans() {

        Line line = isolatedLine(100);

        WorkOrder started = otherWork(
                "STARTED",
                100,
                line,
                em.find(User.class, managerId),
                start,
                "PRODUCE"
        );

        Long planId =
                productionPlanRepository.findByWorkOrder_Id(started.getId())
                        .getFirst()
                        .getId();

        PaymentDto.ManualDepositResponse response =
                paymentService.recordManualDeposit(request(), actorId);

        em.flush();
        em.clear();

        WorkOrder fixed = em.find(WorkOrder.class, started.getId());

        assertThat(fixed.getLine().getId()).isEqualTo(line.getId());
        assertThat(fixed.getPlannedStartDate()).isEqualTo(start);
        assertThat(fixed.getPlannedEndDate()).isEqualTo(start);

        assertThat(
                productionPlanRepository.findByWorkOrder_Id(fixed.getId())
                        .getFirst()
                        .getId()
        ).isEqualTo(planId);

        assertThat(response.workOrderIds())
                .allSatisfy(id ->
                        assertThat(
                                em.find(WorkOrder.class, id)
                                        .getPlannedStartDate()
                        ).isAfter(start)
                );
    }

    @Test
    @DisplayName("생산 후 준비로 돌아온 작업도 생산 이력이 있으면 계획을 고정한다")
    void preservePreviouslyStartedPrepareWork() {

        Line line = isolatedLine(100);

        WorkOrder work = otherWork(
                "PREVIOUSLY-STARTED",
                100,
                line,
                em.find(User.class, managerId),
                start,
                "PRODUCE"
        );

        em.persist(new StatusHistory(
                HistoryEntityType.WORK_ORDER,
                work.getId(),
                "PRODUCE",
                "HALT",
                actor
        ));

        work.changeStatus(em.find(CommonCode.class, 16L));

        Long planId =
                productionPlanRepository.findByWorkOrder_Id(work.getId())
                        .getFirst()
                        .getId();

        paymentService.recordManualDeposit(request(), actorId);

        em.flush();
        em.clear();

        WorkOrder fixed = em.find(WorkOrder.class, work.getId());

        assertThat(fixed.getPlannedStartDate()).isEqualTo(start);
        assertThat(fixed.getLine().getId()).isEqualTo(line.getId());

        assertThat(
                productionPlanRepository.findByWorkOrder_Id(work.getId())
                        .getFirst()
                        .getId()
        ).isEqualTo(planId);
    }

    @Test
    @DisplayName("준비 작업이 한 장으로 통합되어도 이전 배정 이력과 작업번호를 보존한다")
    void preserveAssignmentHistoryWhenPrepareJobsMerge() {

        Line line = isolatedLine(1000);
        User manager = em.find(User.class, managerId);

        WorkOrder first = otherWork(
                "MERGE",
                100,
                line,
                manager,
                start,
                "PREPARE"
        );

        productionPlanRepository.deleteAll(
                productionPlanRepository.findByWorkOrder_Id(first.getId())
        );

        productionPlanRepository.flush();

        first.changeQuantity(50);

        em.persist(new ProductionPlan(first, start, 50));

        Line otherLine = new Line("통합 전 라인", 50);
        em.persist(otherLine);

        WorkOrder extra = new WorkOrder(
                "WO-MERGE-EXTRA",
                first.getOrderProduct(),
                otherLine,
                manager,
                50,
                start,
                start,
                "통합 전 작업"
        );

        extra.changeStatus(em.find(CommonCode.class, 16L));

        em.persist(extra);
        em.persist(new ProductionPlan(extra, start, 50));

        WorkOrderHistory history = new WorkOrderHistory(
                extra,
                line,
                otherLine,
                null,
                manager,
                actor,
                "이전 배정"
        );

        em.persist(history);
        em.flush();

        Long extraId = extra.getId();
        Long historyId = history.getId();

        paymentService.recordManualDeposit(request(), actorId);

        em.flush();
        em.clear();

        assertThat(em.find(WorkOrder.class, extraId)).isNull();

        WorkOrderHistory archived =
                em.find(WorkOrderHistory.class, historyId);

        assertThat(archived).isNotNull();
        assertThat(archived.getWorkOrder()).isNull();
        assertThat(archived.getWorkNo()).isEqualTo("WO-MERGE-EXTRA");
        assertThat(archived.getChangeReason()).isEqualTo("이전 배정");
    }

    @Test
    @DisplayName("다음 날 작업지시 장수가 적은 활성 생산 현장 관리자를 고르고 현재 생산 여부는 제외하지 않는다")
    void assignByNextDayWorkCount() {

        Line line = isolatedLine(1000);

        for (User user :
                em.createQuery("select u from User u", User.class).getResultList()) {

            if (user.getRole() == UserRole.PRODUCTION_MANAGER) {
                user.deactivate();
            }
        }

        User first = em.find(User.class, managerId);

        first.update(
                "가담당자",
                first.getEmail(),
                first.getPhone(),
                UserRole.PRODUCTION_MANAGER,
                UserStatus.ACTIVE
        );

        User second = User.builder()
                .name("나담당자")
                .email("next-day@test.com")
                .phone("010-1234-5678")
                .password("1234")
                .role(UserRole.PRODUCTION_MANAGER)
                .build();

        em.persist(second);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        otherWork(
                "BUSY",
                200,
                line,
                first,
                today.minusDays(2),
                "PRODUCE"
        );

        otherWork(
                "TOMORROW",
                1,
                line,
                second,
                today.plusDays(1),
                "PREPARE"
        );

        PaymentDto.ManualDepositResponse response =
                paymentService.recordManualDeposit(request(), actorId);

        assertThat(response.workOrderIds())
                .allSatisfy(id ->
                        assertThat(
                                em.find(WorkOrder.class, id)
                                        .getUser()
                                        .getId()
                        ).isEqualTo(first.getId())
                );
    }

    @Test
    @DisplayName("다음 날 장수가 같으면 이름 순으로 담당자를 배정한다")
    void assignByNameWhenCountsAreEqual() {

        isolatedLine(1000);

        for (User user :
                em.createQuery("select u from User u", User.class).getResultList()) {

            if (user.getRole() == UserRole.PRODUCTION_MANAGER) {
                user.deactivate();
            }
        }

        User last = em.find(User.class, managerId);

        last.update(
                "하담당자",
                last.getEmail(),
                last.getPhone(),
                UserRole.PRODUCTION_MANAGER,
                UserStatus.ACTIVE
        );

        User first = User.builder()
                .name("가담당자")
                .email("first-name@test.com")
                .phone("010-1234-5678")
                .password("1234")
                .role(UserRole.PRODUCTION_MANAGER)
                .build();

        em.persist(first);

        PaymentDto.ManualDepositResponse response =
                paymentService.recordManualDeposit(request(), actorId);

        assertThat(response.workOrderIds())
                .allSatisfy(id ->
                        assertThat(
                                em.find(WorkOrder.class, id)
                                        .getUser()
                                        .getId()
                        ).isEqualTo(first.getId())
                );
    }

    private Line isolatedLine(int capacity) {

        em.createQuery(
                        "update CustomerOrder o set o.statusCode = :completed where o.id <> :id"
                ).setParameter("completed", em.find(CommonCode.class, 12L))
                .setParameter("id", orderId)
                .executeUpdate();

        for (Line line :
                em.createQuery("select l from Line l", Line.class).getResultList()) {
            line.changeStatus(LineStatus.BROKEN);
        }

        Line line = new Line("등록 순서 테스트 라인", capacity);
        em.persist(line);

        return line;
    }

    private WorkOrder otherWork(
            String suffix,
            int quantity,
            Line line,
            User user,
            LocalDate date,
            String status
    ) {
        CustomerOrder target = em.find(CustomerOrder.class, orderId);

        Quotation quotation = new Quotation(
                "QT-" + suffix,
                target.getClient(),
                "담당자",
                actor,
                date.plusDays(7),
                date.plusDays(7),
                date,
                date,
                date.plusDays(14),
                true,
                QuotationStatus.APPROVED,
                (long) quantity
        );

        em.persist(quotation);

        CustomerOrder other = new CustomerOrder(
                "PF-" + suffix,
                quotation,
                target.getClient(),
                "담당자",
                "other@test.com",
                "02-1111-2222",
                "주소",
                actor,
                date.plusDays(7),
                date.plusDays(7),
                date,
                date,
                date,
                "배송지",
                "수신자",
                "010-3333-4444",
                NotifyChannel.EMAIL,
                null,
                em.find(
                        CommonCode.class,
                        "PRODUCE".equals(status) ? 8L : 7L
                ),
                true,
                (long) quantity
        );

        other.setCreatedAt(target.getCreatedAt().plusMinutes(1));
        em.persist(other);

        OrderProduct item = new OrderProduct(
                other,
                em.find(Product.class, productIds.getFirst()),
                "다른 수주 품목",
                quantity,
                1L
        );

        em.persist(item);

        WorkOrder work = new WorkOrder(
                "WO-" + suffix,
                item,
                line,
                user,
                quantity,
                date,
                date,
                "테스트 작업"
        );

        Long codeId = "PRODUCE".equals(status) ? 17L : 16L;

        work.changeStatus(em.find(CommonCode.class, codeId));
        em.persist(work);
        em.persist(new ProductionPlan(work, date, quantity));
        em.flush();

        return work;
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("작업 배정 실패 시 결제·입금·수주 상태·이력을 모두 롤백한다")
    void rollbackWhenWorkAssignmentFails() {

        java.util.Map<Long, LineStatus> statuses =
                new java.util.HashMap<>();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {

            for (Line line :
                    em.createQuery("select l from Line l", Line.class).getResultList()) {

                statuses.put(line.getId(), line.getStatus());
                line.changeStatus(LineStatus.BROKEN);
            }
        });

        try {
            assertThatThrownBy(
                    () -> paymentService.recordManualDeposit(request(), actorId)
            ).isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("가동 가능한");

        } finally {
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {

                        for (java.util.Map.Entry<Long, LineStatus> entry :
                                statuses.entrySet()) {

                            em.find(Line.class, entry.getKey())
                                    .changeStatus(entry.getValue());
                        }
                    });
        }

        assertThat(
                paymentRepository.findByOrder_IdOrderByIdAsc(orderId)
        ).isEmpty();

        assertThat(
                paymentHistoryRepository.findByPayment_Order_Id(orderId)
        ).isEmpty();

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {

                    CustomerOrder stored =
                            em.find(CustomerOrder.class, orderId);

                    assertThat(stored.getStatusCode().getCode())
                            .isEqualTo("WAITING");

                    assertThat(stored.getPaymentStatus())
                            .isEqualTo(OrderPaymentStatus.UNPAID);
                });

        assertThat(
                statusHistoryRepository.findByEntityAndEntityIdOrderByCreatedAtDescIdDesc(
                        HistoryEntityType.ORDER,
                        orderId
                )
        ).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동시 요청 두 건 중 한 건만 계약금을 반영한다")
    void concurrentRequestsApplyDepositOnce() throws Exception {

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);

        try (java.util.concurrent.ExecutorService executor =
                     Executors.newFixedThreadPool(2)) {

            java.util.concurrent.Callable<Integer> task = () -> {

                authenticate();
                ready.countDown();

                try {
                    if (!startSignal.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 요청 준비 실패");
                    }

                    paymentService.recordManualDeposit(request(), actorId);

                    return 200;

                } catch (ResponseStatusException exception) {
                    return exception.getStatusCode().value();

                } finally {
                    SecurityContextHolder.clearContext();
                }
            };

            java.util.concurrent.Future<Integer> first =
                    executor.submit(task);

            java.util.concurrent.Future<Integer> second =
                    executor.submit(task);

            assertThat(
                    ready.await(10, TimeUnit.SECONDS)
            ).isTrue();

            startSignal.countDown();

            assertThat(
                    List.of(
                            first.get(30, TimeUnit.SECONDS),
                            second.get(30, TimeUnit.SECONDS)
                    )
            ).containsExactlyInAnyOrder(200, 409);
        }

        assertThat(
                paymentRepository.findByOrder_IdOrderByIdAsc(orderId)
        ).hasSize(1);

        assertThat(
                paymentHistoryRepository.findByPayment_Order_Id(orderId)
        ).hasSize(1);
    }

    private PaymentDto.ManualDepositRequest request() {
        return new PaymentDto.ManualDepositRequest(
                orderId,
                5000L,
                null
        );
    }

    private void authenticate() {

        CustomUserDetails principal = new CustomUserDetails(actor);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                )
        );
    }
}
