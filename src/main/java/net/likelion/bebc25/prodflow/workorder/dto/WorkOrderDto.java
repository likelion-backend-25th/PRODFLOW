package net.likelion.bebc25.prodflow.workorder.dto;

import java.time.LocalDate;

public class WorkOrderDto {

    public record CreateRequest(
            Long orderProductId, // 생산 품목
            Long LineId, // 작업 라인
            Long assignedId, // 담당자
            int quantity, // 이 라인에 배정하는 수량
            LocalDate plannedStartDate,
            LocalDate plannedEndDate
    ) {
    }

    // 등록 후 응답
    public record Response(
            Long id, // 저장된 작업지시서 고유 번호
            String workNo, // 작업 지시 번호
            String note
    ) {
    }
}
