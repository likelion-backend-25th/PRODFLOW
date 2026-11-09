package net.likelion.bebc25.prodflow.payment.repository;

import jakarta.persistence.LockModeType;
import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {

    // 결제건 하나의 입금내역 조회
    Optional<PaymentHistory> findByPayment_Id(Long paymentId);

    // 여러 결제건의 입금내역 일괄 조회
    List<PaymentHistory> findByPayment_IdIn(Collection<Long> paymentIds);

    // 결제건에 입금내역이 존재하는지 확인
    boolean existsByPayment_Id(Long paymentId);

    // 선택한 수주 결제에 연결된 입금내역 조회
    List<PaymentHistory> findByPayment_Order_Id(Long orderId);

    // 동시성 제어를 위한 비관적 쓰기 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ph
            FROM PaymentHistory ph
            WHERE ph.payment.id = :paymentId
            """)
    Optional<PaymentHistory> getByPayment_IdForUpdate(
            @Param("paymentId") Long paymentId
    );
}
