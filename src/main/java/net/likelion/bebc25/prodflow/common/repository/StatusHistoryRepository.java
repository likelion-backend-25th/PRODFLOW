package net.likelion.bebc25.prodflow.common.repository;

import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
}
