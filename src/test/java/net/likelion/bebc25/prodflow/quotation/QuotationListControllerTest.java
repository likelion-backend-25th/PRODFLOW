package net.likelion.bebc25.prodflow.quotation;

import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.global.exception.GlobalExceptionHandler;
import net.likelion.bebc25.prodflow.quotation.controller.QuotationController;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationSortType;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.service.QuotationService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationPdfStorageService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationSchedulePreviewService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationRescheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QuotationListControllerTest {
    private final QuotationService service = mock(QuotationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new QuotationController(service,
                        mock(QuotationSchedulePreviewService.class), mock(QuotationRescheduleService.class), mock(QuotationPdfStorageService.class), null, null))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(service.getQuotationList(nullable(String.class), nullable(QuotationStatus.class),
                any(QuotationSortType.class), any(Pageable.class), anyBoolean()))
                .thenReturn(new PageResponse<>(List.of(), 0, 12, 0, 0));
    }

    @Test
    void defaultsToCreatedAtWhenSortTypeIsOmitted() throws Exception {
        mvc.perform(get("/api/v1/quotations"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        verify(service).getQuotationList(isNull(), isNull(), eq(QuotationSortType.CREATED_AT),
                argThat(page -> page.getPageNumber() == 0 && page.getPageSize() == 12), eq(false));
    }

    @Test
    void acceptsEachSortTypeWithKeywordAndPagination() throws Exception {
        for (QuotationSortType type : QuotationSortType.values()) {
            mvc.perform(get("/api/v1/quotations").param("sortType", type.name())
                            .param("keyword", "한빛").param("page", "1").param("size", "5"))
                    .andExpect(status().isOk());
            verify(service).getQuotationList(eq("한빛"), isNull(), eq(type),
                    argThat(page -> page.getPageNumber() == 1 && page.getPageSize() == 5), eq(false));
        }
    }

    @Test
    void acceptsEveryStatusTogetherWithEverySortType() throws Exception {
        for (QuotationStatus quotationStatus : QuotationStatus.values()) {
            for (QuotationSortType sortType : QuotationSortType.values()) {
                mvc.perform(get("/api/v1/quotations").param("status", quotationStatus.name())
                                .param("sortType", sortType.name()).param("keyword", "한빛"))
                        .andExpect(status().isOk());
                verify(service).getQuotationList(eq("한빛"), eq(quotationStatus), eq(sortType), any(Pageable.class), eq(false));
            }
        }
    }

    @Test
    void invalidStatusReturns400WithoutCallingService() throws Exception {
        for (String invalid : List.of("UNKNOWN", "approved")) {
            mvc.perform(get("/api/v1/quotations").param("status", invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.errors").isEmpty())
                    .andExpect(jsonPath("$.message").value("status 값은 DRAFT, SENT, APPROVED, REJECTED, CANCELLED, EXPIRED 중 하나여야 합니다."));
        }
        verifyNoInteractions(service);
    }

    @Test
    void emptyAndWhitespaceStatusUseAllStatuses() throws Exception {
        for (String value : List.of("", " ", "\t")) {
            mvc.perform(get("/api/v1/quotations").param("status", value))
                    .andExpect(status().isOk());
        }
        verify(service, times(3)).getQuotationList(isNull(), isNull(), eq(QuotationSortType.CREATED_AT), any(Pageable.class), eq(false));
    }

    @Test
    void invalidSortTypeReturns400WithoutCallingService() throws Exception {
        mvc.perform(get("/api/v1/quotations").param("sortType", "UNKNOWN"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.message").value("sortType 값은 CREATED_AT, REQUESTED_DUE_DATE, ESTIMATED_DUE_DATE, VALID_UNTIL 중 하나여야 합니다."));
        verifyNoInteractions(service);
    }

    @Test
    void emptySortTypeUsesDefault() throws Exception {
        mvc.perform(get("/api/v1/quotations").param("sortType", ""))
                .andExpect(status().isOk());
        verify(service).getQuotationList(isNull(), isNull(), eq(QuotationSortType.CREATED_AT), any(Pageable.class), eq(false));
    }

    @Test
    void orderPendingKeepsKeywordStatusSortAndPagination() throws Exception {
        for (QuotationSortType type : QuotationSortType.values()) {
            mvc.perform(get("/api/v1/quotations").param("orderPending", "true")
                            .param("status", "APPROVED").param("keyword", "한빛")
                            .param("sortType", type.name()).param("page", "1").param("size", "5"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
            verify(service).getQuotationList(eq("한빛"), eq(QuotationStatus.APPROVED), eq(type),
                    argThat(page -> page.getPageNumber() == 1 && page.getPageSize() == 5), eq(true));
        }
    }

    @Test
    void invalidOrderPendingReturns400WithoutCallingService() throws Exception {
        mvc.perform(get("/api/v1/quotations").param("orderPending", "invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void invalidQuotationIdReturns400WithoutCallingService() throws Exception {
        mvc.perform(get("/api/v1/quotations/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.message").value("quotationId 파라미터 형식을 확인해주세요."));
        verifyNoInteractions(service);
    }
}
