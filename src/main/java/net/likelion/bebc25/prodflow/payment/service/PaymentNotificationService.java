package net.likelion.bebc25.prodflow.payment.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import jakarta.persistence.EntityManager;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.web.server.ResponseStatusException;

import net.likelion.bebc25.prodflow.client_notification.entity.ClientNotiLog;
import net.likelion.bebc25.prodflow.client_notification.entity.PaymentNotificationRequest;
import net.likelion.bebc25.prodflow.client_notification.enums.ClientNotiStatus;
import net.likelion.bebc25.prodflow.client_notification.enums.PaymentNotificationStatus;
import net.likelion.bebc25.prodflow.client_notification.repository.ClientNotiLogRepository;
import net.likelion.bebc25.prodflow.client_notification.repository.PaymentNotificationRequestRepository;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDetailDto;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.customer_order.service.CustomerOrderService;
import net.likelion.bebc25.prodflow.mail.service.MailService;
import net.likelion.bebc25.prodflow.payment.dto.PaymentNotificationDto;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.PaymentMethod;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import net.likelion.bebc25.prodflow.payment.policy.PaymentAmountPolicy;
import net.likelion.bebc25.prodflow.payment.repository.PaymentRepository;
import net.likelion.bebc25.prodflow.payment.repository.VirtualAccountRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;

@Slf4j
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
@RequiredArgsConstructor
public class PaymentNotificationService {
    private final CustomerOrderRepository orderRepository;
    private final CustomerOrderService orderService;
    private final PaymentRepository paymentRepository;
    private final VirtualAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final PaymentNotificationRequestRepository requestRepository;
    private final ClientNotiLogRepository logRepository;
    private final PaymentService paymentService;
    private final MailService mailService;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentNotificationDto.Response send(Long orderId, PaymentNotificationDto.Request request, Long userId) {
        PaymentNotificationDto.Response previous = transaction().execute(status -> claim(orderId, request, userId));
        if (previous != null) {
            return previous;
        }

        MailContext context;
        try {
            // 기존 자동 발급은 이 트랜잭션의 커밋 후 실행된다.
            Long existingPaymentId = transaction().execute(status -> prepare(orderId, request));
            if (existingPaymentId != null) {
                paymentService.issuePayment(existingPaymentId);
            }
            context = transaction().execute(status -> prepareMail(orderId, request));
        } catch (RuntimeException ex) {
            log.error("계약금 계좌 확인 실패. orderId={}", orderId, ex);
            return finish(orderId, request.requestId(), userId, null,
                    PaymentNotificationStatus.ISSUE_UNCERTAIN,
                    "계좌 또는 최신 수주 상태를 확인하지 못했습니다. 결제 동기화 및 수주 확인 후 다시 시도해주세요.", null);
        }

        try {
            mailService.send(context.recipient(), context.subject(), context.body());
        } catch (RuntimeException ex) {
            log.error("계약금 이메일 전송 실패. orderId={}", orderId, ex);
            return finish(orderId, request.requestId(), userId, context.recipient(),
                    PaymentNotificationStatus.FAILED,
                    "가상계좌는 유지되었으나 이메일 전송에 실패했습니다. 다시 발송하면 기존 계좌를 사용합니다.",
                    ex.getClass().getSimpleName());
        }
        // 결과 저장 장애 시 SENDING을 유지한다. 자동 재전송으로 중복 메일을 만들지 않는다.
        return finish(orderId, request.requestId(), userId, context.recipient(),
                PaymentNotificationStatus.SENT, "가상계좌 및 결제정보 이메일을 전송했습니다.", null);
    }

    private PaymentNotificationDto.Response claim(
            Long orderId, PaymentNotificationDto.Request request, Long userId
    ) {
        validateSender(userId);
        CustomerOrder order = lockedOrder(orderId);
        Optional<PaymentNotificationRequest> previous = requestRepository.findById(request.requestId());
        if (previous.isPresent()) {
            PaymentNotificationRequest existing = previous.get();
            if (!existing.getOrder().getId().equals(orderId) || !existing.getUser().getId().equals(userId)) {
                throw conflict("다른 발송 요청에 사용된 요청 식별자입니다.");
            }
            return PaymentNotificationDto.Response.from(existing);
        }
        Optional<PaymentNotificationRequest> latest =
                requestRepository.findFirstByOrder_IdOrderByUpdatedAtDesc(orderId);
        if (latest.isPresent()) {
            PaymentNotificationStatus state = latest.get().getStatus();
            if (state == PaymentNotificationStatus.PROCESSING || state == PaymentNotificationStatus.SENDING) {
                throw conflict("이미 발송 중이거나 전송 결과 확인이 필요한 요청이 있습니다. 자동 재발송하지 않습니다.");
            }
            if (state == PaymentNotificationStatus.SENT) {
                throw conflict("이미 계약금 안내 이메일을 전송했습니다.");
            }
        }
        validateOrder(order);
        validateSnapshot(orderService.getOrder(orderId), request);
        requestRepository.saveAndFlush(new PaymentNotificationRequest(
                request.requestId(), order, userRepository.getReferenceById(userId)));
        return null;
    }

    private Long prepare(Long orderId, PaymentNotificationDto.Request request) {
        CustomerOrder order = lockedOrder(orderId);
        validateOrder(order);
        validateSnapshot(orderService.getOrder(orderId), request);
        Payment payment = paymentRepository.findByOrder_IdAndPaymentType(orderId, PaymentType.DEPOSIT).orElse(null);
        if (payment != null) {
            validatePayment(payment, order);
            return payment.getId();
        }
        paymentService.prepareAndScheduleIssue(order, PaymentType.DEPOSIT);
        return null;
    }

    private MailContext prepareMail(Long orderId, PaymentNotificationDto.Request request) {
        // afterCommit 발급에서 로딩한 영속성 컨텍스트의 이전 상태를 사용하지 않는다.
        entityManager.clear();
        CustomerOrder order = lockedOrder(orderId);
        validateOrder(order);
        CustomerOrderDetailDto.Response detail = orderService.getOrder(orderId);
        validateSnapshot(detail, request);
        Payment payment = paymentRepository.findByOrder_IdAndPaymentType(orderId, PaymentType.DEPOSIT)
                .orElseThrow(() -> conflict("계약금 결제 건이 없습니다."));
        validatePayment(payment, order);
        VirtualAccount account = accountRepository.findFirstByPayment_IdOrderByIdDesc(payment.getId())
                .orElseThrow(() -> conflict("발급된 가상계좌를 확인할 수 없습니다."));
        if (payment.getStatus() != PaymentStatus.WAITING || !account.isActive() || account.isExpired()) {
            throw conflict("입금 가능한 계좌가 아닙니다. 결제 상태 및 재발급 필요 여부를 확인해주세요.");
        }
        StringBuilder body = new StringBuilder();
        body.append(detail.client().companyName()).append(" 담당자님, 계약금 결제정보를 안내드립니다.\n\n")
                .append("수주번호: ").append(detail.orderNo()).append("\n")
                .append("단가 부가세 ").append(detail.vatIncluded() ? "포함" : "별도").append("\n\n");
        for (CustomerOrderDetailDto.ItemResponse item : detail.items()) {
            body.append(item.productName()).append(" | 수량 ").append(item.quantity())
                    .append("개 | 단가 ").append(won(item.unitPrice()))
                    .append(" | 공급가액 ").append(won(item.supplyAmount())).append("\n");
        }
        body.append("\n공급가액 합계: ").append(won(detail.supplyAmount()))
                .append("\n부가세: ").append(won(detail.vatAmount()))
                .append("\n수주 총액: ").append(won(detail.totalAmount()))
                .append("\n계약금 청구액: ").append(won(payment.getAmount()))
                .append("\n은행: ").append(account.getBank().getLabel())
                .append("\n계좌번호: ").append(account.getAccountNumber())
                .append("\n예금주: ").append(Objects.toString(account.getAccountName(), "은행 안내 참조"))
                .append("\n계약금 마감일: ").append(detail.depositDueDate())
                .append("\n계좌 만료일시: ").append(account.getExpiresAt())
                .append("\n\n계좌 만료일과 관계없이 계약금 마감일까지 입금해주세요.");
        requestRepository.findById(request.requestId()).orElseThrow()
                .complete(PaymentNotificationStatus.SENDING, "이메일 전송 중입니다. 결과 확인 전 다시 발송하지 마세요.");
        return new MailContext(detail.client().email(),
                "[PRODFLOW] 계약금 결제 안내 (" + detail.orderNo() + ")", body.toString());
    }

    private PaymentNotificationDto.Response finish(
            Long orderId, String requestId, Long userId, String recipient,
            PaymentNotificationStatus state, String message, String failure
    ) {
        return transaction().execute(status -> {
            lockedOrder(orderId);
            PaymentNotificationRequest request = requestRepository.findById(requestId).orElseThrow();
            request.complete(state, message);
            if (recipient != null) {
                logRepository.save(ClientNotiLog.deposit(orderRepository.getReferenceById(orderId),
                        userRepository.getReferenceById(userId), recipient,
                        state == PaymentNotificationStatus.SENT ? ClientNotiStatus.SENT : ClientNotiStatus.FAILED,
                        failure));
            }
            return PaymentNotificationDto.Response.from(request);
        });
    }

    private void validateSnapshot(CustomerOrderDetailDto.Response detail, PaymentNotificationDto.Request request) {
        List<PaymentNotificationDto.Item> items = detail.items().stream().map(item -> new PaymentNotificationDto.Item(
                item.id(), item.productName(), item.quantity(), item.unitPrice(), item.supplyAmount())).toList();
        if (!Objects.equals(detail.client().email(), request.recipient())
                || !Objects.equals(detail.totalAmount(), request.totalAmount())
                || !Objects.equals(detail.supplyAmount(), request.supplyAmount())
                || !Objects.equals(detail.vatAmount(), request.vatAmount())
                || !Objects.equals(detail.vatIncluded(), request.vatIncluded())
                || !Objects.equals(detail.depositDueDate(), request.depositDueDate())
                || !Objects.equals(PaymentAmountPolicy.calculate(detail.totalAmount(), PaymentType.DEPOSIT),
                        request.depositAmount()) || !items.equals(request.items())) {
            throw conflict("확인한 수주 정보가 변경되었습니다. 모달을 닫고 다시 확인해주세요.");
        }
        if (detail.client().email().length() > 100) {
            throw conflict("발송 이력에 저장할 수 있는 이메일 길이(100자)를 초과했습니다.");
        }
    }

    private void validateSender(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AccessDeniedException("계정을 찾을 수 없습니다."));
        if (user.getStatus() != UserStatus.ACTIVE || user.isMustChangePassword()
                || !Set.of(UserRole.SUPER_ADMIN, UserRole.ORDER_MANAGER).contains(user.getRole())) {
            throw new AccessDeniedException("사용 가능한 관리자 계정으로 비밀번호 변경 후 발송해주세요.");
        }
    }

    private void validateOrder(CustomerOrder order) {
        if (!"WAITING".equals(order.getStatusCode().getCode())
                || order.getDepositDueDate().isBefore(LocalDate.now(ZoneId.of("Asia/Seoul")))) {
            throw conflict("계약금 마감일 이내의 대기 수주만 발송할 수 있습니다.");
        }
    }

    private void validatePayment(Payment payment, CustomerOrder order) {
        if (payment.getPaymentMethod() != PaymentMethod.VA || payment.getStatus() == PaymentStatus.PAID
                || payment.getAmount() != PaymentAmountPolicy.calculate(order.getTotalAmount(), PaymentType.DEPOSIT)) {
            throw conflict("계약금 결제 상태 또는 청구액을 확인해주세요.");
        }
    }

    private CustomerOrder lockedOrder(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("수주를 찾을 수 없습니다."));
    }

    private TransactionTemplate transaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private String won(long amount) {
        return String.format(Locale.KOREA, "%,d원", amount);
    }

    private record MailContext(String recipient, String subject, String body) {
    }
}
