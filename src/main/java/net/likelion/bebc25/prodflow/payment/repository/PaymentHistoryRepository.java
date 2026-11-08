package net.likelion.bebc25.prodflow.payment.repository;

import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {
    boolean existsByPayment_Id(Long paymentId);
    // 선택한 수주 결제에 연결된 입금 내역 조회
    List<PaymentHistory> findByPayment_Order_Id(Long orderId);
}
