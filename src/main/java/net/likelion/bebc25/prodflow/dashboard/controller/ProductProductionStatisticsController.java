package net.likelion.bebc25.prodflow.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.service.ProductProductionStatisticsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class ProductProductionStatisticsController {

    private final ProductProductionStatisticsService productProductionStatisticsService;

    @Operation(
            summary = "품목별 생산 비중 통계 조회",
            description = "선택한 월의 품목별 생산 완료 수량과 전체 수량, 품목별 생산 비중을 조회한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "조회 기간 형식 오류 또는 month 누락")
    })
    @GetMapping("/product-production-share")
    public DashboardDto.ProductProductionShareResponse getProductProductionShare(
            @RequestParam(required = false) String month
    ) {
        if (month == null || month.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month는 필수입니다. yyyy-MM 형식으로 입력해주세요.");
        }

        YearMonth yearMonth;

        try {
            yearMonth = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month는 yyyy-MM 형식으로 입력해야 합니다.");
        }

        return productProductionStatisticsService.getProductProductionShare(yearMonth);
    }
}
