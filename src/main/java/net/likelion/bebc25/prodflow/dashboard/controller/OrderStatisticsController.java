package net.likelion.bebc25.prodflow.dashboard.controller;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class OrderStatisticsController {

    private final DashboardService dashboardService;

    // 수주 통계 조회
    @GetMapping("/orders")
    public DashboardDto.OrderStatisticsResponse getOrderStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, // 조회 시작일
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to // 조회 종료일
    ) {
        return dashboardService.getOrderStatistics(from, to);
    }
}
