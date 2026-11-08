package net.likelion.bebc25.prodflow.payment.repository;

import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VirtualAccountRepository
        extends JpaRepository<VirtualAccount, Long> {

    //특정 결제의 가상계좌 목록
    List<VirtualAccount> findByPayment_IdOrderByIdDesc(
            Long paymentId
    );

    // 특정 결제의 최신 가상계좌
    Optional<VirtualAccount> findFirstByPayment_IdOrderByIdDesc(
            Long paymentId
    );

    // 여러 결제건의 가상계좌 일괄 조회
    List<VirtualAccount> findByPayment_IdInOrderByIdDesc(
            Collection<Long> paymentIds
    );
}
