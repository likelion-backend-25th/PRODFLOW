package net.likelion.bebc25.prodflow.dashboard.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.repository.ProductionStatisticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductionStatisticsService {

    private final ProductionStatisticsRepository productionStatisticsRepository;

    // 선택한 월의 전체 생산량 통계
    public DashboardDto.MonthlyProductionResponse getMonthlyProduction(
            int year, int month) {

        validateMonth(year, month);

        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.plusMonths(1);

        long plannedQuantity =
                productionStatisticsRepository.sumPlannedQuantity(from, to);

        long completedQuantity =
                productionStatisticsRepository.sumCompletedQuantity(from, to);

        return new DashboardDto.MonthlyProductionResponse(
                year,
                month,
                plannedQuantity,
                completedQuantity
        );
    }

    // 선택한 월의 주차별 생산 계획량 및 완료량
    public List<DashboardDto.WeeklyProductionResponse> getWeeklyProduction(
            int year, int month) {

        validateMonth(year, month);

        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        LocalDate to = monthEnd.plusDays(1);

        // 일별 계획량과 완료량 조회
        List<Object[]> plannedRows =
                productionStatisticsRepository.findDailyPlannedQuantity(monthStart, to);

        List<Object[]> completedRows =
                productionStatisticsRepository.findDailyCompletedQuantity(monthStart, to);

        // 주차별 집계 데이터
        Map<Integer, WeeklyQuantity> weeklyMap = new TreeMap<>();

        // 월의 날짜를 기준으로 주차 초기화
        for (LocalDate date = monthStart; !date.isAfter(monthEnd); date = date.plusDays(1)) {
            int week = getWeekOfMonth(date, monthStart);

            weeklyMap.putIfAbsent(
                    week,
                    new WeeklyQuantity(
                            date.with(DayOfWeek.MONDAY).isBefore(monthStart)
                                    ? monthStart
                                    : date.with(DayOfWeek.MONDAY),
                            date.with(DayOfWeek.SUNDAY).isAfter(monthEnd)
                                    ? monthEnd
                                    : date.with(DayOfWeek.SUNDAY)
                    )
            );
        }

        // 일별 계획량을 주차별로 합산
        for (Object[] row : plannedRows) {
            LocalDate date = (LocalDate) row[0];
            long quantity = ((Number) row[1]).longValue();

            int week = getWeekOfMonth(date, monthStart);
            weeklyMap.get(week).plannedQuantity += quantity;
        }

        // 일별 완료량을 주차별로 합산
        for (Object[] row : completedRows) {
            LocalDate date = (LocalDate) row[0];
            long quantity = ((Number) row[1]).longValue();

            int week = getWeekOfMonth(date, monthStart);
            weeklyMap.get(week).completedQuantity += quantity;
        }

        // DTO로 변환
        List<DashboardDto.WeeklyProductionResponse> result = new ArrayList<>();

        weeklyMap.forEach((week, quantity) ->
                result.add(new DashboardDto.WeeklyProductionResponse(
                        week,
                        quantity.startDate,
                        quantity.endDate,
                        quantity.plannedQuantity,
                        quantity.completedQuantity
                ))
        );

        return result;
    }

    // 월요일 시작 기준으로 해당 월의 몇 번째 주인지 계산
    private int getWeekOfMonth(LocalDate date, LocalDate monthStart) {
        LocalDate firstMonday = monthStart.with(DayOfWeek.MONDAY);

        if (firstMonday.isAfter(monthStart)) {
            firstMonday = firstMonday.minusWeeks(1);
        }

        return (int) (ChronoUnit.WEEKS.between(firstMonday, date.with(DayOfWeek.MONDAY))) + 1;
    }

    // 연도와 월 유효성 검사
    private void validateMonth(int year, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("1월부터 12월까지 입력 가능합니다.");
        }

        if (year < 1) {
            throw new IllegalArgumentException("올바른 연도를 입력해야 합니다.");
        }
    }

    // 주차별 집계를 위한 내부 클래스
    private static class WeeklyQuantity {
        private final LocalDate startDate;
        private final LocalDate endDate;
        private long plannedQuantity;
        private long completedQuantity;

        private WeeklyQuantity(LocalDate startDate, LocalDate endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }
}