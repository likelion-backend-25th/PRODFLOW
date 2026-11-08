package net.likelion.bebc25.prodflow.payment.repository;

import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 수주 상세에 표시할 결제 목록 조회
    List<Payment> findByOrder_IdOrderByIdAsc(Long orderId);

    boolean existsByOrder_IdAndPaymentType(Long orderId, PaymentType paymentType);

    Optional<Payment> findByOrder_IdAndPaymentType(Long orderId, PaymentType paymentType);

    Optional<Payment> findByPortonePaymentId(String portonePaymentId);
}
