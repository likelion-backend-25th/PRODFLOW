package net.likelion.bebc25.prodflow.payment.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.mail.MailSendException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import net.likelion.bebc25.prodflow.client_notification.entity.PaymentNotificationRequest;
import net.likelion.bebc25.prodflow.client_notification.enums.PaymentNotificationStatus;
import net.likelion.bebc25.prodflow.client_notification.repository.ClientNotiLogRepository;
import net.likelion.bebc25.prodflow.client_notification.repository.PaymentNotificationRequestRepository;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDetailDto;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.customer_order.service.CustomerOrderService;
import net.likelion.bebc25.prodflow.mail.service.MailService;
import net.likelion.bebc25.prodflow.payment.dto.PaymentNotificationDto;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.Bank;
import net.likelion.bebc25.prodflow.payment.enums.PaymentMethod;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import net.likelion.bebc25.prodflow.payment.repository.PaymentRepository;
import net.likelion.bebc25.prodflow.payment.repository.VirtualAccountRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentNotificationServiceTest {
    private final CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
    private final CustomerOrderService orderService = mock(CustomerOrderService.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final VirtualAccountRepository accounts = mock(VirtualAccountRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PaymentNotificationRequestRepository requests = mock(PaymentNotificationRequestRepository.class);
    private final ClientNotiLogRepository logs = mock(ClientNotiLogRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final MailService mail = mock(MailService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final Map<String, PaymentNotificationRequest> saved = new HashMap<>();
    private final CustomerOrder order = mock(CustomerOrder.class);
    private final Payment payment = mock(Payment.class);
    private final VirtualAccount account = mock(VirtualAccount.class);
    private PaymentNotificationService service;
    private PaymentNotificationDto.Request request;

    @BeforeEach
    void setUp() {
        service = new PaymentNotificationService(orders, orderService, payments, accounts, users, requests,
                logs, paymentService, mail, transactions, mock(EntityManager.class));
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        User sender = mock(User.class);
        when(sender.getRole()).thenReturn(UserRole.ORDER_MANAGER);
        when(sender.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(users.findById(7L)).thenReturn(Optional.of(sender));
        when(users.getReferenceById(7L)).thenReturn(sender);
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orders.getReferenceById(1L)).thenReturn(order);
        CommonCode code = mock(CommonCode.class);
        when(code.getCode()).thenReturn("WAITING");
        when(order.getStatusCode()).thenReturn(code);
        when(order.getDepositDueDate()).thenReturn(LocalDate.of(2099, 1, 1));
        when(order.getTotalAmount()).thenReturn(11000L);
        CustomerOrderDetailDto.Response detail = mock(CustomerOrderDetailDto.Response.class);
        when(detail.client()).thenReturn(new CustomerOrderDetailDto.ClientResponse(
                2L, "거래처", "담당자", "client@example.com", "01012345678", "서울"));
        when(detail.orderNo()).thenReturn("PF-1");
        when(detail.totalAmount()).thenReturn(11000L);
        when(detail.supplyAmount()).thenReturn(10000L);
        when(detail.vatAmount()).thenReturn(1000L);
        when(detail.depositDueDate()).thenReturn(LocalDate.of(2099, 1, 1));
        when(detail.items()).thenReturn(List.of(new CustomerOrderDetailDto.ItemResponse(
                3L, 4L, "품목 A", "기종", 100, 100L, 10000L, 1000L, 11000L)));
        when(orderService.getOrder(1L)).thenReturn(detail);
        request = request("11111111-1111-1111-1111-111111111111", 11000L);
        when(payment.getId()).thenReturn(5L);
        when(payment.getPaymentMethod()).thenReturn(PaymentMethod.VA);
        when(payment.getStatus()).thenReturn(PaymentStatus.WAITING);
        when(payment.getAmount()).thenReturn(5500L);
        when(payments.findByOrder_IdAndPaymentType(1L, PaymentType.DEPOSIT)).thenReturn(Optional.of(payment));
        when(accounts.findFirstByPayment_IdOrderByIdDesc(5L)).thenReturn(Optional.of(account));
        when(account.isActive()).thenReturn(true);
        when(account.getBank()).thenReturn(Bank.SHINHAN);
        when(account.getAccountNumber()).thenReturn("123456789");
        when(requests.findFirstByOrder_IdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(order.getId()).thenReturn(1L);
        when(sender.getId()).thenReturn(7L);
        when(requests.findById(anyString())).thenAnswer(call -> Optional.ofNullable(saved.get(call.getArgument(0))));
        when(requests.saveAndFlush(any())).thenAnswer(call -> {
            PaymentNotificationRequest value = call.getArgument(0);
            saved.put(value.getId(), value);
            return value;
        });
    }

    @Test
    void sendsExistingAccountAndDoesNotResendSameRequest() {
        assertEquals(PaymentNotificationStatus.SENT, service.send(1L, request, 7L).status());
        assertEquals(PaymentNotificationStatus.SENT, service.send(1L, request, 7L).status());
        verify(mail, times(1)).send(eq("client@example.com"), contains("PF-1"), contains("123456789"));
        verify(logs, times(1)).save(any());
        verify(paymentService, never()).prepareAndScheduleIssue(any(), any());
    }

    @Test
    void changedSnapshotDoesNotIssueOrSend() {
        assertThrows(ResponseStatusException.class, () -> service.send(1L,
                request("22222222-2222-2222-2222-222222222222", 12000L), 7L));
        verify(paymentService, never()).issuePayment(any());
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void uncertainAccountDoesNotSendEmail() {
        when(accounts.findFirstByPayment_IdOrderByIdDesc(5L)).thenReturn(Optional.empty());
        assertEquals(PaymentNotificationStatus.ISSUE_UNCERTAIN, service.send(1L, request, 7L).status());
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void emailFailurePreservesAccountAndAllowsExplicitRetry() {
        doThrow(new MailSendException("SMTP failure")).doNothing()
                .when(mail).send(anyString(), anyString(), anyString());
        assertEquals(PaymentNotificationStatus.FAILED, service.send(1L, request, 7L).status());
        assertEquals(PaymentNotificationStatus.SENT, service.send(1L,
                request("22222222-2222-2222-2222-222222222222", 11000L), 7L).status());
        verify(mail, times(2)).send(anyString(), anyString(), contains("123456789"));
        verify(paymentService, never()).prepareAndScheduleIssue(any(), any());
    }

    @Test
    void inProgressOrderBlocksAnotherRequest() {
        User sender = users.getReferenceById(7L);
        when(requests.findFirstByOrder_IdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.of(
                new PaymentNotificationRequest("other-request", order, sender)));
        assertThrows(ResponseStatusException.class, () -> service.send(1L, request, 7L));
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void expiredDeadlineDoesNotIssueOrSend() {
        when(order.getDepositDueDate()).thenReturn(LocalDate.of(2000, 1, 1));
        assertThrows(ResponseStatusException.class, () -> service.send(1L, request, 7L));
        verify(paymentService, never()).issuePayment(any());
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void expiredAccountDoesNotSendEmail() {
        when(account.isExpired()).thenReturn(true);
        assertEquals(PaymentNotificationStatus.ISSUE_UNCERTAIN, service.send(1L, request, 7L).status());
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    private PaymentNotificationDto.Request request(String id, long amount) {
        return new PaymentNotificationDto.Request(id, "client@example.com", amount, 10000L, 1000L,
                5500L, false, LocalDate.of(2099, 1, 1),
                List.of(new PaymentNotificationDto.Item(3L, "품목 A", 100, 100L, 10000L)));
    }
}
