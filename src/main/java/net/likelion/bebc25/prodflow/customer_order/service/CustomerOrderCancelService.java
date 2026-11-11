package net.likelion.bebc25.prodflow.customer_order.service;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.client_notification.enums.PaymentNotificationStatus;
import net.likelion.bebc25.prodflow.client_notification.repository.PaymentNotificationRequestRepository;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDetailDto;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.VirtualAccountStatus;
import net.likelion.bebc25.prodflow.payment.repository.PaymentHistoryRepository;
import net.likelion.bebc25.prodflow.payment.repository.PaymentRepository;
import net.likelion.bebc25.prodflow.payment.repository.VirtualAccountRepository;
import net.likelion.bebc25.prodflow.payment.service.PaymentService;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import net.likelion.bebc25.prodflow.work_order.repository.WorkOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
@RequiredArgsConstructor
@Validated
public class CustomerOrderCancelService {
    private final CustomerOrderRepository orderRepository;
    private final QuotationRepository quotationRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final VirtualAccountRepository accountRepository;
    private final PaymentNotificationRequestRepository notificationRepository;
    private final WorkOrderRepository workOrderRepository;
    private final CustomerOrderProgressService progressService;
    private final CustomerOrderService orderService;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;
    private final PaymentService paymentService;

    // 외부 API 호출 중에는 DB 트랜잭션을 보유하지 않는다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CustomerOrderDetailDto.Response cancel(
            Long orderId, @NotNull @Valid CustomerOrderDto.CancelRequest request, Long userId
    ) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        // 1차 검증: 부적합 수주에 대해 PortOne 말소를 먼저 수행하지 않는다.
        boolean alreadyCancelled = Boolean.TRUE.equals(tx.execute(status ->
                lockAndValidate(orderId, userId).alreadyCancelled()
        ));
        if (alreadyCancelled) {
            return orderService.getOrder(orderId);
        }

        // 이전 PaymentService의 PG 말소 코드를 재사용하되, 수주/견적/이력은 여기서 아직 바꾸지 않는다.
        paymentService.closeUnpaidOrderPayments(orderId, userId);

        // 별도 TX에서 최신 상태를 다시 검사하고 원본 견적·수주·이력을 함께 확정한다.
        return tx.execute(status -> {
            CancellationContext context = lockAndValidate(orderId, userId);
            if (context.alreadyCancelled()) {
                return orderService.getOrder(orderId);
            }

            requireAllPaymentsClosed(orderId);
            context.quotation().cancel();
            progressService.changeProgress(
                    context.order(), "CANCELLED", context.actor(), request.reason().trim()
            );
            return orderService.getOrder(orderId);
        });
    }

    // 반드시 TransactionTemplate이 만든 DB 트랜잭션 안에서만 호출한다.
    // 팀원 코드의 권한·견적·수주·알림·작업지시 검증을 그대로 보존했다.
    private CancellationContext lockAndValidate(Long orderId, Long userId) {
        User actor = validateActor(userId);
        Long quotationId = orderRepository.findQuotationIdById(orderId)
                .orElseThrow(() -> new NoSuchElementException("수주를 찾을 수 없습니다."));

        commonCodeRepository.findOrderWaitingForUpdate(CodeStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("수주 대기 상태 코드가 없습니다."));
        Quotation quotation = quotationRepository.findByIdForUpdate(quotationId)
                .orElseThrow(() -> new NoSuchElementException("원본 견적을 찾을 수 없습니다."));
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("수주를 찾을 수 없습니다."));

        if ("CANCELLED".equals(order.getStatusCode().getCode())) {
            if (quotation.getStatus() != QuotationStatus.CANCELLED) {
                throw conflict("수주와 원본 견적의 취소 상태가 일치하지 않습니다.");
            }
            return new CancellationContext(actor, quotation, order, true);
        }
        if (!"WAITING".equals(order.getStatusCode().getCode())
                || order.getPaymentStatus() != OrderPaymentStatus.UNPAID) {
            throw conflict("입금 전 대기 수주만 취소할 수 있습니다.");
        }
        if (quotation.getStatus() != QuotationStatus.APPROVED) {
            throw conflict("원본 견적이 승인 상태가 아닙니다.");
        }
        if (!paymentHistoryRepository.findByPayment_Order_Id(orderId).isEmpty()) {
            throw conflict("입금 내역이 있는 수주는 취소할 수 없습니다.");
        }
        if (workOrderRepository.existsByOrderProduct_CustomerOrder_Id(orderId)) {
            throw conflict("작업지시가 생성된 수주는 취소할 수 없습니다.");
        }
        notificationRepository.findFirstByOrder_IdOrderByUpdatedAtDesc(orderId).ifPresent(notification -> {
            if (notification.getStatus() == PaymentNotificationStatus.PROCESSING
                    || notification.getStatus() == PaymentNotificationStatus.SENDING) {
                throw conflict("결제정보 발송 처리 중입니다. 처리 결과를 확인한 뒤 취소해주세요.");
            }
        });
        return new CancellationContext(actor, quotation, order, false);
    }

    private void requireAllPaymentsClosed(Long orderId) {
        for (Payment row : paymentRepository.findByOrder_IdOrderByIdAsc(orderId)) {
            Payment payment = paymentRepository.getForUpdate(row.getId())
                    .orElseThrow(() -> new NoSuchElementException("결제 정보를 찾을 수 없습니다."));
            entityManager.refresh(payment);

            if (paymentHistoryRepository.existsByPayment_Id(payment.getId())) {
                throw conflict("입금 내역이 있는 결제는 취소할 수 없습니다.");
            }
            if (payment.getStatus() != PaymentStatus.CANCELLED) {
                throw conflict("말소되지 않았거나 결과 확인 중인 결제건이 있습니다.");
            }
            boolean hasUnclosedAccount = accountRepository.findByPayment_IdOrderByIdDesc(payment.getId())
                    .stream().anyMatch(account -> account.getStatus() != VirtualAccountStatus.CANCELLED);
            if (hasUnclosedAccount) {
                throw conflict("가상계좌 말소가 완료되지 않았습니다.");
            }
        }
    }

    private record CancellationContext(User actor, Quotation quotation, CustomerOrder order,
                                       boolean alreadyCancelled) {
    }

    private User validateActor(Long userId) {
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("처리 직원 계정을 찾을 수 없습니다."));
        if (actor.getStatus() != UserStatus.ACTIVE || actor.isMustChangePassword()
                || (actor.getRole() != UserRole.SUPER_ADMIN && actor.getRole() != UserRole.ORDER_MANAGER)) {
            throw new AccessDeniedException(
                    "사용 중이며 비밀번호 변경을 완료한 관리자만 수주를 취소할 수 있습니다."
            );
        }
        return actor;
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
