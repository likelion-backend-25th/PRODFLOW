package net.likelion.bebc25.prodflow.common.dto;

import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;

import java.time.LocalDateTime;

public class StatusHistoryDto {

    // 저장된 상태 변경 이력 조회 응답
    public record Response(

            Long id,                  // 상태 변경 이력 ID
            HistoryEntityType entity, // 변경 대상 구분 (ORDER / WORK_ORDER/ LINE)
            Long entityId,            // 변경 대상 ID
            String fromStatus,        // 변경 전 상태 코드
            String toStatus,          // 변경 후 상태 코드
            String userName,          // 상태를 변경한 사용자 이름
            String note,              // 상태 변경 사유
            LocalDateTime createdAt   // 상태 변경 일시
    ) {
        // StatusHistory 엔티티를 응답 DTO로 변환
        public static Response from(StatusHistory history) {
            return new Response(
                    history.getId(),
                    history.getEntity(),
                    history.getEntityId(),
                    history.getFromStatus(),
                    history.getToStatus(),
                    history.getUserName(),
                    history.getNote(),
                    history.getCreatedAt()
            );
        }
    }
}
