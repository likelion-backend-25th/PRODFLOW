package net.likelion.bebc25.prodflow.quotation;

import net.likelion.bebc25.prodflow.quotation.dto.QuotationScheduleDto;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationSchedulePreviewResult;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleResult;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationItemService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationSchedulePreviewService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuotationSchedulePreviewServiceTest {
    private final QuotationItemService items = mock(QuotationItemService.class);
    private final QuotationScheduleService schedules = mock(QuotationScheduleService.class);
    private final QuotationSchedulePreviewService service = new QuotationSchedulePreviewService(items, schedules);
    private final QuotationScheduleResult earliest = new QuotationScheduleResult(
            LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5));

    @Test
    void mapsEarliestScheduleAndSumsItemQuantitiesWhenDateIsOmitted() {
        var request = request(null);
        when(schedules.previewSchedule(eq(300L), isNull(), any(LocalDate.class)))
                .thenReturn(new QuotationSchedulePreviewResult(earliest, earliest));

        var response = service.previewSchedule(request);

        verify(items).validateProducts(List.of(1L, 2L));
        assertThat(response.earliestDueDate()).isEqualTo(earliest.estimatedDueDate());
        assertThat(response.requestedDueDate()).isNull();
        assertThat(response.feasible()).isTrue();
        assertThat(response.plannedStartDate()).isEqualTo(earliest.plannedStartDate());
        assertThat(response.plannedEndDate()).isEqualTo(earliest.plannedEndDate());
    }

    @Test
    void mapsImpossibleDateToFalseAndEmptyProductionDates() {
        LocalDate requested = LocalDate.of(2026, 11, 9);
        when(schedules.previewSchedule(eq(300L), eq(requested), any(LocalDate.class)))
                .thenReturn(new QuotationSchedulePreviewResult(earliest, null));

        var response = service.previewSchedule(request(requested));

        assertThat(response.earliestDueDate()).isEqualTo(earliest.estimatedDueDate());
        assertThat(response.requestedDueDate()).isEqualTo(requested);
        assertThat(response.feasible()).isFalse();
        assertThat(response.plannedStartDate()).isNull();
        assertThat(response.plannedEndDate()).isNull();
    }

    @Test
    void mapsSelectedScheduleInsteadOfEarliestSchedule() {
        LocalDate requested = LocalDate.of(2026, 11, 20);
        var selected = new QuotationScheduleResult(requested, requested.minusDays(3), requested.minusDays(3));
        when(schedules.previewSchedule(eq(300L), eq(requested), any(LocalDate.class)))
                .thenReturn(new QuotationSchedulePreviewResult(earliest, selected));

        var response = service.previewSchedule(request(requested));

        assertThat(response.earliestDueDate()).isEqualTo(earliest.estimatedDueDate());
        assertThat(response.requestedDueDate()).isEqualTo(requested);
        assertThat(response.feasible()).isTrue();
        assertThat(response.plannedStartDate()).isEqualTo(selected.plannedStartDate());
        assertThat(response.plannedEndDate()).isEqualTo(selected.plannedEndDate());
    }

    @Test
    void invalidProductsStopScheduleCalculation() {
        when(items.validateProducts(List.of(1L, 2L)))
                .thenThrow(new IllegalArgumentException("생산 중단된 품목은 견적에 추가할 수 없습니다."));

        assertThatThrownBy(() -> service.previewSchedule(request(null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("생산 중단");
        verifyNoInteractions(schedules);
    }

    private QuotationScheduleDto.Request request(LocalDate dueDate) {
        return new QuotationScheduleDto.Request(List.of(
                new QuotationScheduleDto.ItemRequest(1L, 100),
                new QuotationScheduleDto.ItemRequest(2L, 200)), dueDate);
    }
}
