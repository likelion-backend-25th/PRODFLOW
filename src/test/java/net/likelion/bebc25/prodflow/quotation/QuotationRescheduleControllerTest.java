package net.likelion.bebc25.prodflow.quotation;

import net.likelion.bebc25.prodflow.global.exception.GlobalExceptionHandler;
import net.likelion.bebc25.prodflow.quotation.controller.QuotationController;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationRescheduleDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationScheduleDto;
import net.likelion.bebc25.prodflow.quotation.service.QuotationRescheduleService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationSchedulePreviewService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationPdfStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QuotationRescheduleControllerTest {
    private final QuotationRescheduleService service = mock(QuotationRescheduleService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new QuotationController(mock(QuotationService.class),
                        mock(QuotationSchedulePreviewService.class), service, mock(QuotationPdfStorageService.class), null, null))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void previewAllowsOmittedDateAndPassesQuotationId() throws Exception {
        LocalDate due = LocalDate.of(2026, 12, 1);
        when(service.previewSchedule(eq(7L), any()))
                .thenReturn(new QuotationScheduleDto.Response(due, null, true, due.minusDays(7), due.minusDays(3)));

        mvc.perform(post("/api/v1/quotations/7/schedule").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earliestDueDate").value("2026-12-01"))
                .andExpect(jsonPath("$.feasible").value(true));
        verify(service).previewSchedule(7L, new QuotationRescheduleDto.PreviewRequest(null));
    }

    @Test
    void updateBindsDateAndIdToUpdateRequest() throws Exception {
        mvc.perform(patch("/api/v1/quotations/7/schedule").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedDueDate\":\"2026-12-01\"}"))
                .andExpect(status().isOk());
        verify(service).updateSchedule(7L, new QuotationRescheduleDto.UpdateRequest(LocalDate.of(2026, 12, 1)));
    }

    @Test
    void updateRejectsOmittedNullAndInvalidDate() throws Exception {
        for (String body : List.of("{}", "{\"requestedDueDate\":null}", "{\"requestedDueDate\":\"invalid\"}")) {
            mvc.perform(patch("/api/v1/quotations/7/schedule").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void previewRejectsMalformedDateWithoutCallingService() throws Exception {
        mvc.perform(post("/api/v1/quotations/7/schedule").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedDueDate\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        verifyNoInteractions(service);
    }

    @Test
    void missingQuotationReturns404ForBothEndpoints() throws Exception {
        when(service.previewSchedule(eq(7L), any())).thenThrow(new NoSuchElementException("존재하지 않는 견적"));
        when(service.updateSchedule(eq(7L), any())).thenThrow(new NoSuchElementException("존재하지 않는 견적"));
        mvc.perform(post("/api/v1/quotations/7/schedule").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(patch("/api/v1/quotations/7/schedule").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedDueDate\":\"2026-12-01\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void ineligibleQuotationReturnsCommon400Response() throws Exception {
        when(service.previewSchedule(eq(7L), any())).thenThrow(new IllegalArgumentException("재산정 불가"));
        when(service.updateSchedule(eq(7L), any())).thenThrow(new IllegalArgumentException("재산정 불가"));
        mvc.perform(post("/api/v1/quotations/7/schedule").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        mvc.perform(patch("/api/v1/quotations/7/schedule").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedDueDate\":\"2026-12-01\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }
}
