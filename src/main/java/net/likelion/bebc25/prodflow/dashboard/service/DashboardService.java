package net.likelion.bebc25.prodflow.dashboard.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.repository.OrderStatisticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {

    private final OrderStatisticsRepository orderStatisticsRepository;

    // 수주 건주 통계 조회
    public DashboardDto.OrderStatisticsResponse getOrderStatistics(
            LocalDate startDate,
            LocalDate endDate
    ) {
        // 조회 기간 검증
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("조회 시작일과 종료일은 필수입니다.");
        }

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("조회 시작일은 종료일보다 늦을 수 없습니다.");
        }

        // 전체 수주 건수 조회
        long totalOrderCount = orderStatisticsRepository.countAllOrders();

        // 조회 기간의 시작일, 종료일 설정
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        // 조회 기간별 수주 건수 조회
        long periodOrderCount = orderStatisticsRepository.countOrdersByPeriod(
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        );

        // 조회 결과를 DTO로 변환하여 반환
        return new DashboardDto.OrderStatisticsResponse(
                totalOrderCount,
                periodOrderCount
        );
    }

}
