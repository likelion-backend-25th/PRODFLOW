package net.likelion.bebc25.prodflow.client_notification.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import net.likelion.bebc25.prodflow.client_notification.entity.ClientNotiLog;

public interface ClientNotiLogRepository extends JpaRepository<ClientNotiLog, Long> {
    // 견적의 최근 발송 결과 조회
    Optional<ClientNotiLog> findFirstByQuotationIdAndKindOrderByIdDesc(Long quotationId, String kind);
}
