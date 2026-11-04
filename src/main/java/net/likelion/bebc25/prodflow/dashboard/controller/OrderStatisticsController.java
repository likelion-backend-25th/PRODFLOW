package net.likelion.bebc25.prodflow.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.service.OrderStatisticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class OrderStatisticsController {

    private final OrderStatisticsService orderStatisticsService;

    // 수주 건수 통계 조회
    @Operation(
            summary = "수주 건수 통계 조회",
            description = "조회 시작일과 종료일을 입력받아 취소 수주를 제외한 해당 기간의 수주 건수 통계를 조회한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 날짜 형식 또는 조회 기간 입력")
    })
    @GetMapping("/orders")
    public DashboardDto.OrderStatisticsResponse getOrderStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return orderStatisticsService.getOrderStatistics(from, to);
    }

    // 수주 상태별 현황 조회
    @Operation(
            summary = "수주 상태별 현황 조회",
            description = "수주 상태별 건수를 집계하여 상태별 현황을 조회한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/order-status")
    public List<DashboardDto.OrderStatusStatisticsResponse> getOrderStatusStatistics() {
        return orderStatisticsService.getOrderStatusStatistics();
    }
}
