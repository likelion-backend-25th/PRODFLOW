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

    //특정 결제의 가상계좌 목록
    List<VirtualAccount> findByPayment_IdOrderByIdDesc(
            Long paymentId
    );
    // 상태와 무관하게 결제별 가장 최근 발급된 계좌를 일괄 조회한다.
    @Query("""
            select v from VirtualAccount v
            join fetch v.payment p
            where p.order.id = :orderId
              and v.id = (select max(latest.id) from VirtualAccount latest where latest.payment = p)
            """)
    List<VirtualAccount> findLatestByOrderId(@Param("orderId") Long orderId);

    // 특정 결제의 최신 가상계좌
    Optional<VirtualAccount> findFirstByPayment_IdOrderByIdDesc(
            Long paymentId
    );

    // 여러 결제건의 가상계좌 일괄 조회
    List<VirtualAccount> findByPayment_IdInOrderByIdDesc(
            Collection<Long> paymentIds
    );
}
