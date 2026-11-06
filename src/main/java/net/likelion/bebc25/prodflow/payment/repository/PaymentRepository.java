package net.likelion.bebc25.prodflow.payment.repository;

import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByOrder_IdAndPaymentType(Long orderId, PaymentType paymentType);

    Optional<Payment> findByOrder_IdAndPaymentType(Long orderId, PaymentType paymentType);

    Optional<Payment> findByPortonePaymentId(String portonePaymentId);
}
