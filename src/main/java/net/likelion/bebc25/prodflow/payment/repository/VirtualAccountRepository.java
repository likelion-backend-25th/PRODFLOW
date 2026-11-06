package net.likelion.bebc25.prodflow.payment.repository;

import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, Long> {

    List<VirtualAccount> findByPayment_IdOrderByIdDesc(Long paymentId);

    Optional<VirtualAccount> findFirstByPayment_IdOrderByIdDesc(Long paymentId);
}
