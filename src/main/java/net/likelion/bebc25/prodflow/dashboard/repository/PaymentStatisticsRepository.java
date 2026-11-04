package net.likelion.bebc25.prodflow.dashboard.repository;

import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentStatisticsRepository extends JpaRepository<Payment, Long> {

    // 수주별 입금 완료 금액 합계 조회
    @Query("""
            SELECT p.order.id, COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.order.id IN :orderIds
            AND p.status = :status
            GROUP BY p.order.id
            """)
    List<Object[]> sumPaidAmountsByOrderIds(
            @Param("orderIds") List<Long> orderIds,
            @Param("status") PaymentStatus status
    );
}
