package net.likelion.bebc25.prodflow.quotation.schedule;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.line.repsitory.LineRepository;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationScheduleRepository;
import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class QuotationScheduleService { // 생산 능력과 기존 수주의 생산량 반영 납기 일정 계산
    private final LineRepository lineRepository;
    private final QuotationScheduleRepository scheduleRepository;

    // 선택한 납기에 맞춰 생산 일정 계산
    public QuotationScheduleResult calculate(long quantity, LocalDate requestedDueDate, LocalDate today) {
        if (requestedDueDate == null) {
            throw new IllegalArgumentException("희망 납기일을 입력해주세요.");
        }

        QuotationSchedulePreviewResult result = previewSchedule(quantity, requestedDueDate, today);
        if (result.selectedSchedule() == null) {
            throw new IllegalArgumentException("희망 납기를 지킬 수 없습니다. 가능한 가장 빠른 납기일: "
                    + result.earliestSchedule().estimatedDueDate());
        }
        return result.selectedSchedule();
    }

    // 생산능력, 휴일, 기존 수주를 반영한 계산용 캘린더 생성
    private ProductionCalendar createCalendar(LocalDate today) {
        long dailyCapacity = calculateDailyCapacity();
        Set<LocalDate> holidays = new HashSet<>(scheduleRepository.findHolidays());

        ProductionCalendar calendar = new ProductionCalendar(
                dailyCapacity,
                holidays,
                LocalDate.of(today.getYear() + 1, 12, 31)
        );
        reserveExistingOrders(calendar, today);

        return calendar;
    }

    // 가동 가능·가동 중 라인의 일일 생산능력 합산
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

    // 가장 빠른 납기와 선택한 납기의 생산 가능 여부 조회
    public QuotationSchedulePreviewResult previewSchedule(long quantity, LocalDate requestedDueDate, LocalDate today) {
        ProductionCalendar calendar = createCalendar(today);
        if (requestedDueDate != null) {
            calendar.checkCalculationLimit(requestedDueDate);
        }

        LocalDate firstDate = calendar.nextWorkingDay(today.plusDays(3));

        Map<LocalDate, Long> earliestPlan = calendar.planFromStart(quantity, firstDate);

        LocalDate earliestStartDate = Collections.min(earliestPlan.keySet());
        LocalDate earliestEndDate = Collections.max(earliestPlan.keySet());

        LocalDate earliestDueDate = calendar.addWorkingDays(earliestEndDate, 3);
        QuotationScheduleResult earliestSchedule = new QuotationScheduleResult(
                earliestDueDate, earliestStartDate, earliestEndDate);

        // 날짜를 선택하지 않았으면 가장 빠른 일정을 반환
        if (requestedDueDate == null) {
            return new QuotationSchedulePreviewResult(earliestSchedule, earliestSchedule);
        }

        // 희망 납기일이 가장 빠른 납기일보다 이르면 선택 불가능
        if (requestedDueDate.isBefore(earliestDueDate)) {
            return new QuotationSchedulePreviewResult(earliestSchedule, null);
        }

        Map<LocalDate, Long> selectedPlan = calendar.planProduction(quantity, firstDate, requestedDueDate, earliestPlan);

        LocalDate selectedStartDate = Collections.min(selectedPlan.keySet());
        LocalDate selectedEndDate = Collections.max(selectedPlan.keySet());

        QuotationScheduleResult selectedSchedule = new QuotationScheduleResult(
                requestedDueDate, selectedStartDate, selectedEndDate);
        return new QuotationSchedulePreviewResult(earliestSchedule, selectedSchedule);
    }

    // 기존 수주의 남은 생산 수량을 등록순으로 계산용 캘린더에 배정
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

    // 수주별 품목 수량 합산
    private Map<Long, Long> calculateOrderQuantities(List<Long> orderIds) {
        Map<Long, Long> totalQuantityByOrder = new HashMap<>();
        for (OrderProduct item : scheduleRepository.findOrderProducts(orderIds)) {
            Long orderId = item.getCustomerOrder().getId();
            long previousQuantity = totalQuantityByOrder.getOrDefault(orderId, 0L);
            totalQuantityByOrder.put(orderId, Math.addExact(previousQuantity, item.getQuantity()));
        }
        return totalQuantityByOrder;
    }

    // 시작된 작업의 수주별 고정 수량 합산 및 오늘 이후 생산량 예약
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
}
