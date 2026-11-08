package net.likelion.bebc25.prodflow.common.repository;

import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    /**
     * 특정 대상(수주 또는 작업지시서 또는 라인)의 상태 변경 이력 조회
     *
     * @param entity (ORDER / WORK_ORDER / LINE)
     * @return 변경 일시(createdAt) 기준 최신순 정렬
     */
    List<StatusHistory> findByEntityAndEntityIdOrderByCreatedAtDesc(
            HistoryEntityType entity,
            Long entityId
    );

    // 특정 entity에서 발생하는 모든 상태 이력을 조회
    List<StatusHistory> findByEntityOrderByCreatedAtDesc(
            HistoryEntityType entity
    );
}
