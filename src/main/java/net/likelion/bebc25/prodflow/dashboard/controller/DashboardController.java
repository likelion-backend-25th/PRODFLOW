package net.likelion.bebc25.prodflow.dashboard.controller;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    // 대시보드 통합 조회
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDto.DashboardResponse> getDashboard(
            @ModelAttribute DashboardDto.Request request
    ) {
        return ResponseEntity.ok(dashboardService.getDashboard(request));
    }

}
