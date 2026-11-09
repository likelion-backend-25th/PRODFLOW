package net.likelion.bebc25.prodflow.payment.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.payment.dto.PortOneDto;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.repository.PaymentHistoryRepository;
import net.likelion.bebc25.prodflow.payment.repository.PaymentRepository;
import net.likelion.bebc25.prodflow.payment.repository.VirtualAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentStore {
    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final VirtualAccountRepository virtualAccountRepository;

    public Payment applyWebhook(Long id, PortOneDto.PaymentSnapshot snapshot, String type) {
        Payment payment = lockedPayment(id);
        PaymentHistory paymentHistory = lockedPaymentHistory(id);
        VirtualAccount virtualAccount = lockedVirtualAccount(id);
        apply(payment, paymentHistory, virtualAccount, snapshot);
        paymentHistory.recordWebhookType(type);
        return payment;
    }

    public void apply(
            Payment payment,
            PaymentHistory paymentHistory,
            VirtualAccount virtualAccount,
            PortOneDto.PaymentSnapshot snapshot
    ) {
        if (paymentHistory.getWebhookRemoteUpdatedAt() != null
                && snapshot.updatedAt().isBefore(paymentHistory.getWebhookRemoteUpdatedAt())) {
            return;
        }
        payment.updateStatus(PaymentStatus.valueOf(snapshot.status()));
        if (snapshot.method() != null && snapshot.method().accountNumber() != null) {
            virtualAccount.updateAccountNumber(snapshot.method().accountNumber());
            virtualAccount.updateExpiresAt(snapshot.method().expiredAt());
        }
        paymentHistory.updatePaidAt(snapshot.paidAt());
        paymentHistory.updateWebhookRemoteUpdatedAt(snapshot.updatedAt());
    }

    private Payment lockedPayment(Long id) {
        return paymentRepository.getForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 결제 내역을 찾을 수 없습니다. id = " + id));
    }

    private PaymentHistory lockedPaymentHistory(Long paymentId) {
        return paymentHistoryRepository.getByPayment_IdForUpdate(paymentId)
                .orElseThrow(() ->
                        new IllegalArgumentException("해당 결제 내역 기록을 찾을 수 없습니다. id = " + paymentId));
    }

    private VirtualAccount lockedVirtualAccount(Long paymentId) {
        return virtualAccountRepository.findRecentByPayment_IdForUpdate(paymentId)
                .orElseThrow(() ->
                        new IllegalArgumentException("해당 가상 계좌를 찾을 수 없습니다. id = " + paymentId));
    }
}
