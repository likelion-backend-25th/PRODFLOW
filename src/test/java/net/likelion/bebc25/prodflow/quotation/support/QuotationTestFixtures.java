package net.likelion.bebc25.prodflow.quotation.support;

import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationItemDto;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// PDF 서비스와 컨트롤러 테스트에서 사용하는 공통 견적 초안 데이터다.
public final class QuotationTestFixtures {
    private QuotationTestFixtures() {
    }

    public static QuotationDto.DetailResponse draft() {
        var draft = mock(QuotationDto.DetailResponse.class);
        when(draft.id()).thenReturn(123L);
        when(draft.quotationNo()).thenReturn("QT-20261104-123");
        when(draft.status()).thenReturn(QuotationStatus.DRAFT);
        when(draft.createdBy()).thenReturn("작성 직원");
        when(draft.createdAt()).thenReturn(LocalDateTime.of(2026, 11, 4, 12, 0));
        when(draft.validUntil()).thenReturn(LocalDate.of(2026, 11, 18));
        when(draft.clientCompany()).thenReturn("테스트 거래처");
        when(draft.clientName()).thenReturn("담당자");
        when(draft.clientPhone()).thenReturn("02-1234-5678");
        when(draft.clientEmail()).thenReturn("client@example.com");
        when(draft.items()).thenReturn(List.of(
                new QuotationItemDto.Response(1L, 1L, "한글 품목", "기종", 100, 1000L, 110000L)));
        when(draft.supplyAmount()).thenReturn(100000L);
        when(draft.vatAmount()).thenReturn(10000L);
        when(draft.totalAmount()).thenReturn(110000L);
        when(draft.requestedDueDate()).thenReturn(LocalDate.of(2026, 12, 10));
        when(draft.estimatedDueDate()).thenReturn(LocalDate.of(2026, 12, 10));
        return draft;
    }
}
