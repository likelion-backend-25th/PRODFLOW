package net.likelion.bebc25.prodflow.payment.repository;

import jakarta.persistence.LockModeType;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.VirtualAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, Long> {

    // 특정 결제의 가상계좌 목록 (최신순)
    List<VirtualAccount> findByPayment_IdOrderByIdDesc(Long paymentId);

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

    // 특정 결제의 최신 가상계좌를 비관적 쓰기 락으로 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT vc
            FROM VirtualAccount vc
            WHERE vc.id = (
                SELECT MAX(latest.id)
                FROM VirtualAccount latest
                WHERE latest.payment.id = :paymentId
            )
            """)
    Optional<VirtualAccount> findRecentByPayment_IdForUpdate(
            @Param("paymentId") Long paymentId
    );

    // 지정 시각까지 만료된 ACTIVE 계좌만 DB에 EXPIRED로 반영한다.
    // 상태 조건을 UPDATE 자체에 포함하므로 USED/CANCELLED는 변경되지 않는다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE VirtualAccount v
            SET v.status = :expired,
                v.updatedAt = :updatedAt,
                v.updatedBy = 'SYSTEM'
            WHERE v.status = :active
              AND v.expiresAt <= :now
            """)
    int expireActiveAccounts(
            @Param("now") OffsetDateTime now,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("active") VirtualAccountStatus active,
            @Param("expired") VirtualAccountStatus expired
    );

    // 여러 결제건의 가상계좌 일괄 조회 (최신순)
    List<VirtualAccount> findByPayment_IdInOrderByIdDesc(
            Collection<Long> paymentIds
    );
}
