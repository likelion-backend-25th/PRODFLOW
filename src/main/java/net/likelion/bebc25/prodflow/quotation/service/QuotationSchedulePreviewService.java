package net.likelion.bebc25.prodflow.quotation.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationScheduleDto;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationSchedulePreviewResult;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@Validated
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class QuotationSchedulePreviewService {

    private final QuotationItemService quotationItemService;
    private final QuotationScheduleService scheduleService;

    // 품목을 검증하고 합산 수량으로 납기 미리보기 조회
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
    public QuotationScheduleDto.Response previewSchedule(@NotNull @Valid QuotationScheduleDto.Request request) {
        List<Long> productIds = request.items().stream()
                .map(QuotationScheduleDto.ItemRequest::productId)
                .toList();
        // 품목 검증
        quotationItemService.validateProducts(productIds);

        long totalQuantity = request.items().stream()
                .mapToLong(item -> item.quantity())
                .sum();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        QuotationSchedulePreviewResult result = scheduleService.previewSchedule(
                totalQuantity,
                request.requestedDueDate(),
                today
        );
        return QuotationScheduleDto.Response.from(result, request.requestedDueDate());
    }

}
