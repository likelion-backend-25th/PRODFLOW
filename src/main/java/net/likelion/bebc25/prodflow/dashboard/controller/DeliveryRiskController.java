package net.likelion.bebc25.prodflow.dashboard.controller;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.service.DeliveryRiskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class DeliveryRiskController {

    private final DeliveryRiskService deliveryRiskService;

    //납기 위험 현황 조회
    @GetMapping("/delivery-risks")
    public ResponseEntity<DashboardDto.DeliveryRiskStatisticsResponse> getDeliveryRisk() {
        return ResponseEntity.ok(deliveryRiskService.getDeliveryRisks());
    }
}
