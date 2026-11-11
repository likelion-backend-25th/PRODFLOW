package net.likelion.bebc25.prodflow.payment.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.line.repository.LineRepository;
import net.likelion.bebc25.prodflow.payment.dto.PaymentDto;
import net.likelion.bebc25.prodflow.payment.dto.PortOneDto;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.*;
import net.likelion.bebc25.prodflow.payment.repository.PaymentHistoryRepository;
import net.likelion.bebc25.prodflow.payment.repository.PaymentRepository;
import net.likelion.bebc25.prodflow.payment.repository.VirtualAccountRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import net.likelion.bebc25.prodflow.work_order.service.WorkOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentStore {
    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final LineRepository lineRepository;
    private final WorkOrderService workOrderService;
    private final StatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;

    //  만료일이 지난 계좌의 DB 상태를 주기적으로 갱신
    //  실제 입금 완료/말소 시의 USED/CANCELLED 상태는 덮어쓰지 않음
    @Scheduled(fixedDelayString = "${payment.virtual-account-expire-check-ms:60000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireDueVirtualAccounts() {
        virtualAccountRepository.expireActiveAccounts(
                OffsetDateTime.now(ZoneId.of("Asia/Seoul")),
                LocalDateTime.now(),
                VirtualAccountStatus.ACTIVE,
                VirtualAccountStatus.EXPIRED
        );
    }

    // PortOne POST 직전에 REQUESTED 상태를 별도 트랜잭션으로 확정한다.
    // 같은 결제에 중복 POST를 보내지 않기 위해 락 + 상태 체크를 수행한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimIssue(Long id) {
        // 수주 취소와 발급 claim이 경합하지 않도록 수주 -> 결제 순서로 락을 잡는다.
        // Payment 엔티티를 먼저 로딩하면 락 대기 후에도 오래된 상태를 사용할 수 있다.
        Long orderId = paymentRepository.findOrderIdByPaymentId(id)
                .orElseThrow(() -> new NoSuchElementException("결제를 찾을 수 없습니다."));
        CustomerOrder order = customerOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("수주를 찾을 수 없습니다."));
        Payment payment = lockedPayment(id);
        if ("CANCELLED".equals(order.getStatusCode().getCode())
                || payment.getStatus() != PaymentStatus.READY
                || paymentRepository.findByOrder_IdOrderByIdAsc(order.getId()).stream()
                .anyMatch(p -> p.getStatus() == PaymentStatus.ORDER_CANCEL_CLOSING)) {
            return false;
        }
        payment.requestIssue();
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markIssueUncertain(Long id) {
        lockedPayment(id).issueUncertain();
    }


    //수주 취소 의도를 먼저 커밋한다. PG 호출은 이 트랜잭션 밖에서 수행한다.
    //READY이며 계좌가 없는 결제는 아직 PG 발급을 요청하지 않았으므로 즉시 취소한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OrderCancelTarget> beginUnpaidOrderCancellation(Long orderId) {
        CustomerOrder order = customerOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수주를 찾을 수 없습니다."));
        if ("CANCELLED".equals(order.getStatusCode().getCode())) {
            return List.of(); // 재호출 멱등 처리
        }
        if (!"WAITING".equals(order.getStatusCode().getCode())
                || order.getPaymentStatus() != OrderPaymentStatus.UNPAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "미입금 대기 수주만 자동 취소할 수 있습니다. 입금 후 취소는 환불 절차가 필요합니다.");
        }

        List<OrderCancelTarget> targets = new ArrayList<>();
        for (Payment candidate : paymentRepository.findByOrder_IdOrderByIdAsc(orderId)) {
            Payment payment = lockedPayment(candidate.getId());
            if (paymentHistoryRepository.existsByPayment_Id(payment.getId())
                    || payment.getStatus() == PaymentStatus.PAID
                    || payment.getStatus() == PaymentStatus.PARTIAL_CANCELLED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "입금 이력이 있는 수주는 자동 취소할 수 없습니다. 환불 절차를 확인하세요.");
            }
            List<VirtualAccount> accounts = virtualAccountRepository
                    .findByPayment_IdOrderByIdDesc(payment.getId());
            VirtualAccount latest = accounts.isEmpty() ? null : accounts.getFirst();
            // 재발급 이력의 예전 계좌가 아직 말소되지 않았다면 자동 취소하지 않는다.
            for (int i = 1; i < accounts.size(); i++) {
                if (accounts.get(i).getStatus() != VirtualAccountStatus.CANCELLED) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "이전 가상계좌에 미확인 상태가 있어 수동 확인이 필요합니다.");
                }
            }
            if (payment.getPaymentMethod() != PaymentMethod.VA) {
                if (payment.getStatus() != PaymentStatus.READY
                        && payment.getStatus() != PaymentStatus.CANCELLED) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "수동 입금 결제의 상태를 확인한 뒤 취소하세요.");
                }
                payment.cancel();
                continue;
            }
            if (payment.getStatus() == PaymentStatus.READY && latest == null) {
                payment.cancel();
                continue;
            }
            if (payment.getStatus() == PaymentStatus.CANCELLED
                    && (latest == null || latest.getStatus() == VirtualAccountStatus.CANCELLED)) {
                continue;
            }
            if (payment.getStatus() == PaymentStatus.REISSUE_CLOSING
                    || payment.getStatus() == PaymentStatus.REQUESTED
                    || payment.getStatus() == PaymentStatus.ISSUE_UNCERTAIN
                    || payment.getStatus() == PaymentStatus.PENDING
                    || payment.getStatus() == PaymentStatus.PAY_PENDING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "가상계좌 발급·재발급 결과가 확정되지 않았습니다. 먼저 결제 상태를 동기화하세요.");
            }
            if (payment.getStatus() != PaymentStatus.WAITING
                    && payment.getStatus() != PaymentStatus.FAILED
                    && payment.getStatus() != PaymentStatus.CANCELLED
                    && payment.getStatus() != PaymentStatus.ORDER_CANCEL_CLOSING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "현재 결제 상태에서는 자동 취소할 수 없습니다.");
            }
            if (payment.getPortonePaymentId() == null || payment.getPortonePaymentId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "PortOne 결제 ID가 없습니다. 수동 확인이 필요합니다.");
            }
            boolean newlyClaimed = payment.getStatus() != PaymentStatus.ORDER_CANCEL_CLOSING;
            if (newlyClaimed) {
                payment.updateStatus(PaymentStatus.ORDER_CANCEL_CLOSING);
            }
            targets.add(new OrderCancelTarget(payment.getId(), payment.getPortonePaymentId(), newlyClaimed));
        }
        return List.copyOf(targets);
    }

    public record OrderCancelTarget(Long paymentId, String portonePaymentId, boolean newlyClaimed) {
    }

    //PortOne 종료 상태 또는 실제 계좌 말소 응답을 확인한 결제만 취소로 확정
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirmOrderPaymentClosed(Long orderId, Long paymentId, String expectedPortoneId) {
        CustomerOrder order = customerOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수주를 찾을 수 없습니다."));
        if (!"WAITING".equals(order.getStatusCode().getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "수주 상태가 변경되어 취소를 중단했습니다.");
        }
        Payment payment = lockedPayment(paymentId);
        if (!payment.getOrder().getId().equals(orderId)
                || payment.getStatus() != PaymentStatus.ORDER_CANCEL_CLOSING
                || !Objects.equals(payment.getPortonePaymentId(), expectedPortoneId)
                || paymentHistoryRepository.existsByPayment_Id(paymentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "입금 발생 또는 결제 상태 변경으로 취소할 수 없습니다.");
        }
        VirtualAccount latest = virtualAccountRepository.findRecentByPayment_IdForUpdate(paymentId).orElse(null);
        if (latest != null) {
            if (latest.getStatus() == VirtualAccountStatus.USED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용한 가상계좌는 말소할 수 없습니다.");
            }
            latest.recordOriginalPortonePaymentId(expectedPortoneId);
            latest.cancel();
        }
        payment.cancel();
    }

    //모든 결제의 PG 말소를 확정한 다음에만 수주 상태와 이력을 변경
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishUnpaidOrderCancellation(Long orderId, Long actorId) {
        CustomerOrder order = customerOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수주를 찾을 수 없습니다."));
        if ("CANCELLED".equals(order.getStatusCode().getCode())) {
            return;
        }
        if (!"WAITING".equals(order.getStatusCode().getCode())
                || order.getPaymentStatus() != OrderPaymentStatus.UNPAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "수주 상태가 변경되어 자동 취소할 수 없습니다.");
        }
        for (Payment candidate : paymentRepository.findByOrder_IdOrderByIdAsc(orderId)) {
            Payment payment = lockedPayment(candidate.getId());
            if (paymentHistoryRepository.existsByPayment_Id(payment.getId())
                    || payment.getStatus() != PaymentStatus.CANCELLED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "아직 입금 대기 또는 말소 확인 중인 결제건이 있습니다.");
            }
            VirtualAccount latest = virtualAccountRepository.findFirstByPayment_IdOrderByIdDesc(payment.getId()).orElse(null);
            if (latest != null && latest.getStatus() != VirtualAccountStatus.CANCELLED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "가상계좌 말소가 확정되지 않았습니다.");
            }
        }
        CommonCode cancelled = commonCodeRepository.findByCodeGroup_GroupCodeAndCodeAndStatus(
                        "ORDER_STATUS", "CANCELLED", CodeStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("CANCELLED 수주 상태 코드가 없습니다."));
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("취소 처리자를 찾을 수 없습니다."));
        String before = order.getStatusCode().getCode();
        order.changeProgress(cancelled);
        StatusHistory history = new StatusHistory(HistoryEntityType.ORDER, order.getId(),
                before, "CANCELLED", actor);
        history.writeNote("미입금 수주 취소: PortOne 가상계좌 말소 확인 완료");
        statusHistoryRepository.save(history);
    }

    //최초 요청만 말소 API를 호출할 수 있도록 먼저 DB에 재발급 의도를 커밋
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReissueClaim beginReissue(Long paymentId, int dueDays) {
        Payment payment = lockedPayment(paymentId);
        if (payment.getPaymentMethod() != PaymentMethod.VA
                || paymentHistoryRepository.existsByPayment_Id(paymentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "입금 내역이 있는 결제는 재발급할 수 없습니다.");
        }
        if ("CANCELLED".equals(payment.getOrder().getStatusCode().getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "취소된 수주는 가상계좌를 재발급할 수 없습니다.");
        }
        VirtualAccount latest = virtualAccountRepository.findRecentByPayment_IdForUpdate(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "기존 가상계좌가 없습니다. 최초 발급 API를 사용하세요."));
        if (latest.getStatus() == VirtualAccountStatus.USED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용한 가상계좌는 재발급할 수 없습니다.");
        }
        if (payment.getStatus() == PaymentStatus.REISSUE_CLOSING) {
            // .env가 바뀌거나 재호출 시각이 달라져도 최초 확정한 기한을 변경하지 않는다.
            if (payment.getReissueDueAt() == null) {
                throw new IllegalStateException("진행 중인 재발급의 입금기한이 없습니다. 관리자 확인이 필요합니다.");
            }
            return new ReissueClaim(payment.getPortonePaymentId(), false,
                    payment.getReissueClosedAt() != null);
        }
        if (payment.getStatus() != PaymentStatus.WAITING
                && payment.getStatus() != PaymentStatus.CANCELLED
                && payment.getStatus() != PaymentStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "입금 완료/발급 불확실 상태의 결제는 재발급할 수 없습니다. 상태를 먼저 동기화하세요.");
        }
        if (dueDays < 1) {
            throw new IllegalArgumentException("가상계좌 입금기한 설정 일수는 1 이상이어야 합니다.");
        }
        // 결제 행 잠금 안에서 단 한 번 계산해 저장한다.
        OffsetDateTime dueAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
                .plusDays(dueDays).withNano(0);
        payment.beginReissue(dueAt);
        return new ReissueClaim(payment.getPortonePaymentId(), true, false);
    }

    public record ReissueClaim(String oldPortonePaymentId, boolean newlyClaimed, boolean alreadyClosed) {
    }

    // PG 말소 응답/종결 상태를 확인한 후에만 호출
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirmReissueClosed(Long id, String expectedOldId, OffsetDateTime closedAt) {
        Payment payment = lockedPayment(id);
        if (payment.getStatus() != PaymentStatus.REISSUE_CLOSING
                || !Objects.equals(payment.getPortonePaymentId(), expectedOldId)
                || paymentHistoryRepository.existsByPayment_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "재발급 중 입금이 확인되었거나 결제 식별자가 변경되었습니다.");
        }
        VirtualAccount oldAccount = virtualAccountRepository.findRecentByPayment_IdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("말소할 기존 가상계좌를 찾을 수 없습니다."));
        if (oldAccount.getStatus() == VirtualAccountStatus.USED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 입금 처리된 가상계좌는 재발급할 수 없습니다.");
        }
        oldAccount.recordOriginalPortonePaymentId(expectedOldId);
        oldAccount.cancel();
        payment.confirmReissueClosed(closedAt);
    }

    //PG 말소 확인이 기록된 경우에만 기존 계좌를 보존하고 새 ID로 전환
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishReissue(Long id, String expectedOldId) {
        Payment payment = lockedPayment(id);
        if (payment.getStatus() != PaymentStatus.REISSUE_CLOSING
                || !Objects.equals(payment.getPortonePaymentId(), expectedOldId)
                || payment.getReissueClosedAt() == null
                || paymentHistoryRepository.existsByPayment_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "기존 계좌 말소/미입금 검증이 끝나지 않았습니다.");
        }
        VirtualAccount account = virtualAccountRepository.findRecentByPayment_IdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("기존 계좌가 없습니다."));
        if (account.getStatus() == VirtualAccountStatus.USED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용된 계좌는 재발급할 수 없습니다.");
        }
        account.recordOriginalPortonePaymentId(expectedOldId);
        account.cancel();
        String newId;
        do {
            newId = "PF" + UUID.randomUUID().toString().replace("-", "");
        } while (paymentRepository.existsByPortonePaymentId(newId));
        payment.switchToNewPortonePaymentId(newId);
    }

    // PG의 상태를 우리 내부 통계/수주 흐름에서 사용하는 상태로 정규화한다.
    public static PaymentStatus mapPortOneStatus(String status) {
        if (status == null) {
            throw new IllegalArgumentException("PortOne 결제 상태가 비어 있습니다.");
        }
        return switch (status) {
            case "READY" -> PaymentStatus.READY;
            case "PENDING", "PAY_PENDING" -> PaymentStatus.PENDING;
            case "VIRTUAL_ACCOUNT_ISSUED" -> PaymentStatus.WAITING;
            case "PAID" -> PaymentStatus.PAID;
            case "FAILED" -> PaymentStatus.FAILED;
            case "CANCELLED" -> PaymentStatus.CANCELLED;
            case "PARTIAL_CANCELLED" -> PaymentStatus.PARTIAL_CANCELLED;
            default -> throw new IllegalArgumentException("지원하지 않는 PortOne 상태: " + status);
        };
    }


    // 트랜잭션 경계는 PG API 호출 바깥에 둔다. 결제/계좌/입금내역/수주/작업지시를 함께 커밋
    // 같은 PAID 웹훅을 재수신해도 새 이력과 작업지시를 생성하지 않음
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment applyWebhook(Long id, PortOneDto.PaymentSnapshot snapshot, String type) {
        PaymentStatus remoteStatus = mapPortOneStatus(snapshot.status());
        // 수동 입금 처리와 동일한 잠금 순서: 주문대기코드 -> 생산라인 -> 수주 -> 결제.
        // PAID 최초 도착에만 잠가서 생산배정을 직렬화한다.
        Payment existing = paymentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("결제를 찾을 수 없습니다."));
        if (remoteStatus == PaymentStatus.PAID && existing.getPaymentType() == PaymentType.DEPOSIT
                && existing.getStatus() != PaymentStatus.PAID) {
            commonCodeRepository.findOrderWaitingForUpdate(CodeStatus.ACTIVE)
                    .orElseThrow(() -> new IllegalStateException("수주 대기 상태 코드가 없습니다."));
            lineRepository.findAllForUpdate();
            customerOrderRepository.findByIdForUpdate(existing.getOrder().getId())
                    .orElseThrow(() -> new NoSuchElementException("수주를 찾을 수 없습니다."));
        }
        Payment payment = lockedPayment(id);
        if (payment.getPaymentMethod() != PaymentMethod.VA) {
            return payment; // 현금 입금과 충돌하는 외부 웹훅 무시
        }
        // 취소 대기/완료 상태를 늦게 도착한 가상계좌 발급 웹훅이 되돌리지 못하게 한다.
        if ("CANCELLED".equals(payment.getOrder().getStatusCode().getCode())) {
            if (remoteStatus == PaymentStatus.PAID) {
                throw new IllegalStateException("취소된 수주에서 PortOne 입금이 확인되었습니다. 수동 정산 확인이 필요합니다.");
            }
            return payment;
        }
        if (payment.getStatus() == PaymentStatus.ORDER_CANCEL_CLOSING
                && remoteStatus != PaymentStatus.PAID) {
            return payment;
        }
        if (payment.getStatus() == PaymentStatus.CANCELLED && remoteStatus != PaymentStatus.PAID) {
            // 완료된 말소 상태를 늦게 도착한 발급 웹훅이 WAITING으로 되돌리지 못하게 한다.
            return payment;
        }
        PaymentHistory history = paymentHistoryRepository.getByPayment_IdForUpdate(id).orElse(null);
        if (history != null && history.getWebhookRemoteUpdatedAt() != null
                && snapshot.updatedAt().isBefore(history.getWebhookRemoteUpdatedAt())) {
            return payment;
        }
        // 지연/역순 웹훅이 입금 대기를 준비 상태로, 입금 완료를 미완료로 되돌리지 않게 한다.
        if ((payment.getStatus() == PaymentStatus.PAID && remoteStatus != PaymentStatus.PAID)
                || (payment.getStatus() == PaymentStatus.WAITING
                && (remoteStatus == PaymentStatus.READY || remoteStatus == PaymentStatus.PENDING))) {
            return payment;
        }
        if (remoteStatus == PaymentStatus.PARTIAL_CANCELLED) {
            // 환불/부분 취소는 입금 완료와 다른 업무. 자동으로 수주 상태를 되돌리지 않는다.
            throw new IllegalStateException("부분 취소는 관리자 정산 검토가 필요합니다.");
        }
        if (payment.getStatus() == PaymentStatus.REISSUE_CLOSING
                && remoteStatus != PaymentStatus.PAID) {
            // 이전 PG가 말소되는 중 전달된 웹훅은 재발급 상태를 되돌릴 수 없다.
            // PAID 웹훅만 계속 정상 처리하여 입금 충돌 시 재발급을 중단한다.
            return payment;
        }
        VirtualAccount account = virtualAccountRepository.findRecentByPayment_IdForUpdate(id).orElse(null);
        if (account != null && (account.getStatus() == VirtualAccountStatus.CANCELLED
                || (account.getPortonePaymentId() != null
                && !account.getPortonePaymentId().equals(payment.getPortonePaymentId())))) {
            // 재발급 완료 후 신규 PortOne ID의 계좌는 별도 행으로 보존한다.
            account = null;
        }
        if (remoteStatus == PaymentStatus.WAITING || remoteStatus == PaymentStatus.PAID) {
            PortOneDto.IssueResult details = PortOneDto.IssueResult.from(snapshot);
            Bank bank = Bank.valueOf(details.bankCode());
            if (details.accountNumber() == null || (account == null && details.expiresAt() == null)) {
                throw new IllegalStateException("PortOne 최초 가상계좌 번호 또는 만료 시각이 없습니다.");
            }
            if (account == null) {
                account = virtualAccountRepository.save(VirtualAccount.builder()
                        .payment(payment)
                        .portonePaymentId(payment.getPortonePaymentId())
                        .bank(bank)
                        .accountNumber(details.accountNumber())
                        .accountName(details.accountName())
                        .expiresAt(details.expiresAt())
                        .build());
            } else {
                account.recordOriginalPortonePaymentId(payment.getPortonePaymentId());
                if (account.getBank() != bank) {
                    throw new IllegalStateException("발급 계좌의 은행이 기존 기록과 다릅니다.");
                }
                account.updateAccountNumber(details.accountNumber());
                if (details.expiresAt() != null) {
                    account.updateExpiresAt(details.expiresAt());
                }
            }
        }
        if (remoteStatus != PaymentStatus.PAID) {
            // 발급 의도 저장 후 PortOne READY 응답이 온 경우 REQUESTED 상태를 유지한다.
            if (remoteStatus != PaymentStatus.READY || payment.getStatus() != PaymentStatus.REQUESTED) {
                payment.updateStatus(remoteStatus);
            }
            return payment;
        }
        if (account == null || snapshot.paidAt() == null || snapshot.transactionId() == null) {
            throw new IllegalStateException("입금 처리에 필요한 PortOne 데이터가 없습니다.");
        }
        if (history == null) {
            history = paymentHistoryRepository.save(PaymentHistory.builder()
                    .payment(payment)
                    .virtualAccount(account)
                    .transactionId(snapshot.transactionId())
                    .paidAt(snapshot.paidAt())
                    .build());
        } else if (!snapshot.transactionId().equals(history.getTransactionId())) {
            throw new IllegalStateException("이미 기록된 입금 거래 ID와 다릅니다.");
        }
        history.updatePaidAt(snapshot.paidAt());
        history.updateWebhookRemoteUpdatedAt(snapshot.updatedAt());
        history.recordWebhookType(type);
        boolean firstPaid = payment.getStatus() != PaymentStatus.PAID;
        account.used();
        payment.paidByVirtualAccount();
        if (firstPaid) {
            updateOrderAfterPaid(payment);
        }
        return payment;
    }

    private void updateOrderAfterPaid(Payment payment) {
        CustomerOrder order = payment.getOrder();
        if (payment.getPaymentType() == PaymentType.BALANCE) {
            Payment deposit = paymentRepository.findByOrder_IdAndPaymentType(order.getId(), PaymentType.DEPOSIT)
                    .orElseThrow(() -> new IllegalStateException("계약금 결제 정보가 없습니다."));
            if (deposit.getStatus() != PaymentStatus.PAID) {
                throw new IllegalStateException("계약금 입금 전에 잔금을 완료 처리할 수 없습니다.");
            }
            order.markPaid();
            return;
        }
        if (!"WAITING".equals(order.getStatusCode().getCode())
                || order.getPaymentStatus() != OrderPaymentStatus.UNPAID) {
            throw new IllegalStateException("수주 상태가 계약금 입금 후 확정 가능한 상태가 아닙니다.");
        }
        CommonCode confirmed = commonCodeRepository.findByCodeGroup_GroupCodeAndCodeAndStatus(
                        "ORDER_STATUS", "CONFIRMED", CodeStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("수주 확정 상태 코드가 없습니다."));
        order.markPartiallyPaid();
        order.changeProgress(confirmed);
        StatusHistory change = new StatusHistory(HistoryEntityType.ORDER, order.getId(),
                "WAITING", "CONFIRMED", order.getUser());
        change.writeNote("PortOne 계약금 입금 자동 확인, 결제 ID: " + payment.getId());
        statusHistoryRepository.save(change);
        workOrderService.createForConfirmedOrder(order, order.getUser());
    }

    // 발급 결과 조회는 별도 트랜잭션으로 최신 DB 상태를 읽는다.
    // 호출자가 오래된 영속성 컨텍스트를 가진 경우에도 올바른 상태를 반환한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public PaymentDto.IssueResponse getIssueResponse(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("결제를 찾을 수 없습니다."));
        VirtualAccount account = virtualAccountRepository.findFirstByPayment_IdOrderByIdDesc(paymentId)
                .orElseThrow(() -> new IllegalStateException("발급 정보를 확인하지 못했습니다. 재동기화하세요."));
        if (account.getStatus() == VirtualAccountStatus.CANCELLED
                || (account.getPortonePaymentId() != null
                && !account.getPortonePaymentId().equals(payment.getPortonePaymentId()))) {
            throw new IllegalStateException("새 결제에 대응하는 가상계좌가 아직 확인되지 않았습니다. 재동기화하세요.");
        }
        return PaymentDto.IssueResponse.from(payment, account);
    }

    private Payment lockedPayment(Long id) {
        return paymentRepository.getForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("결제를 찾을 수 없습니다. id=" + id));
    }
}
