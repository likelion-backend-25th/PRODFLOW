package net.likelion.bebc25.prodflow.work_order.dto;

import net.likelion.bebc25.prodflow.work_order.entity.WorkOrderHistory;


public class WorkOrderHistoryDto {

    // 작업지시서 라인 및 담당자 변경 이력 조회
    public record Response(
            String previousLineName,  // 변경 전 라인
            String newLineName,       // 변경 후 라인
            String previousUserName,  // 변경 전 담당자
            String newUserName,       // 변경 후 담당자
            String changedByName     // 변경한 직원
    ) {

        // WorkOrderHistory 엔티티를 응답 DTO로 변환
        public static Response from(WorkOrderHistory history) {
            return new Response(
                    history.getPreviousLine().getLineName(),
                    history.getNewLine().getLineName(),
                    history.getPreviousUser().getName(),
                    history.getNewUser().getName(),
                    history.getChangedBy().getName()
            );
        }
    }
}