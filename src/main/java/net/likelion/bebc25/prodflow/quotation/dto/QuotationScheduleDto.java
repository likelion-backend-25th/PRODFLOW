package net.likelion.bebc25.prodflow.quotation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;

import net.likelion.bebc25.prodflow.quotation.schedule.QuotationSchedulePreviewResult;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleResult;

public class QuotationScheduleDto {

    public record Request(
            @NotEmpty(message = "품목을 하나 이상 선택해주세요.")
            List<@NotNull(message = "납기 계산 품목은 비어 있을 수 없습니다.") @Valid ItemRequest> items,
            LocalDate requestedDueDate
    ) {
    }

    public record ItemRequest(
            @NotNull(message = "품목 ID를 입력해주세요.")
            @Positive(message = "품목 ID는 양수여야 합니다.")
            Long productId,

            @NotNull(message = "수량을 입력해주세요.")
            @Min(value = 100, message = "품목별 수량은 최소 100개입니다.")
            Integer quantity
    ) {
    }

    // 가장 빠른 납기와 가능 여부
    public record Response(
            LocalDate earliestDueDate, // 가장 빠른 납기일
            LocalDate requestedDueDate, // 희망 납기일
            boolean feasible, // 가능한지 여부
            LocalDate plannedStartDate, // 시작 예정일
            LocalDate plannedEndDate // 완료 예정일
    ) {
        // 납기 미리보기 응답 변환
        public static Response from(QuotationSchedulePreviewResult result, LocalDate requestedDueDate) {
            QuotationScheduleResult selected = result.selectedSchedule();
            return new Response(
                    result.earliestSchedule().estimatedDueDate(),
                    requestedDueDate,
                    selected != null,
                    selected == null ? null : selected.plannedStartDate(),
                    selected == null ? null : selected.plannedEndDate()
            );
        }
    }
}
