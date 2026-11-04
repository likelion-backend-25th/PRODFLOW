package net.likelion.bebc25.prodflow.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.service.ProductionStatisticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics/production")
@RequiredArgsConstructor
public class ProductionStatisticsController {

    private final ProductionStatisticsService productionStatisticsService;

    // 월간 생산량 통계
    @Operation(
            summary = "월간 생산량 통계 조회",
            description = "연도와 월을 입력받아 해당 월의 총 생산 계획량과 생산 완료량을 조회한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 연도 또는 월 입력")
    })
    @GetMapping("/monthly")
    public DashboardDto.MonthlyProductionResponse getMonthlyProduction(
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        return productionStatisticsService.getMonthlyProduction(year, month);
    }

    // 주간 생산량 통계
    @Operation(
            summary = "주간 생산량 통계 조회",
            description = "연도와 월을 입력받아 해당 월의 주간 생산 계획량과 생산 완료량을 조회한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 연도 또는 월 입력")
    })
    @GetMapping("/weekly")
    public List<DashboardDto.WeeklyProductionResponse> getWeeklyProduction(
            @RequestParam("year") int year,
            @RequestParam("month") int month
    ) {
        return productionStatisticsService.getWeeklyProduction(year, month);
    }

    // 일간 생산량 통계
    @Operation(
            summary = "일간 생산량 통계 조회",
            description = "조회 시작일과 종료일을 입력받아 날짜별 생산 계획량과 생산 완료량을 조회한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 날짜 입력")
    })
    @GetMapping("/daily")
    public List<DashboardDto.ProductionResponse> getDailyProduction(
            @RequestParam("startDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @RequestParam("endDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return productionStatisticsService.getDailyProduction(startDate, endDate);
    }
}