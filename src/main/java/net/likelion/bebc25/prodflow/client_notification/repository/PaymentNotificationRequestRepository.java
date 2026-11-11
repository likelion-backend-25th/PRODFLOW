package net.likelion.bebc25.prodflow.client_notification.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import net.likelion.bebc25.prodflow.client_notification.entity.PaymentNotificationRequest;

public interface PaymentNotificationRequestRepository extends JpaRepository<PaymentNotificationRequest, String> {
    Optional<PaymentNotificationRequest> findFirstByOrder_IdOrderByUpdatedAtDesc(Long orderId);
}
