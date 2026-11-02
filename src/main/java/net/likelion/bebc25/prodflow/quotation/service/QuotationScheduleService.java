package net.likelion.bebc25.prodflow.quotation.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.productionline.entity.Line;
import net.likelion.bebc25.prodflow.productionline.enums.LineStatus;
import net.likelion.bebc25.prodflow.productionline.repsitory.LineRepository;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationScheduleRepository;
import net.likelion.bebc25.prodflow.workorder.entity.ProductionPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class QuotationScheduleService {
    private final LineRepository lineRepository;
    private final QuotationScheduleRepository scheduleRepository;

    public record Schedule(LocalDate estimatedDueDate, LocalDate plannedStartDate, LocalDate plannedEndDate) {}

    public Schedule calculate(long quantity, LocalDate requestedDueDate, LocalDate today) {
        // 하루 생산량 및 휴일 확인
        long dailyCapacity = calculateDailyCapacity();
        Set<LocalDate> holidays = new HashSet<>(scheduleRepository.findHolidays());
        // 요청별 예약 수량 임시 보관
        ProductionCalendar calendar = new ProductionCalendar(dailyCapacity, holidays, today.plusYears(20));

        // 기존 수주 생산량 우선 배정
        reserveExistingOrders(calendar, today);

        // 오늘 + 3일 기준 생산 시작일 계산 (휴일 제외)
        LocalDate firstDate = calendar.nextWorkingDay(today.plusDays(3));
        Map<LocalDate, Long> plan = calendar.planProduction(quantity, firstDate, requestedDueDate);
        LocalDate startDate = Collections.min(plan.keySet());
        LocalDate endDate = Collections.max(plan.keySet());

        // 포장 1일 및 배송 2일 추가 (주말·공휴일 제외)
        LocalDate possibleDueDate = calendar.addWorkingDays(endDate, 3);
        if (possibleDueDate.isAfter(requestedDueDate)) {
            throw new IllegalArgumentException("희망 납기를 지킬 수 없습니다. 가능한 가장 빠른 납기일: " + possibleDueDate);
        }
        return new Schedule(requestedDueDate, startDate, endDate);
    }

    private long calculateDailyCapacity() {
        long dailyCapacity = 0;
        for (Line line : lineRepository.findAll()) {
            if (line.getStatus() == LineStatus.AVAILABLE || line.getStatus() == LineStatus.OPERATING) {
                dailyCapacity = Math.addExact(dailyCapacity, line.getProductionCapacity());
            }
        }
        if (dailyCapacity <= 0) {
            throw new IllegalArgumentException("가동 가능한 생산라인이 없어 납기를 계산할 수 없습니다.");
        }
        return dailyCapacity;
    }

    private void reserveExistingOrders(ProductionCalendar calendar, LocalDate today) {
        // 수주 등록순 조회
        List<CustomerOrder> orders = scheduleRepository.findActiveOrders();
        List<Long> orderIds = new ArrayList<>();
        for (CustomerOrder order : orders) {
            orderIds.add(order.getId());
        }
        Map<Long, Long> totalQuantityByOrder = calculateOrderQuantities(orderIds);
        Map<Long, Long> fixedQuantityByOrder = reserveFixedPlans(calendar, orderIds, today);

        for (CustomerOrder order : orders) {
            long totalQuantity = totalQuantityByOrder.getOrDefault(order.getId(), 0L);
            long fixedQuantity = fixedQuantityByOrder.getOrDefault(order.getId(), 0L);
            long remainingQuantity = totalQuantity - fixedQuantity;
            if (remainingQuantity < 0) {
                throw new IllegalArgumentException("기존 수주의 생산계획 수량이 수주 수량을 초과합니다.");
            }
            if (remainingQuantity == 0) {
                continue;
            }

            LocalDate startDate = order.getDepositDueDate();
            if (startDate.isBefore(today)) {
                startDate = today;
            }
            Map<LocalDate, Long> plan = calendar.planProduction(remainingQuantity,
                    calendar.nextWorkingDay(startDate), order.getEstimatedDueDate());
            for (Map.Entry<LocalDate, Long> day : plan.entrySet()) {
                calendar.reserve(day.getKey(), day.getValue());
            }
        }
    }

    private Map<Long, Long> calculateOrderQuantities(List<Long> orderIds) {
        Map<Long, Long> totalQuantityByOrder = new HashMap<>();
        for (OrderProduct item : scheduleRepository.findOrderProducts(orderIds)) {
            Long orderId = item.getCustomerOrder().getId();
            long previousQuantity = totalQuantityByOrder.getOrDefault(orderId, 0L);
            totalQuantityByOrder.put(orderId, Math.addExact(previousQuantity, item.getQuantity()));
        }
        return totalQuantityByOrder;
    }

    private Map<Long, Long> reserveFixedPlans(ProductionCalendar calendar, List<Long> orderIds, LocalDate today) {
        Map<Long, Long> fixedQuantityByOrder = new HashMap<>();
        // 시작된 작업의 생산계획 고정
        for (ProductionPlan plan : scheduleRepository.findFixedPlans(orderIds)) {
            Long orderId = plan.getOrderProduct().getCustomerOrder().getId();
            long previousQuantity = fixedQuantityByOrder.getOrDefault(orderId, 0L);
            fixedQuantityByOrder.put(orderId, Math.addExact(previousQuantity, plan.getQuantity()));
            // 과거 수량 합산 및 오늘 이후 생산량 예약
            if (!plan.getPlanDate().isBefore(today)) {
                calendar.reserve(plan.getPlanDate(), plan.getQuantity());
            }
        }
        return fixedQuantityByOrder;
    }

    // 날짜별 예약 수량 관리 및 영업일 계산
    private static class ProductionCalendar {
        private final long dailyCapacity;
        private final Set<LocalDate> holidays;
        private final LocalDate calculationLimit;
        private final Map<LocalDate, Long> reservedQuantityByDate = new HashMap<>();

        ProductionCalendar(long dailyCapacity, Set<LocalDate> holidays, LocalDate calculationLimit) {
            this.dailyCapacity = dailyCapacity;
            this.holidays = holidays;
            this.calculationLimit = calculationLimit;
        }

        boolean isWorkingDay(LocalDate date) {
            return date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(date);
        }

        LocalDate nextWorkingDay(LocalDate date) {
            while (!isWorkingDay(date)) {
                date = date.plusDays(1);
            }
            return date;
        }

        LocalDate addWorkingDays(LocalDate date, int days) {
            int countedDays = 0;
            while (countedDays < days) {
                date = date.plusDays(1);
                if (isWorkingDay(date)) {
                    countedDays++;
                }
            }
            return date;
        }

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

        void reserve(LocalDate date, long quantity) {
            long reservedQuantity = reservedQuantityByDate.getOrDefault(date, 0L);
            reservedQuantityByDate.put(date, Math.addExact(reservedQuantity, quantity));
        }

        private long availableQuantity(LocalDate date) {
            if (!isWorkingDay(date)) {
                return 0;
            }
            long reservedQuantity = reservedQuantityByDate.getOrDefault(date, 0L);
            return Math.max(0, dailyCapacity - reservedQuantity);
        }

        Map<LocalDate, Long> planProduction(long quantity, LocalDate firstDate, LocalDate dueDate) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("생산 수량은 양수여야 합니다.");
            }
            // 가장 빠른 생산 일정 계산
            Map<LocalDate, Long> earliestPlan = planFromStart(quantity, firstDate);
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

        private Map<LocalDate, Long> planFromStart(long quantity, LocalDate firstDate) {
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

        private void checkCalculationLimit(LocalDate date) {
            if (date.isAfter(calculationLimit)) {
                throw new IllegalArgumentException("납기 계산 범위(20년)를 초과했습니다.");
            }
        }
    }
}
