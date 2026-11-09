package net.likelion.bebc25.prodflow.payment.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.line.repository.LineRepository;
import net.likelion.bebc25.prodflow.payment.client.PortOneClient;
import net.likelion.bebc25.prodflow.payment.config.PortOneProperties;
import net.likelion.bebc25.prodflow.payment.dto.PaymentDto;
import net.likelion.bebc25.prodflow.payment.dto.PortOneDto;
import net.likelion.bebc25.prodflow.payment.dto.VirtualAccountDto;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.Bank;
import net.likelion.bebc25.prodflow.payment.enums.PaymentMethod;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import net.likelion.bebc25.prodflow.payment.policy.PaymentAmountPolicy;
import net.likelion.bebc25.prodflow.payment.repository.PaymentHistoryRepository;
import net.likelion.bebc25.prodflow.payment.repository.PaymentRepository;
import net.likelion.bebc25.prodflow.payment.repository.VirtualAccountRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto;
import net.likelion.bebc25.prodflow.work_order.service.WorkOrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Validated
public class PaymentService {

    private final CustomerOrderRepository customerOrderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final UserRepository userRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final WorkOrderService workOrderService;
    private final LineRepository lineRepository;
    private final PaymentStore paymentStore;
    private final PortOneClient portOneClient;
    private final PortOneProperties properties;

    @Value("${payment.virtual-account-bank:SHINHAN}")
    private String defaultBank;

    // 최초 계약금/잔금 발급과 재발급에 공통 적용되는 만료 기간(일).
    @Value("${payment.virtual-account-due-days:14}")
    private int virtualAccountDueDays;

    // 현금 수령 기록부터 작업 배정까지 같은 트랜잭션으로 처리한다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    public PaymentDto.ManualDepositResponse recordManualDeposit(
            @NotNull @Valid PaymentDto.ManualDepositRequest request,
            Long userId
    ) {
        User actor = userRepository.findById(userId)
                .orElseThrow(() ->
                        new AuthenticationCredentialsNotFoundException(
                                "처리 직원을 찾을 수 없습니다."
                        )
                );

        if (actor.getStatus() != UserStatus.ACTIVE
                || actor.isMustChangePassword()
                || (actor.getRole() != UserRole.SUPER_ADMIN
                && actor.getRole() != UserRole.ORDER_MANAGER)) {

            throw new AccessDeniedException(
                    "사용 중이며 비밀번호 변경을 완료한 관리자만 입금을 처리할 수 있습니다."
            );
        }

        OffsetDateTime now = OffsetDateTime.now(
                ZoneId.of("Asia/Seoul")
        );

        OffsetDateTime paidAt =
                request.paidAt() == null ? now : request.paidAt();

        if (paidAt.isAfter(now)) {
            throw new IllegalArgumentException(
                    "입금일시는 미래일 수 없습니다."
            );
        }

        // 수주 등록과 같은 잠금으로 생산 자리 계산을 직렬화한다.
        // 라인 변경과는 라인 -> 수주 순으로 잠근다.
        commonCodeRepository.findOrderWaitingForUpdate(CodeStatus.ACTIVE)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "수주 대기 상태 코드가 등록되어 있지 않습니다."
                        )
                );

        lineRepository.findAllForUpdate();

        CustomerOrder order = customerOrderRepository
                .findByIdForUpdate(request.orderId())
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "수주를 찾을 수 없습니다."
                        )
                );

        if (!"WAITING".equals(order.getStatusCode().getCode())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "대기 수주만 계약금을 처리할 수 있습니다."
            );
        }

        long depositAmount = PaymentAmountPolicy.calculate(
                order.getTotalAmount(),
                PaymentType.DEPOSIT
        );

        if (request.amount() != depositAmount) {
            throw new IllegalArgumentException(
                    "입금 금액은 계약금 예정액 "
                            + depositAmount
                            + "원과 같아야 합니다."
            );
        }

        // 최초 발급 claimIssue()와 동일한 결제 행 락을 잡아
        // PortOne POST와 수동 입금의 동시 실행을 방지한다.
        Payment payment = paymentRepository
                .findByOrder_IdAndPaymentType(order.getId(), PaymentType.DEPOSIT)
                .map(existing -> paymentRepository.getForUpdate(existing.getId())
                        .orElseThrow(() -> new NoSuchElementException("계약금 결제 정보를 찾을 수 없습니다.")))
                .orElse(null);

        if (payment != null) {
            if (payment.getStatus() != PaymentStatus.READY) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "PortOne 발급 요청이 이미 시작된 결제입니다. PG 말소/정합성 확인 후 수동 입금하세요.");
            }
            if (paymentHistoryRepository.existsByPayment_Id(payment.getId())) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "이미 처리된 계약금 또는 입금 내역이 있습니다."
                );
            }

            if (payment.getAmount() != depositAmount) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "기존 계약금 청구액과 수주 계약금 예정액이 다릅니다."
                );
            }
        } else {
            // 외부 결제는 생성하지 않는다.
            // 내부 결제 식별값만 부여한다.
            payment = paymentRepository.save(
                    Payment.builder()
                            .order(order)
                            .paymentType(PaymentType.DEPOSIT)
                            .amount(depositAmount)
                            .portonePaymentId(
                                    "MANUAL-" + UUID.randomUUID()
                            )
                            .build()
            );
        }

        // 이미 발급된 활성 가상계좌가 있다면 현금 입금과 중복될 위험이 있으므로
        // PG 말소를 확인하기 전에는 수동입금을 허용하지 않는다.
        if (payment != null) {
            virtualAccountRepository.findFirstByPayment_IdOrderByIdDesc(payment.getId())
                    .filter(account -> account.isActive() && !account.isExpired())
                    .ifPresent(account -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "활성 가상계좌가 있습니다. PG 말소 확인 후 수동 입금하세요.");
                    });
        }

        CommonCode confirmed = commonCodeRepository
                .findByCodeGroup_GroupCodeAndCodeAndStatus(
                        "ORDER_STATUS",
                        "CONFIRMED",
                        CodeStatus.ACTIVE
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "생산대기 상태 코드가 등록되어 있지 않습니다."
                        )
                );

        payment.paidManually();

        // 머지 충돌로 중복된 history 선언을 하나로 정리
        PaymentHistory history = paymentHistoryRepository.save(
                PaymentHistory.builder()
                        .payment(payment)
                        .paidAt(paidAt)
                        .build()
        );

        order.markPartiallyPaid();
        order.changeProgress(confirmed);

        StatusHistory statusHistory = new StatusHistory(
                HistoryEntityType.ORDER,
                order.getId(),
                "WAITING",
                "CONFIRMED",
                actor
        );

        statusHistory.writeNote(
                "현금 계약금 "
                        + depositAmount
                        + "원 수동 입금 확인 (입금일시: "
                        + paidAt
                        + ")"
        );

        statusHistoryRepository.save(statusHistory);

        List<Long> workOrderIds = workOrderService
                .createForConfirmedOrder(order, actor)
                .stream()
                .map(WorkOrderDto.Response::id)
                .toList();

        return PaymentDto.ManualDepositResponse.from(
                payment,
                history,
                order,
                workOrderIds
        );
    }

    // 결제·계좌 발급이나 상태 변경 없이 수주의 저장된 결제 정보를 조회한다.
    public PaymentDto.OrderResponse getByOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException(
                    "수주 ID를 입력해 주세요."
            );
        }

        CustomerOrder order = customerOrderRepository
                .findById(orderId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "수주를 찾을 수 없습니다."
                        )
                );

        long depositAmount = PaymentAmountPolicy.calculate(
                order.getTotalAmount(),
                PaymentType.DEPOSIT
        );

        List<Payment> payments =
                paymentRepository.findByOrder_IdOrderByIdAsc(orderId);

        if (payments.isEmpty()) {
            return PaymentDto.OrderResponse.from(
                    order,
                    depositAmount,
                    0,
                    List.of()
            );
        }

        Map<Long, PaymentHistory> histories = new HashMap<>();

        for (PaymentHistory history :
                paymentHistoryRepository.findByPayment_Order_Id(orderId)) {

            histories.put(
                    history.getPayment().getId(),
                    history
            );
        }

        Map<Long, VirtualAccount> accounts = new HashMap<>();

        for (VirtualAccount account :
                virtualAccountRepository.findLatestByOrderId(orderId)) {

            accounts.put(
                    account.getPayment().getId(),
                    account
            );
        }

        long paidAmount = 0;

        List<PaymentDto.OrderPaymentResponse> responses =
                new ArrayList<>();

        for (Payment payment : payments) {
            if (payment.getStatus() == PaymentStatus.PAID) {
                paidAmount += payment.getAmount();
            }

            responses.add(
                    PaymentDto.OrderPaymentResponse.from(
                            payment,
                            accounts.get(payment.getId()),
                            histories.get(payment.getId())
                    )
            );
        }

        return PaymentDto.OrderResponse.from(
                order,
                depositAmount,
                paidAmount,
                responses
        );
    }

    /**
     * 계좌 재발급 이전 이력도 최신순으로 조회한다. 외부 API 호출은 하지 않는다.
     */
    public List<VirtualAccountDto.Response> getVirtualAccountHistory(Long paymentId) {
        if (!paymentRepository.existsById(paymentId)) {
            throw new NoSuchElementException("결제를 찾을 수 없습니다.");
        }
        return virtualAccountRepository.findByPayment_IdOrderByIdDesc(paymentId)
                .stream().map(VirtualAccountDto.Response::from).toList();
    }

    /**
     * 수주 등록/출고 등록 트랜잭션 안에서 호출한다.
     * Payment만 먼저 저장하며, 외부 POST는 커밋된 뒤 시도한다.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void prepareAndScheduleIssue(CustomerOrder order, PaymentType type) {
        if (type == PaymentType.BALANCE) {
            Payment deposit = paymentRepository.findByOrder_IdAndPaymentType(order.getId(), PaymentType.DEPOSIT)
                    .orElseThrow(() -> new IllegalStateException("계약금 결제 정보가 없습니다."));
            if (deposit.getStatus() != PaymentStatus.PAID) {
                throw new IllegalStateException("계약금 입금 완료 후 잔금 결제를 생성할 수 있습니다.");
            }
        }
        Payment existing = paymentRepository.findByOrder_IdAndPaymentType(order.getId(), type).orElse(null);
        if (existing != null) {
            return; // 수주·결제유형별 고유 제약 + 재호출 방어
        }
        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .order(order)
                .paymentType(type)
                .amount(PaymentAmountPolicy.calculate(order.getTotalAmount(), type))
                // PortOne 결제 ID는 최대 40자 PG 제약에 맞춘 34자 고유 값이다.
                .portonePaymentId("PF" + UUID.randomUUID().toString().replace("-", ""))
                .build());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("결제 생성은 트랜잭션 안에서 처리해야 합니다.");
        }
        Long id = payment.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    issuePayment(id);
                } catch (Exception ex) {
                    // 수주/출고는 이미 커밋된 상태. 실패를 감추지 않고 상태와 로그로 추적한다.
                    log.error("PortOne 자동 가상계좌 발급 실패. paymentId={}", id, ex);
                }
            }
        });
    }

    /**
     * 가상계좌 재발급: 기존 계좌의 말소가 PG에서 확인된 뒤에만 새 ID로 발급한다.
     * 네트워크 불확실 시 새 계좌를 발급하지 않으며, 재요청은 GET 조회만 수행한다.
     * 외부 API 호출 중 DB 트랜잭션/락을 유지하지 않는다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    public PaymentDto.IssueResponse reissueVirtualAccount(Long paymentId) {
        // 클라이언트는 만료일을 전달하지 않는다. 최초 요청에서만 .env 일수를 적용한다.
        // 진행 중인 요청은 Payment.reissueDueAt을 재사용하므로 재요청해도 기한이 바뀌지 않는다.
        if (virtualAccountDueDays < 1) {
            throw new IllegalStateException("PAYMENT_VA_DUE_DAYS는 1 이상의 정수여야 합니다.");
        }
        properties.requireConfigured();
        PaymentStore.ReissueClaim claim = paymentStore.beginReissue(paymentId, virtualAccountDueDays);
        String oldId = claim.oldPortonePaymentId();

        if (!claim.alreadyClosed()) {
            PortOneDto.PaymentSnapshot snapshot;
            try {
                snapshot = portOneClient.getPayment(oldId);
                Payment stored = paymentRepository.findById(paymentId)
                        .orElseThrow(() -> new NoSuchElementException("결제 정보를 찾을 수 없습니다."));
                validate(stored, snapshot);
            } catch (Exception ex) {
                // 발급 결과나 말소 결과가 불확실하면 절대 새 계좌를 발급하지 않는다.
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "이전 PortOne 결제를 확인하지 못했습니다. 재동기화 후 다시 확인하세요.", ex);
            }
            if ("PAID".equals(snapshot.status()) || "PARTIAL_CANCELLED".equals(snapshot.status())) {
                if ("PAID".equals(snapshot.status())) {
                    paymentStore.applyWebhook(paymentId, snapshot, "ReissueCheck");
                }
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "이미 입금되었거나 부분 취소된 결제는 재발급할 수 없습니다.");
            }
            if ("CANCELLED".equals(snapshot.status()) || "FAILED".equals(snapshot.status())) {
                // 기존 PG 결제 자체가 종료된 경우 말소 요청을 다시 보내지 않는다.
                paymentStore.confirmReissueClosed(paymentId, oldId, snapshot.updatedAt());
            } else if ("VIRTUAL_ACCOUNT_ISSUED".equals(snapshot.status()) && claim.newlyClaimed()) {
                // 이 호출만 단 1회 말소 POST를 수행한다. 타임아웃 시 반복 POST 금지.
                PortOneDto.CloseVirtualAccountResponse closed;
                try {
                    closed = portOneClient.closeVirtualAccount(oldId);
                } catch (Exception ex) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "말소 요청 결과가 불확실합니다. 새로운 계좌는 발급하지 않았습니다. PortOne 상태를 확인하세요.", ex);
                }
                if (closed == null || closed.closedAt() == null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "PortOne 말소 완료 시각을 확인하지 못했습니다. 재발급을 중단합니다.");
                }
                paymentStore.confirmReissueClosed(paymentId, oldId, closed.closedAt());
            } else {
                // 이전 말소 요청이 불확실한 상태라면 GET만 수행하고, 확인되지 않으면 중단한다.
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "이전 가상계좌 말소를 확인하지 못했습니다. 중복 말소/발급을 방지하기 위해 중단합니다.");
            }
        }

        paymentStore.finishReissue(paymentId, oldId);
        issuePayment(paymentId);
        return paymentStore.getIssueResponse(paymentId);
    }

    /**
     * 외부 POST는 별도 DB 트랜잭션에서 발급 의도를 커밋한 뒤 실행한다.
     */
    public void issuePayment(Long paymentId) {
        boolean firstAttempt = paymentStore.claimIssue(paymentId);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("결제 정보를 찾을 수 없습니다."));
        if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.WAITING) {
            return;
        }
        if (!firstAttempt) {
            // REQUESTED/ISSUE_UNCERTAIN 상태는 재POST하지 않고 조회만 수행한다.
            synchronizePayment(paymentId);
            return;
        }
        try {
            properties.requireConfigured();
            portOneClient.issueInstantPayment(buildIssueRequest(payment));
            synchronizePayment(paymentId);
        } catch (Exception ex) {
            // POST 응답을 잃은 경우 이미 발급되었을 수 있다. 같은 ID로 재POST 금지.
            try {
                synchronizePayment(paymentId);
                return;
            } catch (Exception syncEx) {
                paymentStore.markIssueUncertain(paymentId);
                ex.addSuppressed(syncEx);
                throw new IllegalStateException("PortOne 발급 결과를 확인하지 못했습니다. 결제 동기화가 필요합니다.", ex);
            }
        }
    }

    private PortOneDto.IssueRequest buildIssueRequest(Payment payment) {
        CustomerOrder order = payment.getOrder();
        Bank bank = Bank.valueOf(defaultBank.trim().toUpperCase(Locale.ROOT));
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Seoul"));
        if (virtualAccountDueDays < 1) {
            throw new IllegalStateException("PAYMENT_VA_DUE_DAYS는 1 이상의 정수여야 합니다.");
        }
        // 최초 발급: 모두 발급 시점 + 환경설정 일수
        // 재발급: 말소 작업을 시작할 때 DB에 확정해 둔 만료일(재시도 중 변경 금지)
        OffsetDateTime dueDate = payment.getReissueDueAt() != null
                ? payment.getReissueDueAt()
                : now.plusDays(virtualAccountDueDays).withNano(0);
        if (!dueDate.isAfter(now.plusMinutes(5))) {
            throw new IllegalStateException("새 가상계좌 입금기한이 임박했거나 지났습니다. 재발급 상태를 확인하세요.");
        }
        return new PortOneDto.IssueRequest(
                payment.getPortonePaymentId(),
                order.getOrderNo() + (payment.getPaymentType() == PaymentType.DEPOSIT ? " 계약금" : " 잔금"),
                payment.getAmount(),
                order.getClient().getCompany(),
                order.getEmail(), order.getPhone(),
                order.getAddress(), "", bank.name(), dueDate,
                "issue-" + payment.getPortonePaymentId()
        );
    }

    /**
     * 운영자 재동기화 또는 웹훅에서 사용한다. 절대 새로운 발급 POST를 보내지 않는다.
     */
    public PaymentDto.IssueResponse synchronizePayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("결제를 찾을 수 없습니다."));
        if (payment.getPaymentMethod() != PaymentMethod.VA) {
            throw new IllegalStateException("수동 입금 건은 PortOne과 동기화할 수 없습니다.");
        }
        PortOneDto.PaymentSnapshot snapshot = portOneClient.getPayment(payment.getPortonePaymentId());
        validate(payment, snapshot);
        paymentStore.applyWebhook(paymentId, snapshot, "Sync");
        return paymentStore.getIssueResponse(paymentId);
    }

    /**
     * 웹훅 본문은 알림으로만 취급하고, 서버 API 재조회 결과만 DB에 반영한다.
     */
    public void handleWebhook(PortOneDto.Webhook webhook) {
        if (webhook == null || webhook.data() == null || webhook.data().paymentId() == null) {
            return;
        }
        if (webhook.type() == null || !Set.of(
                "Transaction.Ready", "Transaction.Paid", "Transaction.VirtualAccountIssued",
                "Transaction.PartialCancelled", "Transaction.Cancelled", "Transaction.Failed",
                "Transaction.PayPending", "Transaction.CancelPending").contains(webhook.type())) {
            return;
        }
        if (!Objects.equals(properties.storeId(), webhook.data().storeId())) {
            log.warn("PortOne 웹훅 상점 불일치");
            return;
        }
        Payment payment = paymentRepository.findByPortonePaymentId(webhook.data().paymentId()).orElse(null);
        if (payment == null || payment.getPaymentMethod() != PaymentMethod.VA) {
            log.info("등록되지 않았거나 수동 입금된 결제의 웹훅은 무시합니다.");
            return;
        }
        PortOneDto.PaymentSnapshot snapshot = portOneClient.getPayment(webhook.data().paymentId());
        validate(payment, snapshot);
        if ("Transaction.Paid".equals(webhook.type())
                && !"PAID".equals(snapshot.status()) && !"PARTIAL_CANCELLED".equals(snapshot.status())) {
            throw new IllegalStateException("입금 완료 알림 이후 PortOne 상태 반영을 기다리는 중입니다.");
        }
        paymentStore.applyWebhook(payment.getId(), snapshot, webhook.type());
    }

    private void validate(Payment payment, PortOneDto.PaymentSnapshot snapshot) {
        if (snapshot == null || !payment.getPortonePaymentId().equals(snapshot.id())
                || !properties.storeId().equals(snapshot.storeId())
                || !"KRW".equals(snapshot.currency()) || snapshot.amount() == null
                || !payment.getAmount().equals(snapshot.amount().total())
                || snapshot.channel() == null || !properties.channelKey().equals(snapshot.channel().key())
                || !"TOSSPAYMENTS".equals(snapshot.channel().pgProvider())
                || snapshot.updatedAt() == null) {
            throw new IllegalStateException("PortOne 결제 ID/상점/금액/채널 검증 실패");
        }
        PaymentStatus mapped = PaymentStore.mapPortOneStatus(snapshot.status());
        if (mapped == PaymentStatus.WAITING || mapped == PaymentStatus.PAID) {
            if (snapshot.method() == null
                    || !"PaymentMethodVirtualAccount".equals(snapshot.method().type())
                    || snapshot.method().accountNumber() == null
                    || snapshot.method().accountNumber().isBlank()
                    || snapshot.method().bank() == null) {
                throw new IllegalStateException("PortOne 가상계좌 정보를 확인할 수 없습니다.");
            }
            Bank.valueOf(snapshot.method().bank()); // 프로젝트가 지원하는 은행인지 확인
            // PortOne expiredAt은 optional이다. 신규 발급에는 필수지만 입금 시점에는
            // 기존 발급 계좌의 만료일이 DB에 있으면 그대로 재사용한다.
            if (snapshot.method().expiredAt() == null
                    && virtualAccountRepository.findFirstByPayment_IdOrderByIdDesc(payment.getId()).isEmpty()) {
                throw new IllegalStateException("최초 가상계좌 정보에 만료일이 없습니다.");
            }
            if (mapped == PaymentStatus.PAID && (snapshot.paidAt() == null
                    || snapshot.transactionId() == null || snapshot.transactionId().isBlank())) {
                throw new IllegalStateException("PortOne 입금 시각/거래 ID가 누락되었습니다.");
            }
        }
    }
}
