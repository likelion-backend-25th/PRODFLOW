package net.likelion.bebc25.prodflow.quotation.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// 날짜별 예약 수량 관리 및 영업일 계산
class ProductionCalendar {
    private final long dailyCapacity;
    private final Set<LocalDate> holidays;
    private final LocalDate calculationLimit;
    private final Map<LocalDate, Long> reservedQuantityByDate = new HashMap<>();

    // 일일 생산능력·휴일·계산 가능 마지막 날짜로 계산용 캘린더 초기화
    ProductionCalendar(long dailyCapacity, Set<LocalDate> holidays, LocalDate calculationLimit) {
        this.dailyCapacity = dailyCapacity;
        this.holidays = holidays;
        this.calculationLimit = calculationLimit;
    }

    // 주말과 공휴일을 제외한 영업일 여부 확인
    boolean isWorkingDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(date);
    }

    // 입력 날짜를 포함하여 가장 가까운 이후 영업일 조회
    LocalDate nextWorkingDay(LocalDate date) {
        checkCalculationLimit(date);
        while (!isWorkingDay(date)) {
            date = date.plusDays(1);
            checkCalculationLimit(date);
        }
        return date;
    }

    // 기준일 다음 날부터 영업일만 세어 지정한 일수만큼 날짜 증가
    LocalDate addWorkingDays(LocalDate date, int days) {
        checkCalculationLimit(date);
        int countedDays = 0;
        while (countedDays < days) {
            date = date.plusDays(1);
            checkCalculationLimit(date);
            if (isWorkingDay(date)) {
                countedDays++;
            }
        }
        return date;
    }

    // 기준일 전날부터 영업일만 세어 지정한 일수만큼 날짜 감소
    LocalDate subtractWorkingDays(LocalDate date, int days) {
        int countedDays = 0;
        while (countedDays < days) {
            date = date.minusDays(1);
            if (isWorkingDay(date)) {
                countedDays++;
            }
        }
        return date;
    }

    // 계산용 캘린더의 해당 날짜에 예약된 생산 수량 누적
    void reserve(LocalDate date, long quantity) {
        long reservedQuantity = reservedQuantityByDate.getOrDefault(date, 0L);
        reservedQuantityByDate.put(date, Math.addExact(reservedQuantity, quantity));
    }

    // 영업일의 일일 생산능력에서 예약 수량을 뺀 가용 생산량 조회
    private long availableQuantity(LocalDate date) {
        if (!isWorkingDay(date)) {
            return 0;
        }
        long reservedQuantity = reservedQuantityByDate.getOrDefault(date, 0L);
        return Math.max(0, dailyCapacity - reservedQuantity);
    }

    // 포장·배송 기간을 제외한 납기 기준으로 생산 일정을 배정하고 불가능하면 가장 빠른 일정 반환
    Map<LocalDate, Long> planProduction(long quantity, LocalDate firstDate, LocalDate dueDate) {
        return planProduction(quantity, firstDate, dueDate, planFromStart(quantity, firstDate));
    }

    // 이미 계산한 가장 빠른 일정을 재사용하여 선택한 납기에 맞는 일정 계산
    Map<LocalDate, Long> planProduction(long quantity, LocalDate firstDate, LocalDate dueDate,
                                      Map<LocalDate, Long> earliestPlan) {
        checkCalculationLimit(dueDate);
        LocalDate earliestEndDate = Collections.max(earliestPlan.keySet());
        // 휴일 납기를 직전 영업일로 보정
        LocalDate deliveryDate = dueDate;
        while (!isWorkingDay(deliveryDate)) {
            deliveryDate = deliveryDate.minusDays(1);
        }
        LocalDate deadline = subtractWorkingDays(deliveryDate, 3);
        if (deadline.isBefore(earliestEndDate)) {
            return earliestPlan;
        }
        checkCalculationLimit(deadline);

        // 납기 역순 배정으로 앞 날짜 생산 여유 확보
        return planFromDeadline(quantity, firstDate, deadline, earliestPlan);
    }

    // 시작일부터 날짜별 가용 생산량을 순서대로 채워 가장 빠른 생산 일정 계산
    Map<LocalDate, Long> planFromStart(long quantity, LocalDate firstDate) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("생산 수량은 양수여야 합니다.");
        }
        Map<LocalDate, Long> plan = new LinkedHashMap<>();
        long remainingQuantity = quantity;
        LocalDate date = firstDate;
        while (remainingQuantity > 0) {
            checkCalculationLimit(date);
            long plannedQuantity = Math.min(remainingQuantity, availableQuantity(date));
            if (plannedQuantity > 0) {
                plan.put(date, plannedQuantity);
                remainingQuantity -= plannedQuantity;
            }
            date = date.plusDays(1);
        }
        return plan;
    }

    // 생산 마감일부터 역순으로 수량을 배정하고 부족하면 가장 빠른 일정 반환
    private Map<LocalDate, Long> planFromDeadline(long quantity, LocalDate firstDate,
                                                  LocalDate deadline, Map<LocalDate, Long> earliestPlan) {
        Map<LocalDate, Long> plan = new LinkedHashMap<>();
        long remainingQuantity = quantity;
        LocalDate date = deadline;
        while (!date.isBefore(firstDate) && remainingQuantity > 0) {
            long plannedQuantity = Math.min(remainingQuantity, availableQuantity(date));
            if (plannedQuantity > 0) {
                plan.put(date, plannedQuantity);
                remainingQuantity -= plannedQuantity;
            }
            date = date.minusDays(1);
        }
        if (remainingQuantity > 0) {
            return earliestPlan;
        }
        return plan;
    }

    // 계산 날짜가 허용된 마지막 날짜를 초과하는지 검증
    void checkCalculationLimit(LocalDate date) {
        if (date.isAfter(calculationLimit)) {
            throw new IllegalArgumentException("납기 계산은 다음 해 말일인 " + calculationLimit + "까지 가능합니다.");
        }
    }
}
