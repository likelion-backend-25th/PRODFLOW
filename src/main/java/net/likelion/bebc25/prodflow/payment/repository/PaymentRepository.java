package net.likelion.bebc25.prodflow.payment.repository;

import jakarta.persistence.LockModeType;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 수주 상세에 표시할 결제 목록 조회
    List<Payment> findByOrder_IdOrderByIdAsc(Long orderId);

    boolean existsByOrder_IdAndPaymentType(Long orderId, PaymentType paymentType);

    Optional<Payment> findByOrder_IdAndPaymentType(Long orderId, PaymentType paymentType);

    Optional<Payment> findByPortonePaymentId(String portonePaymentId);

    boolean existsByPortonePaymentId(String portonePaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> getForUpdate(@Param("id") Long id);
}
