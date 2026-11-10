package net.likelion.bebc25.prodflow.common.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.dto.StatusHistoryDto;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StatusHistoryService {

    private final StatusHistoryRepository statusHistoryRepository;

    // 특정 대상의 상태 변경 이력을 최신순으로 조회
    public List<StatusHistoryDto.Response> getStatusHistories(
            HistoryEntityType entity,
            Long entityId
    ) {

        // Repository를 통해 상태 변경 이력 조회
        List<StatusHistory> histories =
                statusHistoryRepository.findByEntityAndEntityIdOrderByCreatedAtDesc(
                        entity,
                        entityId
                );

        // 엔티티를 응답 DTO로 변환
        return histories.stream()
                .map(StatusHistoryDto.Response::from)
                .toList();
    }

    /**
     * 특정 유형(ORDER / WORK_ORDER / LINE)의 전체 상태 변경 이력 조회
     *
     * @param entity 조회 대상 유형 (ORDER / WORK_ORDER / LINE)
     * @return 해당 유형의 상태 변경 이력 (변경 일시 최신순)
     */
    public List<StatusHistoryDto.Response> getStatusHistoriesByEntity(
            HistoryEntityType entity
    ) {

        // 해당 유형의 모든 상태 변경 이력을 최신순으로 조회
        List<StatusHistory> histories =
                statusHistoryRepository.findByEntityOrderByCreatedAtDesc(entity);

        // 조회한 엔티티를 응답 DTO로 변환
        return histories.stream()
                .map(StatusHistoryDto.Response::from)
                .toList();
    }
}