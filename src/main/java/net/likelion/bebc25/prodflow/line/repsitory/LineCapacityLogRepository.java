package net.likelion.bebc25.prodflow.line.repsitory;

import net.likelion.bebc25.prodflow.line.entity.LineCapacityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LineCapacityLogRepository extends JpaRepository<LineCapacityLog, Long> {

    // 상세의 생산량 변경 이력. 최근 변경이 앞이다.
    List<LineCapacityLog> findByLine_IdOrderByChangedAtDesc(Long lineId);
}
