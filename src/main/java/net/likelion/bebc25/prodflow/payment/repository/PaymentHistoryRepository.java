package net.likelion.bebc25.prodflow.payment.repository;

import jakarta.persistence.LockModeType;
import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {
    boolean existsByPayment_Id(Long paymentId);

    // 선택한 수주 결제에 연결된 입금 내역 조회
    List<PaymentHistory> findByPayment_Order_Id(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ph from PaymentHistory ph where ph.payment.id = :paymentId")
    Optional<PaymentHistory> getByPayment_IdForUpdate(@Param("payment_id") Long paymentId);
}
