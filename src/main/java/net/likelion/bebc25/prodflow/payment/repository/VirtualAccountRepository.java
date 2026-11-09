package net.likelion.bebc25.prodflow.payment.repository;

import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VirtualAccountRepository
        extends JpaRepository<VirtualAccount, Long> {

    // 특정 결제의 가상계좌 목록 (최신순)
    List<VirtualAccount> findByPayment_IdOrderByIdDesc(
            Long paymentId
    );

    // 상태와 무관하게 수주별 각 결제의 가장 최근 발급된 계좌 조회
    @Query("""
            SELECT v
            FROM VirtualAccount v
            JOIN FETCH v.payment p
            WHERE p.order.id = :orderId
              AND v.id = (
                  SELECT MAX(latest.id)
                  FROM VirtualAccount latest
                  WHERE latest.payment = p
              )
            """)
    List<VirtualAccount> findLatestByOrderId(
            @Param("orderId") Long orderId
    );

    // 특정 결제의 최신 가상계좌 한 건
    Optional<VirtualAccount> findFirstByPayment_IdOrderByIdDesc(
            Long paymentId
    );

    // 여러 결제건의 가상계좌 일괄 조회 (최신순)
    List<VirtualAccount> findByPayment_IdInOrderByIdDesc(
            Collection<Long> paymentIds
    );
}
