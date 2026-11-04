package net.likelion.bebc25.prodflow.quotation.schedule;

import java.time.LocalDate;

// 납기와 생산 시작·완료 예정일 계산 결과
public record QuotationScheduleResult(
        LocalDate estimatedDueDate,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate
) {
}
