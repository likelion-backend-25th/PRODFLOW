package net.likelion.bebc25.prodflow.quotation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class QuotationRescheduleDto {
    // 날짜를 생략하면 기존 견적 수량의 가장 빠른 납기 조회
    public record PreviewRequest(LocalDate requestedDueDate) {
    }

    // 재산정한 일정을 저장할 때는 희망 납기 필수
    public record UpdateRequest(
            @NotNull(message = "희망 납기일을 입력해주세요.")
            LocalDate requestedDueDate
    ) {
    }
}
