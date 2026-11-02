package net.likelion.bebc25.prodflow.quotation;

import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.global.exception.GlobalExceptionHandler;
import net.likelion.bebc25.prodflow.global.security.jwt.JwtProvider;
import net.likelion.bebc25.prodflow.quotation.controller.QuotationController;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationSortType;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.service.QuotationService;
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
        mvc = MockMvcBuilders.standaloneSetup(new QuotationController(service, mock(JwtProvider.class)))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(service.getQuotationList(nullable(String.class), nullable(QuotationStatus.class),
                any(QuotationSortType.class), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 12, 0, 0));
    }

    @Test
    void defaultsToCreatedAtWhenSortTypeIsOmitted() throws Exception {
        mvc.perform(get("/api/v1/quotations"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        verify(service).getQuotationList(isNull(), isNull(), eq(QuotationSortType.CREATED_AT),
                argThat(page -> page.getPageNumber() == 0 && page.getPageSize() == 12));
    }

    @Test
    void acceptsEachSortTypeWithKeywordAndPagination() throws Exception {
        for (QuotationSortType type : QuotationSortType.values()) {
            mvc.perform(get("/api/v1/quotations").param("sortType", type.name())
                            .param("keyword", "한빛").param("page", "1").param("size", "5"))
                    .andExpect(status().isOk());
            verify(service).getQuotationList(eq("한빛"), isNull(), eq(type),
                    argThat(page -> page.getPageNumber() == 1 && page.getPageSize() == 5));
        }
    }

    @Test
    void acceptsEveryStatusTogetherWithEverySortType() throws Exception {
        for (QuotationStatus quotationStatus : QuotationStatus.values()) {
            for (QuotationSortType sortType : QuotationSortType.values()) {
                mvc.perform(get("/api/v1/quotations").param("status", quotationStatus.name())
                                .param("sortType", sortType.name()).param("keyword", "한빛"))
                        .andExpect(status().isOk());
                verify(service).getQuotationList(eq("한빛"), eq(quotationStatus), eq(sortType), any(Pageable.class));
            }
        }
    }

    @Test
    void invalidStatusReturns400WithoutCallingService() throws Exception {
        for (String invalid : List.of("UNKNOWN", "approved", "")) {
            mvc.perform(get("/api/v1/quotations").param("status", invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HTTP_400"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void invalidSortTypeReturns400WithoutCallingService() throws Exception {
        mvc.perform(get("/api/v1/quotations").param("sortType", "UNKNOWN"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HTTP_400"));
        verifyNoInteractions(service);
    }
}
