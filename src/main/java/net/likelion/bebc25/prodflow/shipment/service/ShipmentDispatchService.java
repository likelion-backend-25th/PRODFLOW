package net.likelion.bebc25.prodflow.shipment.service;

import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.likelion.bebc25.prodflow.client_notification.entity.ClientNotiLog;
import net.likelion.bebc25.prodflow.client_notification.entity.PaymentNotificationRequest;
import net.likelion.bebc25.prodflow.client_notification.enums.ClientNotiStatus;
import net.likelion.bebc25.prodflow.client_notification.enums.PaymentNotificationKind;
import net.likelion.bebc25.prodflow.client_notification.enums.PaymentNotificationStatus;
import net.likelion.bebc25.prodflow.client_notification.repository.ClientNotiLogRepository;
import net.likelion.bebc25.prodflow.client_notification.repository.PaymentNotificationRequestRepository;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.mail.service.MailService;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.PaymentMethod;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import net.likelion.bebc25.prodflow.payment.policy.PaymentAmountPolicy;
import net.likelion.bebc25.prodflow.payment.repository.PaymentRepository;
import net.likelion.bebc25.prodflow.payment.repository.VirtualAccountRepository;
import net.likelion.bebc25.prodflow.payment.service.PaymentService;
import net.likelion.bebc25.prodflow.shipment.dto.ShipmentDto;
import net.likelion.bebc25.prodflow.shipment.entity.Carrier;
import net.likelion.bebc25.prodflow.shipment.entity.Shipment;
import net.likelion.bebc25.prodflow.shipment.repository.CarrierRepository;
import net.likelion.bebc25.prodflow.shipment.repository.ShipmentRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
public class ShipmentDispatchService {
    private final CustomerOrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final CarrierRepository carrierRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final VirtualAccountRepository accountRepository;
    private final PaymentNotificationRequestRepository requestRepository;
    private final ClientNotiLogRepository logRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final StatusHistoryRepository historyRepository;
    private final PaymentService paymentService;
    private final MailService mailService;
    private final PlatformTransactionManager transactionManager;

    // 외부 호출은 DB 트랜잭션 밖에서 수행한다. 실패한 단계의 앞선 결과는 커밋해 재사용한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ShipmentDto.Response dispatch(ShipmentDto.CreateRequest input, Long userId) {
        Claim claim = transaction().execute(status -> claim(input, userId));
        if (claim.completed()) {
            return response(input.orderId());
        }
        if (!claim.mailSent()) {
            MailContext mail;
            try {
                Long paymentId = transaction().execute(status -> preparePayment(input.orderId()));
                // WAITING은 재사용, REQUESTED/ISSUE_UNCERTAIN은 기존 기능이 PG 조회만 수행한다.
                paymentService.issuePayment(paymentId);
                mail = transaction().execute(status -> prepareMail(input.orderId(), claim.requestId()));
            } catch (RuntimeException ex) {
                log.error("출고 잔금 계좌 확인 실패. orderId={}", input.orderId(), ex);
                String message = ex instanceof ResponseStatusException failure && failure.getReason() != null
                        ? failure.getReason() + " 출고대기로 유지했습니다."
                        : "잔금 계좌 준비에 실패했습니다. 출고대기에 남아 있으며 다시 출고하기를 눌러주세요.";
                recordFailure(input.orderId(), claim.requestId(), userId, null,
                        PaymentNotificationStatus.ISSUE_UNCERTAIN,
                        message, ex);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
            }
            try {
                mailService.send(mail.recipient(), mail.subject(), mail.body());
            } catch (RuntimeException ex) {
                boolean retryable = definitelyNotSent(ex);
                String message = retryable
                        ? "이메일 전송에 실패했습니다. 출고대기에 남아 있으며 다시 출고하면 기존 유효 계좌를 재사용합니다."
                        : "이메일 접수 여부를 확인할 수 없습니다. 출고대기로 유지하며 중복 발송 방지를 위해 메일 서버 확인이 필요합니다.";
                recordFailure(input.orderId(), claim.requestId(), userId, mail.recipient(),
                        retryable ? PaymentNotificationStatus.FAILED : PaymentNotificationStatus.SEND_UNCERTAIN,
                        message, ex);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
            }
            // SMTP 성공을 먼저 보존한다. 이후 출고 확정만 실패하면 다음 요청은 메일을 건너뛴다.
            // 이 저장 자체가 실패하면 SENDING을 유지하며 자동 재전송하지 않는다.
            try {
                transaction().executeWithoutResult(status -> {
                    CustomerOrder order = lockedOrder(input.orderId());
                    request(claim.requestId()).complete(PaymentNotificationStatus.MAIL_SENT,
                            "이메일 전송 완료. 출고 확정을 기다리고 있습니다.");
                    logRepository.save(ClientNotiLog.shipping(order, userRepository.getReferenceById(userId),
                            mail.recipient(), ClientNotiStatus.SENT, null));
                });
            } catch (RuntimeException ex) {
                log.error("출고 메일 결과 저장 실패. requestId={}", claim.requestId(), ex);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "메일 전송 후 결과를 저장하지 못했습니다. 재발송하지 말고 처리 결과를 확인해주세요.");
            }
        }
        try {
            return transaction().execute(status -> confirm(input.orderId(), claim.requestId(), userId));
        } catch (RuntimeException ex) {
            log.error("메일 전송 후 출고 확정 실패. requestId={}", claim.requestId(), ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "이메일은 전송됐지만 출고 확정을 완료하지 못했습니다. 다시 출고하면 이메일 없이 확정만 재시도합니다.");
        }
    }

    private Claim claim(ShipmentDto.CreateRequest input, Long userId) {
        User actor = validateActor(userId);
        if (input == null || input.orderId() == null) {
            throw new IllegalArgumentException("수주를 선택해 주세요.");
        }
        CustomerOrder order = lockedOrder(input.orderId());
        String carrierCode = input.carrierCode() == null ? "" : input.carrierCode().trim();
        String trackingNumber = normalizeTrackingNumber(input.trackingNumber());
        Shipment shipment = shipmentRepository.findByOrderId(order.getId()).orElse(null);
        if (shipment != null && (!shipment.getCarrier().getCode().equals(carrierCode)
                || !shipment.getTrackingNumber().equals(trackingNumber))) {
            throw conflict("저장된 출고 요청과 택배사·운송장 번호가 다릅니다. 기존 정보로 다시 시도해주세요.");
        }
        if (shipment != null && shipment.getStartedAt() != null
                && Set.of("SHIPPING", "COMPLETED").contains(order.getStatusCode().getCode())) {
            return new Claim(null, true, true);
        }
        requireReady(order);
        PaymentNotificationRequest previous = requestRepository
                .findFirstByOrder_IdAndKindOrderByUpdatedAtDesc(order.getId(), PaymentNotificationKind.SHIPMENT)
                .orElse(null);
        if (previous != null) {
            if (previous.getStatus() == PaymentNotificationStatus.MAIL_SENT) {
                return new Claim(previous.getId(), true, false);
            }
            if (Set.of(PaymentNotificationStatus.PROCESSING, PaymentNotificationStatus.SENDING,
                    PaymentNotificationStatus.SEND_UNCERTAIN, PaymentNotificationStatus.SENT)
                    .contains(previous.getStatus())) {
                throw conflict(previous.getMessage() + " 중복 처리하지 않으며 처리 결과를 먼저 확인해주세요.");
            }
        }
        validateRecipient(order.getEmail());
        requireDepositPaid(order.getId());
        if (shipment == null) {
            Carrier carrier = carrierRepository.findById(carrierCode)
                    .orElseThrow(() -> new IllegalArgumentException("등록되지 않은 택배사입니다."));
            if (shipmentRepository.existsByCarrier_CodeAndTrackingNumber(carrierCode, trackingNumber)) {
                throw conflict("같은 택배사에 이미 등록된 운송장 번호입니다.");
            }
            shipmentRepository.saveAndFlush(Shipment.prepare(order.getId(), carrier, trackingNumber));
        }
        if (previous == null) {
            previous = PaymentNotificationRequest.shipment(UUID.randomUUID().toString(), order, actor);
            requestRepository.saveAndFlush(previous);
        } else {
            previous.complete(PaymentNotificationStatus.PROCESSING, "출고 처리를 다시 시도하고 있습니다.");
        }
        return new Claim(previous.getId(), false, false);
    }

    private Long preparePayment(Long orderId) {
        CustomerOrder order = lockedOrder(orderId);
        requireReady(order);
        requireDepositPaid(orderId);
        paymentService.prepareAndScheduleIssue(order, PaymentType.BALANCE);
        return paymentRepository.findByOrder_IdAndPaymentType(orderId, PaymentType.BALANCE)
                .orElseThrow(() -> conflict("잔금 결제 정보가 없습니다.")).getId();
    }

    private MailContext prepareMail(Long orderId, String requestId) {
        CustomerOrder order = lockedOrder(orderId);
        requireReady(order);
        validateRecipient(order.getEmail());
        requireDepositPaid(orderId);
        Payment payment = paymentRepository.findByOrder_IdAndPaymentType(orderId, PaymentType.BALANCE)
                .orElseThrow(() -> conflict("잔금 결제 정보가 없습니다."));
        if (payment.getAmount() != PaymentAmountPolicy.calculate(order.getTotalAmount(), PaymentType.BALANCE)
                || payment.getPaymentMethod() != PaymentMethod.VA) {
            throw conflict("잔금 청구액과 결제 방식을 확인해주세요.");
        }
        Shipment shipment = shipmentRepository.findByOrderId(orderId).orElseThrow();
        StringBuilder body = new StringBuilder()
                .append("안녕하세요, ").append(order.getClient().getCompany()).append(" 담당자님.\nPRODFLOW입니다.\n\n")
                .append("주문하신 제품의 배송 정보와 잔금 입금 계좌를 안내드립니다.\n\n■ 배송 정보\n수주번호: ").append(order.getOrderNo())
                .append("\n택배사: ").append(shipment.getCarrier().getName())
                .append("\n운송장 번호: ").append(shipment.getTrackingNumber())
                .append("\n배송지: ").append(order.getShippingAddress())
                .append("\n수령인: ").append(order.getReceiverName())
                .append("\n\n배송 진행 상황은 택배사에 운송장 정보가 반영된 후\n해당 택배사의 배송조회 서비스를 통해 확인하실 수 있습니다.\n\n")
                .append("■ 잔금 입금 안내\n수주 총액: ").append(won(order.getTotalAmount()))
                .append("\n잔금 청구액: ").append(won(payment.getAmount()));
        if (payment.getStatus() == PaymentStatus.PAID) {
            body.append("\n잔금 입금이 확인되었습니다. 감사합니다.");
        } else {
            VirtualAccount account = accountRepository.findFirstByPayment_IdOrderByIdDesc(payment.getId())
                    .orElseThrow(() -> conflict("잔금 가상계좌가 없습니다."));
            if (payment.getStatus() != PaymentStatus.WAITING || !account.isActive() || account.isExpired()
                    || (account.getPortonePaymentId() != null
                    && !Objects.equals(account.getPortonePaymentId(), payment.getPortonePaymentId()))) {
                throw conflict("입금 가능한 잔금 계좌가 아닙니다. 결제 상태·재발급 필요 여부를 확인해주세요.");
            }
            body.append("\n입금은행: ").append(account.getBank().getLabel())
                    .append("\n계좌번호: ").append(account.getAccountNumber())
                    .append("\n예금주: ").append(Objects.toString(account.getAccountName(), "은행 안내 참조"))
                    .append("\n가상계좌 만료일시: ").append(account.getExpiresAt().atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append(" (한국 시간)")
                    .append("\n\n제품 수령 및 확인 후 계좌 만료일시 내 잔금 입금 부탁드립니다.");
        }
        body.append("\n제품이나 배송 관련 문의 사항은 본 메일로 회신해 주시기 바랍니다.\n\n거래해 주셔서 감사합니다.\nPRODFLOW 드림");
        request(requestId).complete(PaymentNotificationStatus.SENDING,
                "이메일 전송 중입니다. 결과 확인 전 다시 발송하지 마세요.");
        return new MailContext(order.getEmail(), "[PRODFLOW] 배송 시작 및 잔금 안내 — " + order.getOrderNo(),
                body.toString());
    }

    private ShipmentDto.Response confirm(Long orderId, String requestId, Long userId) {
        User actor = validateActor(userId);
        CustomerOrder order = lockedOrder(orderId);
        Shipment shipment = shipmentRepository.findByOrderId(orderId).orElseThrow();
        PaymentNotificationRequest request = request(requestId);
        if (request.getStatus() == PaymentNotificationStatus.SENT && shipment.getStartedAt() != null) {
            return ShipmentDto.Response.from(shipment, order);
        }
        requireReady(order);
        if (request.getStatus() != PaymentNotificationStatus.MAIL_SENT) {
            throw conflict("이메일 전송 완료가 확인되지 않아 출고할 수 없습니다.");
        }
        var shipping = commonCodeRepository.findByCodeGroup_GroupCodeAndCodeAndStatus(
                "ORDER_STATUS", "SHIPPING", CodeStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("배송중 상태 코드를 찾을 수 없습니다."));
        shipment.confirmDispatch(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        order.changeProgress(shipping);
        historyRepository.save(new StatusHistory(HistoryEntityType.ORDER, orderId,
                "READY_TO_SHIP", "SHIPPING", actor));
        request.complete(PaymentNotificationStatus.SENT, "잔금 계좌와 이메일 안내를 완료하고 출고했습니다.");
        return ShipmentDto.Response.from(shipment, order);
    }

    private void recordFailure(Long orderId, String requestId, Long userId, String recipient,
                               PaymentNotificationStatus status, String message, RuntimeException error) {
        log.error("출고 처리 실패. orderId={}, status={}", orderId, status, error);
        transaction().executeWithoutResult(tx -> {
            CustomerOrder order = lockedOrder(orderId);
            request(requestId).complete(status, message);
            if (recipient != null) {
                logRepository.save(ClientNotiLog.shipping(order, userRepository.getReferenceById(userId),
                        recipient, ClientNotiStatus.FAILED, message));
            }
        });
    }

    private ShipmentDto.Response response(Long orderId) {
        return transaction().execute(tx -> ShipmentDto.Response.from(
                shipmentRepository.findByOrderId(orderId).orElseThrow(), lockedOrder(orderId)));
    }

    private CustomerOrder lockedOrder(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("수주를 찾을 수 없습니다."));
    }

    private PaymentNotificationRequest request(String id) {
        return requestRepository.findById(id).orElseThrow();
    }

    private void requireReady(CustomerOrder order) {
        if (!"READY_TO_SHIP".equals(order.getStatusCode().getCode())) {
            throw conflict("출고대기 수주만 출고 처리할 수 있습니다.");
        }
    }

    private void requireDepositPaid(Long orderId) {
        Payment deposit = paymentRepository.findByOrder_IdAndPaymentType(orderId, PaymentType.DEPOSIT)
                .orElseThrow(() -> conflict("계약금 결제 정보가 없습니다."));
        if (deposit.getStatus() != PaymentStatus.PAID) {
            throw conflict("계약금 입금 완료 후 출고할 수 있습니다.");
        }
    }

    private User validateActor(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AccessDeniedException("계정을 찾을 수 없습니다."));
        if (user.getStatus() != UserStatus.ACTIVE || user.isMustChangePassword()
                || !Set.of(UserRole.SUPER_ADMIN, UserRole.ORDER_MANAGER).contains(user.getRole())) {
            throw new AccessDeniedException("사용 가능한 관리자 계정으로 비밀번호 변경 후 출고해주세요.");
        }
        return user;
    }

    private void validateRecipient(String email) {
        try {
            if (email == null || email.isBlank() || email.length() > 100) {
                throw new AddressException("수신 이메일을 확인해주세요.");
            }
            InternetAddress address = new InternetAddress(email, true);
            address.validate();
            if (!email.equals(address.getAddress())) {
                throw new AddressException("단일 이메일 주소가 필요합니다.");
            }
        } catch (AddressException ex) {
            throw new IllegalArgumentException("거래처 수신 이메일 형식 또는 길이를 확인해주세요.");
        }
    }

    private String normalizeTrackingNumber(String raw) {
        String value = raw == null ? "" : raw.replace("-", "").replace(" ", "");
        if (value.isEmpty() || !value.chars().allMatch(Character::isDigit) || value.length() > 50) {
            throw new IllegalArgumentException("운송장 번호는 숫자만 입력해 주세요.");
        }
        return value;
    }

    // SMTP 접수 후 타임아웃·연결 종료는 미전송으로 단정할 수 없다.
    private boolean definitelyNotSent(Throwable error) {
        if (error instanceof MailAuthenticationException || error instanceof MailParseException
                || error instanceof MailPreparationException) {
            return true;
        }
        if (error instanceof SendFailedException failed) {
            return (failed.getValidSentAddresses() == null || failed.getValidSentAddresses().length == 0)
                    && ((failed.getInvalidAddresses() != null && failed.getInvalidAddresses().length > 0)
                    || (failed.getValidUnsentAddresses() != null && failed.getValidUnsentAddresses().length > 0));
        }
        if (error instanceof ConnectException || error instanceof UnknownHostException
                || error.getClass().getSimpleName().equals("MailConnectException")) {
            return true;
        }
        if (error instanceof MailSendException failed && failed.getMessageExceptions().length > 0) {
            for (Exception nested : failed.getMessageExceptions()) {
                if (!definitelyNotSent(nested)) return false;
            }
            return true;
        }
        return error.getCause() != null && error.getCause() != error && definitelyNotSent(error.getCause());
    }

    private TransactionTemplate transaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx;
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private String won(long amount) {
        return String.format(Locale.KOREA, "%,d원", amount);
    }

    private record Claim(String requestId, boolean mailSent, boolean completed) { }
    private record MailContext(String recipient, String subject, String body) { }
}
