package net.likelion.bebc25.prodflow.quotation.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationRescheduleDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationScheduleDto;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.entity.QuotationItem;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationItemRepository;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationSchedulePreviewResult;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleResult;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Validated
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
@RequiredArgsConstructor
public class QuotationRescheduleService {
    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository quotationItemRepository;
    private final QuotationScheduleService scheduleService;
    private final QuotationService quotationService;

    // 저장된 품목 수량을 기준으로 기존 견적의 납기 미리보기 조회
    public QuotationScheduleDto.Response previewSchedule(
            Long quotationId, @NotNull @Valid QuotationRescheduleDto.PreviewRequest request
    ) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Quotation quotation = findEligibleQuotation(quotationId, today);
        long totalQuantity = totalQuantity(quotation.getId());

        QuotationSchedulePreviewResult result = scheduleService.previewSchedule(
                totalQuantity, request.requestedDueDate(), today);
        return QuotationScheduleDto.Response.from(result, request.requestedDueDate());
    }

    // 최신 생산 조건으로 재검증한 뒤 견적의 날짜 네 개만 변경
    @Transactional
    public QuotationDto.DetailResponse updateSchedule(
            Long quotationId, @NotNull @Valid QuotationRescheduleDto.UpdateRequest request
    ) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Quotation quotation = findEligibleQuotation(quotationId, today);
        long totalQuantity = totalQuantity(quotation.getId());
        QuotationScheduleResult schedule = scheduleService.calculate(totalQuantity, request.requestedDueDate(), today);

        quotation.changeSchedule(request.requestedDueDate(), schedule.estimatedDueDate(),
                schedule.plannedStartDate(), schedule.plannedEndDate());
        quotationRepository.flush();
        return quotationService.getQuotationById(quotationId);
    }

    // 발송완료 상태와 실제 유효기간을 모두 확인하여 재산정 대상 조회
    private Quotation findEligibleQuotation(Long quotationId, LocalDate today) {
        Quotation quotation = quotationRepository.findDetailById(quotationId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 견적서입니다. ID: " + quotationId));
        if (quotation.getStatus() != QuotationStatus.SENT) {
            throw new IllegalArgumentException("발송완료 상태의 견적만 납기를 재산정할 수 있습니다.");
        }
        if (quotation.getValidUntil().isBefore(today)) {
            throw new IllegalArgumentException("유효기간이 지난 견적은 납기를 재산정할 수 없습니다.");
        }
        return quotation;
    }

    // 현재 제품 정보 대신 견적에 저장된 품목 수량 사용
    private long totalQuantity(Long quotationId) {
        List<QuotationItem> items = quotationItemRepository.findByQuotationIdOrderByIdAsc(quotationId);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("견적 품목이 없어 납기를 재산정할 수 없습니다.");
        }
        return items.stream().mapToLong(QuotationItem::getQuantity).sum();
    }
}
