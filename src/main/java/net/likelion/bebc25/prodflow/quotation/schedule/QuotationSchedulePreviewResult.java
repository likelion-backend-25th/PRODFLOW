package net.likelion.bebc25.prodflow.quotation.schedule;

// 가장 빠른 일정과 선택한 납기의 일정. 선택한 납기가 불가능하면 selectedSchedule은 null
public record QuotationSchedulePreviewResult(
        QuotationScheduleResult earliestSchedule,
        QuotationScheduleResult selectedSchedule
) {
}
