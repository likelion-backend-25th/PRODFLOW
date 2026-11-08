package net.likelion.bebc25.prodflow.line.serivce;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.line.dto.LineDto;
import net.likelion.bebc25.prodflow.line.repsitory.LineCapacityLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LineCapacityLogService {

    private final LineCapacityLogRepository lineCapacityLogRepository;

    // 특정 생산라인의 생산량 변경 이력 조회
    public List<LineDto.CapacityLog> getCapacityHistories(Long lineId) {

        // 생산라인 ID를 기준으로 변경 이력을 최신순 조회
        return lineCapacityLogRepository
                .findTop3ByLine_IdOrderByChangedAtDesc(lineId)
                .stream()
                // 조회한 엔티티에서 필요한 정보만 DTO로 변환
                .map(log -> new LineDto.CapacityLog(
                        log.getChangedAt(),
                        log.getBeforeCapacity(),
                        log.getAfterCapacity(),
                        log.getReason(),
                        log.getChangedBy().getName()
                ))
                .toList();
    }
}