package net.likelion.bebc25.prodflow.customer_order.service;

import java.util.NoSuchElementException;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

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
import net.likelion.bebc25.prodflow.payment.repository.PaymentHistoryRepository;
import net.likelion.bebc25.prodflow.payment.repository.PaymentRepository;
import net.likelion.bebc25.prodflow.payment.repository.VirtualAccountRepository;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import net.likelion.bebc25.prodflow.work_order.repository.WorkOrderRepository;

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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CustomerOrderDetailDto.Response cancel(
            Long orderId, @NotNull @Valid CustomerOrderDto.CancelRequest request, Long userId
    ) {
        User actor = validateActor(userId);
        Long quotationId = orderRepository.findQuotationIdById(orderId)
                .orElseThrow(() -> new NoSuchElementException("수주를 찾을 수 없습니다."));
        // 등록·수동 입금·입금 웹훅과 동일하게 공통 대기 코드부터 잠근다.
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
            return orderService.getOrder(orderId);
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

        cancelPaymentsBeforeOrderCancellation(orderId);
        quotation.cancel();
        progressService.changeProgress(order, "CANCELLED", actor, request.reason().trim());
        return orderService.getOrder(orderId);
    }

    // 가상계좌 담당자의 말소 연계 지점. 현재는 외부 발급 전 결제만 취소한다.
    // PG 호출을 추가할 때는 DB 트랜잭션 밖에서 말소를 확인한 뒤 최신 상태를 재검증해야 한다.
    private void cancelPaymentsBeforeOrderCancellation(Long orderId) {
        for (Payment row : paymentRepository.findByOrder_IdOrderByIdAsc(orderId)) {
            Payment payment = paymentRepository.getForUpdate(row.getId())
                    .orElseThrow(() -> new NoSuchElementException("결제 정보를 찾을 수 없습니다."));
            // 목록 조회 뒤 다른 트랜잭션이 발급을 시작했을 수 있으므로 잠금 후 최신 상태를 다시 읽는다.
            entityManager.refresh(payment);
            if (paymentHistoryRepository.existsByPayment_Id(payment.getId())) {
                throw conflict("입금 내역이 있는 결제는 취소할 수 없습니다.");
            }
            if (accountRepository.findFirstByPayment_IdOrderByIdDesc(payment.getId()).isPresent()) {
                throw conflict("발급된 가상계좌가 있습니다. 가상계좌 말소 처리 연계 후 취소할 수 있습니다.");
            }
            if (payment.getStatus() != PaymentStatus.READY && payment.getStatus() != PaymentStatus.CANCELLED) {
                throw conflict("외부 발급이 시작됐거나 결과 확인이 필요한 결제입니다. 결제 상태를 확인해주세요.");
            }
            if (payment.getStatus() == PaymentStatus.READY) {
                payment.cancel();
            }
        }
    }

    private User validateActor(Long userId) {
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("처리 직원 계정을 찾을 수 없습니다."));
        if (actor.getStatus() != UserStatus.ACTIVE || actor.isMustChangePassword()
                || (actor.getRole() != UserRole.SUPER_ADMIN && actor.getRole() != UserRole.ORDER_MANAGER)) {
            throw new AccessDeniedException("사용 중이며 비밀번호 변경을 완료한 관리자만 수주를 취소할 수 있습니다.");
        }
        return actor;
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
