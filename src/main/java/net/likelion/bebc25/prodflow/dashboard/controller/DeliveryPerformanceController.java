package net.likelion.bebc25.prodflow.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.service.DeliveryPerformanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class DeliveryPerformanceController {

    private final DeliveryPerformanceService deliveryPerformanceService;

    // 납기 이행률 통계 조회
    @Operation(
            summary = "납기 이행률 통계 조회",
            description = "납기일 기준으로 집계 대상 수주 건수와 납기 내 완료된 수주 건수를 비교하여 납기 이행률을 조회한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 조회 기간 입력")
    })
    @GetMapping("/delivery-performance")
    public DashboardDto.DeliveryPerformanceResponse getDeliveryPerformance(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return deliveryPerformanceService.getDeliveryPerformance(from, to);
    }
}