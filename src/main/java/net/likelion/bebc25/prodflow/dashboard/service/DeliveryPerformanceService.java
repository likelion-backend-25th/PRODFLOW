package net.likelion.bebc25.prodflow.dashboard.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.repository.DeliveryPerformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DeliveryPerformanceService {

    private final DeliveryPerformanceRepository deliveryPerformanceRepository;

    // 납기 이행률 조회
    public DashboardDto.DeliveryPerformanceResponse getDeliveryPerformance(
            LocalDate from,
            LocalDate to
    ) {
        validateDateRange(from, to);

        long totalOrderCount = deliveryPerformanceRepository.countTargetOrders(from, to);
        long onTimeOrderCount = deliveryPerformanceRepository.countOnTimeOrders(from, to);

        double onTimeDeliveryRate = totalOrderCount == 0 ? 0.0 : (double) onTimeOrderCount / totalOrderCount * 100;

        onTimeDeliveryRate = Math.round(onTimeDeliveryRate * 100.0) / 100.0;

        return new DashboardDto.DeliveryPerformanceResponse(
                totalOrderCount,
                onTimeOrderCount,
                onTimeDeliveryRate
        );

    }

    // 조회 기간 유효성 검사
    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("조회일과 종료일을 입력해야합니다.");
        }

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("조회일은 종료일보다 늦을 수 없습니다.");
        }
    }


}
