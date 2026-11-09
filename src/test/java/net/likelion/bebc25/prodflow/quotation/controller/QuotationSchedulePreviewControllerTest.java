package net.likelion.bebc25.prodflow.quotation.controller;

import net.likelion.bebc25.prodflow.quotation.schedule.QuotationSchedulePreviewResult;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleResult;
import net.likelion.bebc25.prodflow.quotation.schedule.QuotationScheduleService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationItemService;
import net.likelion.bebc25.prodflow.quotation.service.QuotationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class QuotationSchedulePreviewControllerTest {
    private static final String URL = "/api/v1/quotations/schedule";
    private static final String ITEMS = "\"items\":[{\"productId\":1,\"quantity\":100},{\"productId\":2,\"quantity\":200}]";
    private final QuotationScheduleResult earliest = new QuotationScheduleResult(
            LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 9));

    @Autowired MockMvc mvc;
    @MockitoBean QuotationItemService items;
    @MockitoBean QuotationScheduleService schedules;
    @MockitoBean QuotationService quotations;

    @BeforeEach
    void setUp() {
        when(schedules.previewSchedule(anyLong(), nullable(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new QuotationSchedulePreviewResult(earliest, earliest));
    }

    @Test
    void allowedRolesCanPreviewEarliestScheduleWithoutCreatingQuotation() throws Exception {
        for (String role : List.of("SUPER_ADMIN", "ORDER_MANAGER")) {
            mvc.perform(post(URL).with(user("관리자").roles(role))
                            .contentType(MediaType.APPLICATION_JSON).content("{" + ITEMS + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.earliestDueDate").value("2026-11-12"))
                    .andExpect(jsonPath("$.requestedDueDate").doesNotExist())
                    .andExpect(jsonPath("$.feasible").value(true))
                    .andExpect(jsonPath("$.plannedStartDate").value("2026-11-05"))
                    .andExpect(jsonPath("$.plannedEndDate").value("2026-11-09"));
        }
        verify(items, times(2)).validateProducts(List.of(1L, 2L));
        verify(schedules, times(2)).previewSchedule(eq(300L), isNull(), any(LocalDate.class));
        verifyNoInteractions(quotations);
    }

    @Test
    void selectedDateIsPassedToCalculationAndItsScheduleIsReturned() throws Exception {
        LocalDate requested = LocalDate.of(2026, 11, 20);
        var selected = new QuotationScheduleResult(requested, requested.minusDays(3), requested.minusDays(3));
        when(schedules.previewSchedule(eq(300L), eq(requested), any(LocalDate.class)))
                .thenReturn(new QuotationSchedulePreviewResult(earliest, selected));

        mvc.perform(post(URL).with(user("수주관리자").roles("ORDER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + ITEMS + ",\"requestedDueDate\":\"2026-11-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earliestDueDate").value("2026-11-12"))
                .andExpect(jsonPath("$.requestedDueDate").value("2026-11-20"))
                .andExpect(jsonPath("$.feasible").value(true))
                .andExpect(jsonPath("$.plannedStartDate").value("2026-11-17"));
    }

    @Test
    void impossibleDateReturns200WithFalseAndNoProductionSchedule() throws Exception {
        LocalDate requested = LocalDate.of(2026, 11, 10);
        when(schedules.previewSchedule(eq(300L), eq(requested), any(LocalDate.class)))
                .thenReturn(new QuotationSchedulePreviewResult(earliest, null));

        mvc.perform(post(URL).with(user("수주관리자").roles("ORDER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + ITEMS + ",\"requestedDueDate\":\"2026-11-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earliestDueDate").value("2026-11-12"))
                .andExpect(jsonPath("$.feasible").value(false))
                .andExpect(jsonPath("$.plannedStartDate").doesNotExist())
                .andExpect(jsonPath("$.plannedEndDate").doesNotExist());
    }

    @Test
    void invalidItemsAreRejectedBeforeProductLookupAndCalculation() throws Exception {
        for (String body : List.of("{}", "{\"items\":[]}", "{\"items\":[null]}",
                "{\"items\":[{\"productId\":1}]}", "{\"items\":[{\"quantity\":100}]}",
                "{\"items\":[{\"productId\":0,\"quantity\":100}]}",
                "{\"items\":[{\"productId\":1,\"quantity\":99}]}")) {
            mvc.perform(post(URL).with(user("수주관리자").roles("ORDER_MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        }
        verifyNoInteractions(items, schedules, quotations);
    }

    @Test
    void malformedDateAndJsonReturnCommon400Response() throws Exception {
        for (String body : List.of("{", "{" + ITEMS + ",\"requestedDueDate\":\"invalid\"}")) {
            mvc.perform(post(URL).with(user("수주관리자").roles("ORDER_MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        }
        verifyNoInteractions(items, schedules);
    }

    @Test
    void productValidationFailureReturns400WithoutCalculating() throws Exception {
        when(items.validateProducts(List.of(1L, 2L))).thenThrow(new IllegalArgumentException("생산품목을 찾을 수 없습니다."));

        mvc.perform(post(URL).with(user("수주관리자").roles("ORDER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{" + ITEMS + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        verifyNoInteractions(schedules, quotations);
    }

    @Test
    void rejectsAnonymousAndUnauthorizedRoles() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{" + ITEMS + "}"))
                .andExpect(status().isUnauthorized());
        for (String role : List.of("PRODUCTION_MANAGER", "INQUIRY_MANAGER")) {
            mvc.perform(post(URL).with(user("직원").roles(role))
                            .contentType(MediaType.APPLICATION_JSON).content("{" + ITEMS + "}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN_OPERATION"));
        }
        verifyNoInteractions(items, schedules, quotations);
    }
}
