package net.likelion.bebc25.prodflow.payment.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.line.repository.LineRepository;
import net.likelion.bebc25.prodflow.payment.dto.PaymentDto;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

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

    // 현금 수령 기록부터 작업 배정까지 같은 트랜잭션으로 처리한다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    public PaymentDto.ManualDepositResponse recordManualDeposit(
            @NotNull @Valid PaymentDto.ManualDepositRequest request, Long userId) {
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("처리 직원을 찾을 수 없습니다."));
        if (actor.getStatus() != UserStatus.ACTIVE || actor.isMustChangePassword()
                || (actor.getRole() != UserRole.SUPER_ADMIN && actor.getRole() != UserRole.ORDER_MANAGER)) {
            throw new AccessDeniedException("사용 중이며 비밀번호 변경을 완료한 관리자만 입금을 처리할 수 있습니다.");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        LocalDateTime receivedAt = request.receivedAt() == null ? now : request.receivedAt();
        if (receivedAt.isAfter(now)) {
            throw new IllegalArgumentException("입금일시는 미래일 수 없습니다.");
        }

        // 수주 등록과 같은 잠금으로 생산 자리 계산을 직렬화한다. 라인 변경과는 라인→수주 순으로 잠근다.
        commonCodeRepository.findOrderWaitingForUpdate(CodeStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("수주 대기 상태 코드가 등록되어 있지 않습니다."));
        lineRepository.findAllForUpdate();
        CustomerOrder order = customerOrderRepository.findByIdForUpdate(request.orderId())
                .orElseThrow(() -> new NoSuchElementException("수주를 찾을 수 없습니다."));
        if (!"WAITING".equals(order.getStatusCode().getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "대기 수주만 계약금을 처리할 수 있습니다.");
        }
        long depositAmount = PaymentAmountPolicy.calculate(order.getTotalAmount(), PaymentType.DEPOSIT);
        if (request.amount() != depositAmount) {
            throw new IllegalArgumentException("입금 금액은 계약금 예정액 " + depositAmount + "원과 같아야 합니다.");
        }
        Payment payment = paymentRepository.findByOrder_IdAndPaymentType(order.getId(), PaymentType.DEPOSIT)
                .orElse(null);
        if (payment != null) {
            if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.CANCELLED
                    || paymentHistoryRepository.existsByPayment_Id(payment.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 계약금 또는 입금 내역이 있습니다.");
            }
            if (payment.getAmount() != depositAmount) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "기존 계약금 청구액과 수주 계약금 예정액이 다릅니다.");
            }
        } else {
            // 외부 결제는 생성하지 않는다. NOT NULL인 내부 결제 식별값만 부여한다.
            payment = paymentRepository.save(
                    Payment.builder()
                            .order(order)
                            .paymentType(PaymentType.DEPOSIT)
                            .amount(depositAmount)
                            .portonePaymentId("MANUAL-" + UUID.randomUUID())
                            .build()
            );
        }
        CommonCode confirmed = commonCodeRepository.findByCodeGroup_GroupCodeAndCodeAndStatus(
                        "ORDER_STATUS", "CONFIRMED", CodeStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("생산대기 상태 코드가 등록되어 있지 않습니다."));
        payment.paidManually();
        PaymentHistory history = paymentHistoryRepository.save(PaymentHistory.builder()
                .payment(payment).receivedAt(receivedAt).build());
        order.markPartiallyPaid();
        order.changeProgress(confirmed);
        StatusHistory statusHistory = new StatusHistory(HistoryEntityType.ORDER, order.getId(), "WAITING", "CONFIRMED", actor);
        statusHistory.writeNote("현금 계약금 " + depositAmount + "원 수동 입금 확인 (입금일시: " + receivedAt + ")");
        statusHistoryRepository.save(statusHistory);
        List<Long> workOrderIds = workOrderService.createForConfirmedOrder(order, actor).stream()
                .map(WorkOrderDto.Response::id).toList();
        return PaymentDto.ManualDepositResponse.from(payment, history, order, workOrderIds);
    }

    // 결제·계좌 발급이나 상태 변경 없이 수주의 저장된 결제 정보를 조회한다.
    public PaymentDto.OrderResponse getByOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("수주 ID를 입력해 주세요.");
        }
        CustomerOrder order = customerOrderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("수주를 찾을 수 없습니다."));

        long depositAmount = PaymentAmountPolicy.calculate(order.getTotalAmount(), PaymentType.DEPOSIT);
        List<Payment> payments = paymentRepository.findByOrder_IdOrderByIdAsc(orderId);

        if (payments.isEmpty()) {
            return PaymentDto.OrderResponse.from(order, depositAmount, 0, List.of());
        }

        Map<Long, PaymentHistory> histories = new HashMap<>();
        for (PaymentHistory history : paymentHistoryRepository.findByPayment_Order_Id(orderId)) {
            histories.put(history.getPayment().getId(), history);
        }

        Map<Long, VirtualAccount> accounts = new HashMap<>();
        for (VirtualAccount account : virtualAccountRepository.findLatestByOrderId(orderId)) {
            accounts.put(account.getPayment().getId(), account);
        }

        long paidAmount = 0;
        List<PaymentDto.OrderPaymentResponse> responses = new ArrayList<>();
        for (Payment payment : payments) {
            if (payment.getStatus() == PaymentStatus.PAID) {
                paidAmount += payment.getAmount();
            }
            responses.add(PaymentDto.OrderPaymentResponse.from(payment,
                    accounts.get(payment.getId()), histories.get(payment.getId())));
        }

        return PaymentDto.OrderResponse.from(order, depositAmount, paidAmount, responses);
    }
}
