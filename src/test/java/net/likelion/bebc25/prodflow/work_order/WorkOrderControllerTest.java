package net.likelion.bebc25.prodflow.work_order;

import net.likelion.bebc25.prodflow.global.exception.GlobalExceptionHandler;
import net.likelion.bebc25.prodflow.common.service.StatusHistoryService;
import net.likelion.bebc25.prodflow.work_order.controller.WorkOrderController;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto;
import net.likelion.bebc25.prodflow.work_order.service.WorkOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkOrderControllerTest {
    private final WorkOrderService workOrderService = mock(WorkOrderService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new WorkOrderController(
                        workOrderService, mock(StatusHistoryService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("수주 필터와 기존 검색 조건을 전달하고 추가 생산 정보를 응답한다")
    void getPageByOrderId() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 26);
        WorkOrderDto.PageResponse row = new WorkOrderDto.PageResponse(1L, "WI-001", "PF-001",
                "거래처", "품목", 100, "A라인", null, date, date, "PACK", 31L, date, date);
        when(workOrderService.findAll(eq(123L), eq(2L), eq("PACK"), eq(date), eq(date), eq("품목"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 5), 1));

        mvc.perform(get("/api/v1/work-orders").param("orderId", "123").param("lineId", "2")
                        .param("status", "PACK").param("from", "2026-10-26").param("to", "2026-10-26")
                        .param("keyword", "품목"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.content[0].orderProductId").value(31))
                .andExpect(jsonPath("$.content[0].plannedStartDate").value("2026-10-26"))
                .andExpect(jsonPath("$.content[0].completedAt").value("2026-10-26"));
        verify(workOrderService).findAll(eq(123L), eq(2L), eq("PACK"), eq(date), eq(date), eq("품목"), isNull(),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 5));
    }

    @Test
    @DisplayName("수주 ID를 생략해도 기존 목록 조회와 페이지 조건을 유지한다")
    void getPageWithoutOrderId() throws Exception {
        when(workOrderService.findAll(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 2), 0));

        mvc.perform(get("/api/v1/work-orders").param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        verify(workOrderService).findAll(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                argThat(pageable -> pageable.getPageNumber() == 1 && pageable.getPageSize() == 2));
    }

    @Test
    @DisplayName("없는 수주 ID는 404로 응답한다")
    void unknownOrderReturnsNotFound() throws Exception {
        when(workOrderService.findAll(eq(Long.MAX_VALUE), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenThrow(new NoSuchElementException("수주를 찾을 수 없습니다."));

        mvc.perform(get("/api/v1/work-orders").param("orderId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("수주를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("숫자가 아닌 수주 ID는 서비스 호출 전에 거부한다")
    void rejectInvalidOrderId() throws Exception {
        mvc.perform(get("/api/v1/work-orders").param("orderId", "invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(workOrderService);
    }
}
