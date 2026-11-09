package net.likelion.bebc25.prodflow.payment.repository;

import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends
        JpaRepository<Payment, Long>,
        JpaSpecificationExecutor<Payment> {

    // 수주 ID와 결제 유형으로 결제 존재 여부 확인
    boolean existsByOrder_IdAndPaymentType(
            Long orderId,
            PaymentType paymentType
    );

    // 수주 ID와 결제 유형으로 결제 조회
    Optional<Payment> findByOrder_IdAndPaymentType(
            Long orderId,
            PaymentType paymentType
    );

    // PortOne 결제 ID로 조회
    Optional<Payment> findByPortonePaymentId(
            String portonePaymentId
    );

    // 수주 상세에 표시할 결제 목록 조회 (팀원 코드 유지)
    List<Payment> findByOrder_IdOrderByIdAsc(Long orderId);

    // 검색 + 페이징 시 수주와 거래처 함께 조회
    @Override
    @EntityGraph(attributePaths = {"order", "order.client"})
    Page<Payment> findAll(
            Specification<Payment> spec,
            Pageable pageable
    );

    // 상세 조회 시 수주와 거래처 함께 조회
    @Override
    @EntityGraph(attributePaths = {
            "order",
            "order.client",
            "order.statusCode"
    })
    Optional<Payment> findById(Long id);

    // 취소되지 않은 전체 수주 금액
    // 아직 생성되지 않은 잔금도 포함
    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0)
            FROM CustomerOrder o
            WHERE o.statusCode.code <> 'CANCELLED'
            """)
    Long sumValidOrderAmount();

    // 취소되지 않은 수주의 상태별 결제 금액
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            JOIN p.order o
            WHERE o.statusCode.code <> 'CANCELLED'
              AND p.status = :status
            """)
    Long sumAmountByStatus(
            @Param("status") PaymentStatus status
    );
}
