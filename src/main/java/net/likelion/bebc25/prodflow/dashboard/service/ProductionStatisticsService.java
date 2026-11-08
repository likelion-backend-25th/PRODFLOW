package net.likelion.bebc25.prodflow.dashboard.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.dashboard.dto.DashboardDto;
import net.likelion.bebc25.prodflow.dashboard.repository.ProductionStatisticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
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

    // 날짜별 생산 계획량 및 완료량 조회
    public List<DashboardDto.ProductionResponse> getDailyProduction(
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateDateRange(startDate, endDate);

        // Repository의 종료일은 미포함이므로 종료일 다음 날 전달
        LocalDate to = endDate.plusDays(1);

        // 일별 계획량 조회
        List<Object[]> plannedRows =
                productionStatisticsRepository.findDailyPlannedQuantity(
                        startDate,
                        to
                );

        // 일별 완료량 조회
        List<Object[]> completedRows =
                productionStatisticsRepository.findDailyCompletedQuantity(
                        startDate,
                        to
                );

        // 날짜별 생산량 집계
        Map<LocalDate, DailyQuantity> dailyMap = new TreeMap<>();

        // 계획량 집계
        for (Object[] row : plannedRows) {
            LocalDate date = toLocalDate(row[0]);
            long quantity = ((Number) row[1]).longValue();

            dailyMap.putIfAbsent(date, new DailyQuantity());
            dailyMap.get(date).plannedQuantity += quantity;
        }

        // 완료량 집계
        for (Object[] row : completedRows) {
            LocalDate date = toLocalDate(row[0]);
            long quantity = ((Number) row[1]).longValue();

            dailyMap.putIfAbsent(date, new DailyQuantity());
            dailyMap.get(date).completedQuantity += quantity;
        }

        // 조회 기간 내 데이터가 없는 날짜도 0으로 포함
        for (LocalDate date = startDate;
             !date.isAfter(endDate);
             date = date.plusDays(1)) {

            dailyMap.putIfAbsent(date, new DailyQuantity());
        }

        // DTO 변환
        return dailyMap.entrySet()
                .stream()
                .map(entry -> new DashboardDto.ProductionResponse(
                        entry.getKey(),
                        entry.getValue().plannedQuantity,
                        entry.getValue().completedQuantity
                ))
                .toList();
    }

    // 선택한 월의 전체 생산량 통계
    public DashboardDto.MonthlyProductionResponse getMonthlyProduction(
            int year,
            int month
    ) {
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

    // 요청 기간에 걸친 달마다 생산량 통계
    public List<DashboardDto.MonthlyProductionResponse> getMonthlyProduction(
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateDateRange(startDate, endDate);

        List<DashboardDto.MonthlyProductionResponse> result = new ArrayList<>();
        YearMonth month = YearMonth.from(startDate);
        YearMonth last = YearMonth.from(endDate);
        while (!month.isAfter(last)) {
            LocalDate monthStart = month.atDay(1);
            LocalDate monthEnd = month.atEndOfMonth();
            LocalDate from = monthStart.isBefore(startDate) ? startDate : monthStart;
            LocalDate to = monthEnd.isAfter(endDate) ? endDate : monthEnd;
            result.add(new DashboardDto.MonthlyProductionResponse(
                    month.getYear(),
                    month.getMonthValue(),
                    productionStatisticsRepository.sumPlannedQuantity(from, to.plusDays(1)),
                    productionStatisticsRepository.sumCompletedQuantity(from, to.plusDays(1))
            ));
            month = month.plusMonths(1);
        }
        return result;
    }

    // 선택한 월의 주차별 생산 계획량 및 완료량
    public List<DashboardDto.WeeklyProductionResponse> getWeeklyProduction(
            int year,
            int month
    ) {
        validateMonth(year, month);

        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        LocalDate to = monthEnd.plusDays(1);

        // 일별 계획량과 완료량 조회
        List<Object[]> plannedRows =
                productionStatisticsRepository.findDailyPlannedQuantity(
                        monthStart,
                        to
                );

        List<Object[]> completedRows =
                productionStatisticsRepository.findDailyCompletedQuantity(
                        monthStart,
                        to
                );

        // 주차별 집계 데이터
        Map<Integer, WeeklyQuantity> weeklyMap = new TreeMap<>();

        // 월의 날짜를 기준으로 주차 초기화
        for (LocalDate date = monthStart;
             !date.isAfter(monthEnd);
             date = date.plusDays(1)) {

            int week = getWeekOfMonth(date, monthStart);

            LocalDate weekStart = date.with(DayOfWeek.MONDAY);
            LocalDate weekEnd = date.with(DayOfWeek.SUNDAY);

            weeklyMap.putIfAbsent(
                    week,
                    new WeeklyQuantity(
                            weekStart.isBefore(monthStart) ? monthStart : weekStart,
                            weekEnd.isAfter(monthEnd) ? monthEnd : weekEnd
                    )
            );
        }

        // 일별 계획량을 주차별로 합산
        for (Object[] row : plannedRows) {
            LocalDate date = toLocalDate(row[0]);
            long quantity = ((Number) row[1]).longValue();

            int week = getWeekOfMonth(date, monthStart);
            weeklyMap.get(week).plannedQuantity += quantity;
        }

        // 일별 완료량을 주차별로 합산
        for (Object[] row : completedRows) {
            LocalDate date = toLocalDate(row[0]);
            long quantity = ((Number) row[1]).longValue();

            int week = getWeekOfMonth(date, monthStart);
            weeklyMap.get(week).completedQuantity += quantity;
        }

        // DTO 변환
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

        return (int) ChronoUnit.WEEKS.between(
                firstMonday,
                date.with(DayOfWeek.MONDAY)
        ) + 1;
    }

    // 조회 기간 유효성 검사
    private void validateDateRange(
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("시작일과 종료일은 필수입니다.");
        }

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 늦을 수 없습니다.");
        }
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

    // Query 결과의 날짜 타입 변환
    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }

        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }

        if (value instanceof java.time.LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }

        throw new IllegalArgumentException("지원하지 않는 날짜 타입입니다: " + value.getClass().getName());
    }

    // 일별 집계를 위한 내부 클래스
    private static class DailyQuantity {
        private long plannedQuantity;
        private long completedQuantity;
    }

    // 주차별 집계를 위한 내부 클래스
    private static class WeeklyQuantity {

        private final LocalDate startDate;
        private final LocalDate endDate;
        private long plannedQuantity;
        private long completedQuantity;

        private WeeklyQuantity(
                LocalDate startDate,
                LocalDate endDate
        ) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }
}