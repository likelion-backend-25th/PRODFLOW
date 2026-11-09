package net.likelion.bebc25.prodflow.work_order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.customer_order.repository.OrderProductRepository;
import net.likelion.bebc25.prodflow.holiday.entity.Holiday;
import net.likelion.bebc25.prodflow.holiday.repository.HolidayRepository;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.line.repository.LineRepository;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationScheduleRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import net.likelion.bebc25.prodflow.work_order.dto.ProductionPlanDto;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderHistoryDto;
import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrderHistory;
import net.likelion.bebc25.prodflow.work_order.repository.ProductionPlanRepository;
import net.likelion.bebc25.prodflow.work_order.repository.WorkOrderHistoryRepository;
import net.likelion.bebc25.prodflow.work_order.repository.WorkOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkOrderService {

    // 변경 가능한 상태 전이
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "PREPARE", Set.of("PRODUCE", "HALT"),
            "PRODUCE", Set.of("SUSPEND", "HALT", "PACK"),
            "SUSPEND", Set.of("PRODUCE", "HALT"),
            "HALT", Set.of("PREPARE"),
            "PACK", Set.of("PACK_DONE")
    );

    private final WorkOrderRepository workOrderRepository;
    private final OrderProductRepository orderProductRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final LineRepository lineRepository;
    private final UserRepository userRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final HolidayRepository holidayRepository;
    private final ProductionPlanRepository productionPlanRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final WorkOrderHistoryRepository workOrderHistoryRepository;
    private final ProductionPlanRescheduleService productionPlanRescheduleService;
    private final QuotationScheduleRepository quotationScheduleRepository;

    // 계약금 확인 시 등록 순서로 미시작 물량을 재배치한다. 생산 이력이 있는 작업은 수정하지 않는다.
    @Transactional
    public List<WorkOrderDto.Response> createForConfirmedOrder(CustomerOrder order, User actor) {
        if (!"CONFIRMED".equals(order.getStatusCode().getCode())
                || workOrderRepository.existsByOrderProduct_CustomerOrder_Id(order.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "생산대기 상태이며 작업지시가 없는 수주만 배정할 수 있습니다.");
        }
        List<Line> lines = lineRepository.findAllForUpdate().stream()
                .filter(line -> line.getStatus() == LineStatus.AVAILABLE || line.getStatus() == LineStatus.OPERATING)
                .toList();
        if (lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "가동 가능한 생산라인이 없습니다.");
        }
        CommonCode prepare = commonCodeRepository.findByCodeGroup_GroupCodeAndCodeAndStatus(
                        "WORK_ORDER_STATUS", "PREPARE", CodeStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("준비 상태 코드를 찾을 수 없습니다."));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<CustomerOrder> orders = quotationScheduleRepository.findActiveOrders();
        List<Long> orderIds = orders.stream().map(CustomerOrder::getId).toList();
        List<WorkOrder> existingWorks = workOrderRepository.findByOrderIds(orderIds);
        Set<Long> fixedIds = new HashSet<>(workOrderRepository.findFixedWorkOrderIds(orderIds));
        Map<Long, List<WorkOrder>> movableByItem = new HashMap<>();
        Map<Long, Integer> fixedQuantity = new HashMap<>();
        Map<Long, List<WorkOrder>> fixedByOrder = new HashMap<>();
        List<Long> movableIds = new ArrayList<>();
        for (WorkOrder work : existingWorks) {
            if (fixedIds.contains(work.getId())) {
                fixedQuantity.merge(work.getOrderProduct().getId(), work.getQuantity(), Integer::sum);
                fixedByOrder.computeIfAbsent(work.getOrderProduct().getCustomerOrder().getId(), key -> new ArrayList<>()).add(work);
            } else {
                movableByItem.computeIfAbsent(work.getOrderProduct().getId(), key -> new ArrayList<>()).add(work);
                movableIds.add(work.getId());
            }
        }
        if (!movableIds.isEmpty()) {
            productionPlanRepository.deleteAll(productionPlanRepository.findByWorkOrder_IdIn(movableIds));
            productionPlanRepository.flush();
        }

        Map<Long, Map<LocalDate, Integer>> reserved = new HashMap<>();
        List<DepositAssignment> assignments = new ArrayList<>();
        for (CustomerOrder current : orders) {
            LocalDate first = null;
            LocalDate last = null;
            for (WorkOrder fixed : fixedByOrder.getOrDefault(current.getId(), List.of())) {
                first = first == null || fixed.getPlannedStartDate().isBefore(first) ? fixed.getPlannedStartDate() : first;
                last = last == null || fixed.getPlannedEndDate().isAfter(last) ? fixed.getPlannedEndDate() : last;
            }
            List<OrderProduct> items = orderProductRepository.findAllByCustomerOrderId(current.getId());
            if (items.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "생산할 수주 품목이 없습니다: " + current.getOrderNo());
            }
            for (OrderProduct item : items) {
                int remaining = item.getQuantity() - fixedQuantity.getOrDefault(item.getId(), 0);
                if (remaining < 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "시작된 작업 수량이 수주 품목 수량보다 많습니다.");
                }
                if (remaining == 0) {
                    continue;
                }
                Map<Line, List<DayPlan>> allocations = allocateDepositItem(item, remaining, lines, today, reserved);
                for (List<DayPlan> days : allocations.values()) {
                    first = first == null || days.getFirst().planDate().isBefore(first) ? days.getFirst().planDate() : first;
                    last = last == null || days.getLast().planDate().isAfter(last) ? days.getLast().planDate() : last;
                }
                // 미입금 수주도 생산 자리는 확보하되 실제 작업지시는 만들지 않는다.
                if (!"WAITING".equals(current.getStatusCode().getCode())) {
                    assignments.add(new DepositAssignment(item, allocations));
                }
            }
            if (first != null && last != null) {
                LocalDate dueDate = deliveryDateAfterProduction(last);
                if (dueDate.isBefore(current.getEstimatedDueDate())) {
                    dueDate = current.getEstimatedDueDate();
                }
                current.changeSchedule(dueDate, first, last);
            }
        }

        List<WorkOrder> targetJobs = new ArrayList<>();
        for (DepositAssignment assignment : assignments) {
            OrderProduct item = assignment.item();
            List<WorkOrder> previous = movableByItem.computeIfAbsent(item.getId(), key -> new ArrayList<>());
            for (Map.Entry<Line, List<DayPlan>> entry : assignment.allocations().entrySet()) {
                List<DayPlan> days = entry.getValue();
                WorkOrder work = previous.stream().filter(candidate -> candidate.getLine().getId().equals(entry.getKey().getId()))
                        .findFirst().orElse(previous.isEmpty() ? null : previous.getFirst());
                int quantity = days.stream().mapToInt(DayPlan::quantity).sum();
                if (work == null) {
                    work = new WorkOrder(nextDepositWorkNo(today), item, entry.getKey(), null, quantity,
                            days.getFirst().planDate(), days.getLast().planDate(), "계약금 확인 후 작업 배정");
                    work.changeStatus(prepare);
                    work.setCreatedBy(item.getCustomerOrder().getUser().getName());
                    workOrderRepository.save(work);
                    statusHistoryRepository.save(new StatusHistory(HistoryEntityType.WORK_ORDER, work.getId(), "NONE", "PREPARE", actor));
                } else {
                    previous.remove(work);
                    Line oldLine = work.getLine();
                    User oldUser = work.getUser();
                    work.changeLine(entry.getKey());
                    if (!isActiveProductionManager(oldUser)) {
                        work.changeUser(null);
                    }
                    if (!oldLine.getId().equals(work.getLine().getId()) || oldUser != work.getUser()) {
                        workOrderHistoryRepository.save(new WorkOrderHistory(work, oldLine, work.getLine(),
                                oldUser, work.getUser(), actor, "수주 등록 순서에 따른 준비 작업 재배치"));
                    }
                    work.changeQuantity(quantity);
                    work.changePlannedDates(days.getFirst().planDate(), days.getLast().planDate());
                }
                work.setUpdatedBy(actor.getName());
                for (DayPlan day : days) {
                    ProductionPlan plan = new ProductionPlan(work, day.planDate(), day.quantity());
                    plan.setCreatedBy(actor.getName());
                    plan.setUpdatedBy(actor.getName());
                    productionPlanRepository.save(plan);
                }
                if (item.getCustomerOrder().getId().equals(order.getId())) {
                    targetJobs.add(work);
                }
            }
        }
        // 여러 준비 작업이 한 라인으로 통합되면 배정 이력을 보관한 후 남는 행을 제거한다.
        for (List<WorkOrder> leftovers : movableByItem.values()) {
            for (WorkOrder unused : leftovers) {
                for (WorkOrderHistory history : workOrderHistoryRepository.findByWorkOrder_Id(unused.getId())) {
                    history.archiveWorkOrder();
                }
                workOrderHistoryRepository.flush();
                workOrderRepository.delete(unused);
            }
        }
        workOrderRepository.flush();
        assignUnassignedPrepare(actor, today.plusDays(1));
        return targetJobs.stream().map(work -> new WorkOrderDto.Response(
                work.getId(), work.getWorkNo(), work.getNote(), plansOf(work))).toList();
    }

    // 날짜별로 빈 생산량이 큰 라인부터 채우고, 같은 품목·라인은 한 작업지시로 묶는다.
    private Map<Line, List<DayPlan>> allocateDepositItem(OrderProduct item, int quantityToAllocate, List<Line> lines, LocalDate today,
                                                         Map<Long, Map<LocalDate, Integer>> reserved) {
        CustomerOrder order = item.getCustomerOrder();
        LocalDate start = order.getPlannedStartDate();
        if (start.isBefore(order.getDepositDueDate())) {
            start = order.getDepositDueDate();
        }
        if (start.isBefore(today)) {
            start = today;
        }
        LocalDate end = LocalDate.of(today.getYear() + 1, 12, 31);
        Set<LocalDate> holidays = new HashSet<>();
        if (!start.isAfter(end)) {
            for (Holiday holiday : holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(start, end)) {
                holidays.add(holiday.getHolidayDate());
            }
        }
        Map<Line, List<DayPlan>> allocations = new LinkedHashMap<>();
        int remaining = quantityToAllocate;
        for (LocalDate date = start; !date.isAfter(end) && remaining > 0; date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY || holidays.contains(date)) {
                continue;
            }
            LocalDate planDate = date;
            List<Line> available = new ArrayList<>(lines);
            available.sort(Comparator.<Line>comparingLong(line -> depositSpace(line, planDate, reserved)).reversed()
                    .thenComparing(Line::getId));
            for (Line line : available) {
                long space = depositSpace(line, date, reserved);
                int quantity = (int) Math.min(remaining, Math.max(0, space));
                if (quantity == 0) {
                    continue;
                }
                allocations.computeIfAbsent(line, key -> new ArrayList<>()).add(new DayPlan(date, quantity));
                reserved.computeIfAbsent(line.getId(), key -> new HashMap<>()).merge(date, quantity, Integer::sum);
                remaining -= quantity;
                if (remaining == 0) {
                    break;
                }
            }
        }
        if (remaining > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "수주 " + order.getOrderNo() + "의 생산 기간에 배정할 용량이 부족합니다. 생산 일정을 먼저 조정해주세요.");
        }
        return allocations;
    }

    private long depositSpace(Line line, LocalDate date, Map<Long, Map<LocalDate, Integer>> reserved) {
        return line.getProductionCapacity() - productionPlanRepository.sumQuantityByLineAndDate(line.getId(), date)
                - reserved.getOrDefault(line.getId(), Map.of()).getOrDefault(date, 0);
    }

    // 출근 여부와 현재 생산 여부는 제외하지 않는다. 다음 날 장수, 이름, ID 순으로 고른다.
    private User chooseDepositAssignee(LocalDate nextDate) {
        User chosen = null;
        long least = Long.MAX_VALUE;
        for (User user : userRepository.findAll()) {
            if (!isActiveProductionManager(user)) {
                continue;
            }
            long load = workOrderRepository.countPlannedWorkByUserAndDate(user.getId(), nextDate);
            if (chosen == null || load < least || (load == least && user.getName().compareTo(chosen.getName()) < 0)
                    || (load == least && user.getName().equals(chosen.getName()) && user.getId() < chosen.getId())) {
                chosen = user;
                least = load;
            }
        }
        return chosen;
    }

    private boolean isActiveProductionManager(User user) {
        return user != null && user.getRole() == UserRole.PRODUCTION_MANAGER && user.getStatus() == UserStatus.ACTIVE;
    }

    private void assignUnassignedPrepare(User actor, LocalDate nextDate) {
        for (WorkOrder work : workOrderRepository.findUnassignedPrepareOrderByDueDate()) {
            User assignee = chooseDepositAssignee(nextDate);
            if (assignee == null) {
                break;
            }
            work.changeUser(assignee);
            work.setUpdatedBy(actor.getName());
            workOrderHistoryRepository.save(new WorkOrderHistory(work, work.getLine(), work.getLine(),
                    null, assignee, actor, "미배정 준비 작업 자동 담당자 배정"));
        }
    }

    private String nextDepositWorkNo(LocalDate today) {
        long next = workOrderRepository.count() + 1;
        String workNo;
        do {
            workNo = "WO-" + today.getYear() + "-" + String.format("%03d", next++);
        } while (workOrderRepository.existsByWorkNo(workNo));
        return workNo;
    }

    private LocalDate deliveryDateAfterProduction(LocalDate completedDate) {
        Set<LocalDate> holidays = new HashSet<>();
        for (Holiday holiday : holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(completedDate, completedDate.plusDays(30))) {
            holidays.add(holiday.getHolidayDate());
        }
        LocalDate date = completedDate;
        int days = 0;
        while (days < 3) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(date)) {
                days++;
            }
        }
        return date;
    }

    // 한 라인이 부족하면 다른 라인에 장을 더 만든다. 장마다 지시번호가 1 증가한다.
    @Transactional
    public List<WorkOrderDto.Response> create(WorkOrderDto.CreateRequest request, Long userId) {

        // 수주 품목 한 줄이다. 수주 담당자는 이 품목의 수주에 있다.
        OrderProduct orderProduct = orderProductRepository.findById(request.orderProductId())
                .orElseThrow(() -> new IllegalArgumentException("수주 품목을 찾을 수 없습니다."));
        // 이 수주를 등록한 수주 관리자다. created_by에 이 이름을 넣는다.
        User creator = orderProduct.getCustomerOrder().getUser();

        // 생산 담당자다. 작업지시서 user_id에 들어간다.
        User assignee = userRepository.findById(request.assignedId())
                .orElseThrow(() -> new IllegalArgumentException("생산 담당자를 찾을 수 없습니다."));
        boolean isProductionManager = assignee.getRole() == UserRole.PRODUCTION_MANAGER;
        boolean isActive = assignee.getStatus() == UserStatus.ACTIVE;
        if (!isProductionManager || !isActive) {
            throw new IllegalArgumentException("사용 중인 생산 현장 관리자만 담당자로 배정할 수 있습니다.");
        }

        // 화면에서 고른 첫 배정 라인이다.
        Line line = lineRepository.findById(request.lineId())
                .orElseThrow(() -> new IllegalArgumentException("라인을 찾을 수 없습니다."));

        // 첫 라인이 점검·고장이면 배정을 시작하지 않는다.
        if (line.getStatus() != LineStatus.AVAILABLE && line.getStatus() != LineStatus.OPERATING) {
            throw new IllegalArgumentException("점검·고장 라인에는 배정할 수 없습니다.");
        }

        // WORK_ORDER_STATUS 그룹의 사용 중인 코드를 가져온다.
        CommonCode prepare = commonCodeRepository
                .findByCodeGroup_GroupCodeAndStatus("WORK_ORDER_STATUS", CodeStatus.ACTIVE)
                .stream()
                // code가 PREPARE인 행
                .filter(code -> "PREPARE".equals(code.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("준비 상태 코드를 찾을 수 없습니다."));

        // 시작일부터 종료일까지의 공휴일이다.
        Set<LocalDate> holidays = new HashSet<>();
        for (Holiday holiday : holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(
                request.plannedStartDate(), request.plannedEndDate())) {
            // 공휴일 날짜만 모아 둠
            holidays.add(holiday.getHolidayDate());
        }

        // 이번 수주 등록의 첫 지시번호다. 장을 저장할 때마다 1 올린다.
        long nextNo = workOrderRepository.count() + 1;
        // 아직 라인에 넣지 못한 수량이다.
        int remaining = request.quantity();
        // 저장된 작업지시서 응답이다. 장이 여러 개일 수 있다.
        List<WorkOrderDto.Response> responses = new ArrayList<>();

        // 사용 가능한 라인을 리스트로 한곳에 담는다
        List<Line> lines = new ArrayList<>();
        lines.add(line);
        // 남은 수량을 넘길 수 있는지 보는 다른 라인
        for (Line otherLine : lineRepository.findAll()) {
            // 이미 넣은 요청 라인은 다시 넣지 않는다.
            if (otherLine.getId().equals(line.getId())) {
                continue;
            }
            // 남은 수량을 넘길 라인은 가동 가능·가동 중만 둔다.
            if (otherLine.getStatus() == LineStatus.AVAILABLE || otherLine.getStatus() == LineStatus.OPERATING) {
                lines.add(otherLine);
            }
        }

        for (Line target : lines) {
            // 수량이 다 들어갔으면 다음 라인을 보지 않는다.
            if (remaining == 0) {
                break;
            }
            // 이 라인에 넣을 하루 계획이다. 라인이 바뀌면 비운 뒤 다시 쌓는다.
            List<DayPlan> dayPlans = new ArrayList<>();
            // 이 라인에 넣어 볼 남은 수량이다.
            int remainingToAssign = remaining;
            // 이 라인의 첫 검사일이다.
            LocalDate date = request.plannedStartDate();

            // 종료일까지, 이 라인에 더 넣을 수량이 있는 동안 하루씩 본다.
            while (!date.isAfter(request.plannedEndDate()) && remainingToAssign > 0) {
                // 주말인지 확인
                boolean isWeekend = date.getDayOfWeek() == DayOfWeek.SATURDAY
                        || date.getDayOfWeek() == DayOfWeek.SUNDAY;
                // 주말이거나 공휴일이면 다음 날로 넘긴다.
                if (isWeekend || holidays.contains(date)) {
                    date = date.plusDays(1);
                    continue;
                }
                // 취소된 수주와 포장 이후 작업을 뺀 계획 합으로 그날 잔여를 다시 계산한다.
                int remainingCapacity = remainingCapacity(target, date);
                // 남은 자리가 있을 때만 수량을 넣는다.
                if (remainingCapacity > 0) {
                    // 남은 자리와 아직 못 넣은 수량 중 작은 쪽을 그날 배정량으로 한다.
                    int assigned = Math.min(remainingCapacity, remainingToAssign);
                    // 날짜와 수량을 하루 계획 한 건으로 쌓는다.
                    dayPlans.add(new DayPlan(date, assigned));
                    // 이 라인에 아직 못 넣은 수량에서 오늘 넣은 양을 뺀다.
                    remainingToAssign -= assigned;
                }
                // 생산 가능한 날도 다음 날로 넘어간다.
                date = date.plusDays(1);
            }

            // 이 라인에 실제로 들어간 수량 합이다.
            int assignedOnThisLine = 0;
            for (DayPlan dayPlan : dayPlans) {
                // 하루 계획 수량을 더해 이 장에 들어갈 총수량을 만든다.
                assignedOnThisLine += dayPlan.quantity();
            }
            // 들어간 수량이 없으면 작업지시서를 만들지 않고 다음 라인으로 간다.
            if (assignedOnThisLine == 0) {
                continue;
            }

            // 이번 장의 지시번호다. count()를 다시 부르지 않는다.
            String workNo = "WO-" + Year.now().getValue() + "-" + String.format("%03d", nextNo);
            // 제품명, 이번 라인, 작업 시작 예정일, 작업 종료 예정일이다.
            String note = orderProduct.getProductName()
                    + " "
                    + target.getLineName()
                    + " "
                    + request.plannedStartDate()
                    + " ~ "
                    + request.plannedEndDate();

            // 같은 라인의 여러 날은 작업지시서 한 장이다. 수량은 이 라인에 들어간 합이다.
            WorkOrder workOrder = new WorkOrder(
                    workNo,
                    orderProduct,
                    target,
                    assignee,
                    assignedOnThisLine,
                    request.plannedStartDate(),
                    request.plannedEndDate(),
                    note);
            // 등록 직원 이름은 수주를 등록한 담당자다.
            workOrder.setCreatedBy(creator.getName());
            // 등록 시점의 수정자도 같은 이름이다.
            workOrder.setUpdatedBy(creator.getName());
            // NOT NULL인 상태를 준비로 채운다.
            workOrder.changeStatus(prepare);
            // 저장해야 생산 계획에 작업지시서 id가 들어간다.
            WorkOrder saved = workOrderRepository.save(workOrder);
            List<ProductionPlanDto.Response> plans = new ArrayList<>();
            for (DayPlan dayPlan : dayPlans) {
                // 저장 전 초안을 생산 계획으로 저장한다. 작성자는 작업지시와 같은 수주 담당자다.
                ProductionPlan plan = new ProductionPlan(saved, dayPlan.planDate(), dayPlan.quantity());
                plan.setCreatedBy(creator.getName());
                plan.setUpdatedBy(creator.getName());
                ProductionPlan savedPlan = productionPlanRepository.save(plan);
                plans.add(ProductionPlanDto.Response.from(savedPlan));
            }
            // 저장된 장 하나와 그 하루 계획을 응답 목록에 넣는다.
            responses.add(new WorkOrderDto.Response(saved.getId(), saved.getWorkNo(), saved.getNote(), plans));
            // 다음 장의 지시번호를 1 올린다.
            nextNo++;
            // 전체 남은 수량에서 이 장에 넣은 수량을 뺀다.
            remaining -= assignedOnThisLine;
        }

        // 모든 라인을 봤는데도 수량이 남으면 기간 안 생산 가능량으로는 부족하다.
        if (remaining > 0) {
            // 응답에는 기간과 남은 수량이 빠지므로 서버 로그에 남긴다.
            log.warn("생산 가능량 초과. 기간 {} ~ {}, 요청 수량 중 남은 수량 {}",
                    request.plannedStartDate(), request.plannedEndDate(), remaining);
            throw new IllegalArgumentException("선택한 기간의 생산 가능량을 초과했습니다.");
        }
        // 만들어진 작업지시서 목록이다. 한 장이면 원소가 하나다.
        return responses;
    }

    // 작업지시서 페이징 조회
    public Page<WorkOrderDto.PageResponse> findAll(
            Long lineId,
            String status,
            LocalDate from,
            LocalDate to,
            String keyword,
            Pageable pageable
    ) {
        return findAll(null, lineId, status, from, to, keyword, pageable);
    }

    // 수주 필터와 기존 검색 조건을 함께 적용한다.
    public Page<WorkOrderDto.PageResponse> findAll(
            Long orderId, Long lineId, String status,
            LocalDate from, LocalDate to, String keyword, Pageable pageable) {
        if (orderId != null && !customerOrderRepository.existsById(orderId)) {
            throw new NoSuchElementException("수주를 찾을 수 없습니다.");
        }

        String code = status == null || status.isBlank() ? null : status.trim();
        String word = keyword == null || keyword.isBlank() ? null : keyword.trim();

        boolean hasCondition = orderId != null || lineId != null
                || code != null || from != null || to != null || word != null;

        if (!hasCondition) {
            return workOrderRepository.findPage(pageable);
        }

        return workOrderRepository.searchPage(orderId, lineId, code, from, to, word, pageable);
    }

    // 작업지시서 상세 조회
    public WorkOrderDto.DetailResponse getDetail(Long workOrderId) {
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new NoSuchElementException("해당 지시서를 찾을 수 없습니다."));
        return WorkOrderDto.DetailResponse.from(workOrder, plansOf(workOrder));
    }

    // 수정 전에 읽는다. 수정 뒤에 조회하면 flush가 수정자 이름을 SYSTEM으로 덮는다.
    private List<ProductionPlanDto.Response> plansOf(WorkOrder workOrder) {
        return productionPlanRepository.findByWorkOrder_Id(workOrder.getId())
                .stream()
                .sorted(Comparator.comparing(ProductionPlan::getPlanDate))
                .map(ProductionPlanDto.Response::from)
                .toList();
    }

    // 작업지시서 수정
    @Transactional
    public WorkOrderDto.DetailResponse update(Long workOrderId,
                                              WorkOrderDto.UpdatedRequest request,
                                              Long userId) {
        // 수정할 작업지시서 조회
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new NoSuchElementException("해당 작업을 찾을 수 없습니다."));
        Line previousLine = workOrder.getLine();
        User previousUser = workOrder.getUser();

        // 변경할 내용이 있는지 확인
        boolean isChangedLine = request.lineId() != null;
        boolean isChangedAssigned = request.assignedId() != null;
        // 변경 사항이 없는 경우
        if (!isChangedLine && !isChangedAssigned) {
            throw new IllegalArgumentException(
                    "변경된 라인 또는 담당자가 없습니다."
            );
        }
        // 갱신한 직원. 라인과 담당자는 바꿀 수 있는 역할이 다르다.
        User modifier = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("수정자를 찾을 수 없습니다."));
        if (isChangedLine
                && modifier.getRole() != UserRole.SUPER_ADMIN
                && modifier.getRole() != UserRole.PRODUCTION_MANAGER) {
            throw new AccessDeniedException("라인 변경은 최고 관리자와 생산 현장 관리자만 할 수 있습니다.");
        }
        if (isChangedAssigned
                && modifier.getRole() != UserRole.SUPER_ADMIN
                && modifier.getRole() != UserRole.ORDER_MANAGER) {
            throw new AccessDeniedException("담당자 변경은 최고 관리자와 수주 관리자만 할 수 있습니다.");
        }
        // 라인변경. 복구 라인의 앞날이 비어 있을 때만 밀린 일정을 당긴다.
        String historyReason = null;
        if (isChangedLine) {
            Line line = lineRepository.findById(request.lineId())
                    .orElseThrow(() -> new IllegalArgumentException("변경할 라인을 찾을 수 없습니다."));
            // 같은 라인을 다시 보내는 수정은 배정을 건드리지 않는다. 생산 중 라인도 여기 해당한다.
            if (!line.getId().equals(previousLine.getId())) {
                // 생산·일시정지는 배정을 이 라인에 남긴다.
                String workStatus = workOrder.getCommonCode().getCode();
                if ("PRODUCE".equals(workStatus) || "SUSPEND".equals(workStatus)) {
                    throw new IllegalArgumentException("생산을 시작한 작업은 라인을 변경할 수 없습니다.");
                }
                if (line.getStatus() != LineStatus.AVAILABLE) {
                    throw new IllegalArgumentException(
                            "가동 가능한 라인만 배정할 수 있습니다.");
                }
                if (productionPlanRescheduleService.shouldPullForward(workOrder, line)) {
                    // 복구 라인의 앞날이 비어 있을 때만 밀린 일정을 당긴다.
                    productionPlanRescheduleService.replanOnRecoveredLine(workOrder, line, modifier);
                    historyReason = "복구 라인으로 재이동";
                } else {
                    // 앞날에 다른 작업이 있거나 일반 이동이면 기존 날짜가 들어가는지만 본다.
                    productionPlanRescheduleService.assertFits(workOrder, line);
                    workOrder.changeLine(line);
                }
            }
        }
        // 담당자 변경
        if (isChangedAssigned) {
            User assignedUser = userRepository.findById(request.assignedId())
                    .orElseThrow(() -> new IllegalArgumentException(""));
            boolean isProductionManager = assignedUser.getRole() == UserRole.PRODUCTION_MANAGER;
            boolean isActive = assignedUser.getStatus() == UserStatus.ACTIVE;
            if (!isProductionManager || !isActive) {
                throw new IllegalArgumentException("사용 중인 생산 현장 관리자만 담당자로 배정할 수 있습니다.");
            }
            workOrder.changeUser(assignedUser);
        }

        // 계획 조회는 수정자 이름을 넣기 전에 한다. 조회가 flush되면 수정자 이름이 SYSTEM으로 바뀐다.
        List<ProductionPlanDto.Response> plans = plansOf(workOrder);
        workOrder.setUpdatedBy(modifier.getName());
        // 바꾸지 않은 라인·담당자는 변경 전과 같은 값을 남긴다.
        workOrderHistoryRepository.save(new WorkOrderHistory(
                workOrder,
                previousLine,
                workOrder.getLine(),
                previousUser,
                workOrder.getUser(),
                modifier,
                historyReason
        ));
        return WorkOrderDto.DetailResponse.from(workOrder, plans);
    }

    /**
     * 특정 작업지시서의 라인 및 담당자 변경 이력 조회
     *
     * @param workOrderId 작업지시서 ID
     * @return 최근 변경 이력 최대 5개
     */
    public List<WorkOrderHistoryDto.Response> getWorkOrderHistories(
            Long workOrderId
    ) {

        // 해당 작업지시서의 변경 이력 최근 5개 조회
        return workOrderHistoryRepository
                .findTop5ByWorkOrder_IdOrderByChangedAtDesc(workOrderId)
                .stream()
                .map(WorkOrderHistoryDto.Response::from)
                .toList();
    }

    @Transactional
    public WorkOrderDto.DetailResponse changeStatus(Long workOrderId,
                                                    WorkOrderDto.UpdateStatusRequest request,
                                                    Long userId) {
        // 수정할 직원
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("수정한 직원을 찾을 수 없습니다."));

        // 수정할 작업지시서 조회
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new NoSuchElementException("해당 작업을 찾을 수 없습니다."));
        // 최고 관리자이거나, 이 작업에 배정된 생산 현장 관리자만 상태를 바꾼다.
        boolean isAssignee = workOrder.getUser() != null && workOrder.getUser().getId().equals(user.getId());
        boolean canChangeStatus = user.getRole() == UserRole.SUPER_ADMIN
                || (user.getRole() == UserRole.PRODUCTION_MANAGER && isAssignee);
        if (!canChangeStatus) {
            throw new AccessDeniedException("배정된 생산 현장 관리자와 최고 관리자만 작업 상태를 바꿀 수 있습니다.");
        }
        List<ProductionPlanDto.Response> plans = plansOf(workOrder);

        // 변경 전 작업 상태
        CommonCode beforeCode = workOrder.getCommonCode();

        // 변경 후 작업 상태. 그룹 WORK_ORDER_STATUS, 요청 코드, 활성화 상태 코드
        CommonCode afterCode = commonCodeRepository
                .findByCodeGroup_GroupCodeAndCodeAndStatus(
                        "WORK_ORDER_STATUS", request.status(), CodeStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("작업 상태 코드를 찾을 수 없습니다."));

        // 변경 전·후 코드를 구한 다음 바로 아래에서 검증
        validateStatusTransition(workOrder, beforeCode, afterCode);

        // 요청 상태에 따라 라인과 수주를 바꾼 뒤 작업 상태를 저장한다.
        processStatusChange(workOrder, afterCode, user);
        workOrder.changeStatus(afterCode);
        workOrder.setUpdatedBy(user.getName());
        StatusHistory history = new StatusHistory(
                HistoryEntityType.WORK_ORDER,
                workOrder.getId(),
                beforeCode.getCode(),
                afterCode.getCode(),
                user
        );
        // 생산 중지 사유는 상태 이력 note에 남긴다. 작업지시 본문 note는 그대로 둔다.
        history.writeNote(request.note());
        statusHistoryRepository.save(history);
        return WorkOrderDto.DetailResponse.from(workOrder, plans);
    }

    // 상태 전이가 가능한지 검증하는 로직
    private void validateStatusTransition(WorkOrder workOrder, CommonCode beforeCode, CommonCode afterCode) {
        String before = beforeCode.getCode();
        String after = afterCode.getCode();
        // 동일한 상태를 체크하는 로직
        if (before.equals(after)) {
            throw new IllegalArgumentException("이미 같은 상태입니다.");
        }
        // 상태 전이를 검증하는 로직
        boolean isAllowed = ALLOWED_TRANSITIONS.getOrDefault(before, Set.of()).contains(after);
        if (!isAllowed) {
            throw new IllegalArgumentException("현재 작업 상태에서는 요청한 상태로 변경할 수 없습니다.");
        }
        // 수주 취소로 멈춘 작업은 준비로 돌아가지 않는다. 생산 중지로 멈춘 작업은 가능하다.
        boolean isResumeAfterHalt = "HALT".equals(before) && "PREPARE".equals(after);
        if (isResumeAfterHalt && "CANCELLED".equals(orderStatus(workOrder))) {
            throw new IllegalArgumentException("취소된 수주의 작업은 다시 준비할 수 없습니다.");
        }
    }

    // 요청받은 작업 상태에 따라 라인과 수주를 바꾼다. 작업지시 상태는 이 호출 뒤에 저장한다.
    private void processStatusChange(WorkOrder workOrder, CommonCode afterCode, User user) {
        switch (afterCode.getCode()) {
            case "PRODUCE" -> startProduction(workOrder, user);
            case "SUSPEND" -> changeOrder(
                    workOrder, otherProduceRemains(workOrder) ? "IN_PRODUCTION" : "ON_HOLD");
            case "HALT" -> haltWork(workOrder);
            case "PACK" -> finishProduction(workOrder, user);
            case "PACK_DONE" -> {
            }
            case "PREPARE" -> changeLineStatus(workOrder.getLine(), LineStatus.AVAILABLE, user);
            default -> throw new IllegalArgumentException("작업 상태 코드를 찾을 수 없습니다.");
        }
    }

    // 생산 시작. 라인은 가동 중이 되고 수주는 생산중이 된다.
    private void startProduction(WorkOrder workOrder, User user) {
        LineStatus lineStatus = workOrder.getLine().getStatus();
        if (lineStatus == LineStatus.BROKEN) {
            throw new IllegalArgumentException("고장 상태인 라인에서는 생산을 시작하거나 재개할 수 없습니다.");
        }
        if (lineStatus == LineStatus.INSPECTION) {
            throw new IllegalArgumentException("점검 중인 라인에서는 생산을 시작하거나 재개할 수 없습니다.");
        }
        if (workOrder.getUser() == null) {
            throw new IllegalArgumentException("담당자를 배정한 후 생산을 시작해주세요.");
        }
        if (workOrder.getPlannedStartDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("생산 시작 예정일 전에는 시작할 수 없습니다.");
        }
        String orderStatus = workOrder.getOrderProduct().getCustomerOrder().getStatusCode().getCode();
        if ("WAITING".equals(orderStatus)) {
            throw new IllegalArgumentException("대기 수주는 생산을 시작할 수 없습니다.");
        }
        boolean otherProduceOnLine = workOrderRepository.existsByLine_IdAndCommonCode_CodeAndIdNot(
                workOrder.getLine().getId(), "PRODUCE", workOrder.getId());
        if (otherProduceOnLine) {
            throw new IllegalArgumentException("이 라인에서 이미 생산 중인 작업이 있습니다.");
        }
        // 중지 뒤 준비로 돌아와도 계획은 그대로다. 줄어든 생산능력보다 큰 날은 시작하지 못한다.
        for (ProductionPlan plan : productionPlanRepository.findByWorkOrder_Id(workOrder.getId())) {
            long occupied = productionPlanRepository.sumQuantityByLineAndDate(
                    workOrder.getLine().getId(), plan.getPlanDate());
            boolean planExceedsLine = plan.getQuantity() > workOrder.getLine().getProductionCapacity();
            boolean dayExceedsLine = occupied > workOrder.getLine().getProductionCapacity();
            if (planExceedsLine || dayExceedsLine) {
                throw new IllegalArgumentException("라인의 생산능력을 초과하여 생산을 시작할 수 없습니다.");
            }
        }
        changeLineStatus(workOrder.getLine(), LineStatus.OPERATING, user);
        changeOrder(workOrder, "IN_PRODUCTION");
    }

    // 생산 중지. 수주가 취소면 생산대기로 되돌리지 않는다.
    private void haltWork(WorkOrder workOrder) {
        if ("CANCELLED".equals(orderStatus(workOrder))) {
            return;
        }
        changeOrder(workOrder, "CONFIRMED");
    }

    // 수주 진행 상태는 ORDER_STATUS 그룹의 코드다.
    private String orderStatus(WorkOrder workOrder) {
        return workOrder.getOrderProduct().getCustomerOrder().getStatusCode().getCode();
    }

    // 생산을 마친다. 같은 라인에 다른 생산이 없으면 라인은 가동 가능이 된다.
    private void finishProduction(WorkOrder workOrder, User user) {
        boolean otherProduceOnLine = workOrderRepository.existsByLine_IdAndCommonCode_CodeAndIdNot(
                workOrder.getLine().getId(), "PRODUCE", workOrder.getId());
        if (!otherProduceOnLine) {
            changeLineStatus(workOrder.getLine(), LineStatus.AVAILABLE, user);
        }
        Long customerOrderId = workOrder.getOrderProduct().getCustomerOrder().getId();
        boolean packingRemains = workOrderRepository
                .existsByOrderProduct_CustomerOrder_IdAndCommonCode_CodeNotInAndIdNot(
                        customerOrderId, List.of("PACK", "PACK_DONE"), workOrder.getId());
        changeOrder(workOrder, packingRemains ? "IN_PRODUCTION" : "PACKING");
    }

    // 이 작업을 빼도 같은 수주에 생산 중인 작업이 있는지
    private boolean otherProduceRemains(WorkOrder workOrder) {
        return workOrderRepository.existsByOrderProduct_CustomerOrder_IdAndCommonCode_CodeAndIdNot(
                workOrder.getOrderProduct().getCustomerOrder().getId(), "PRODUCE", workOrder.getId());
    }

    // 라인 상태가 실제로 바뀔 때만 라인 상태 이력을 남긴다.
    private void changeLineStatus(Line line, LineStatus nextStatus, User user) {
        if (line.getStatus() == nextStatus) {
            return;
        }
        String beforeStatus = line.getStatus().name();
        line.changeStatus(nextStatus);
        statusHistoryRepository.save(new StatusHistory(
                HistoryEntityType.LINE,
                line.getId(),
                beforeStatus,
                nextStatus.name(),
                user
        ));
    }

    // 하루 생산량에서 취소된 수주와 포장 이후 작업을 뺀 계획 합을 뺀 잔여다.
    private int remainingCapacity(Line line, LocalDate date) {
        long occupied = productionPlanRepository.sumQuantityByLineAndDate(line.getId(), date);
        return line.getProductionCapacity() - (int) occupied;
    }

    // 수주 진행 상태를 ORDER_STATUS 코드로 바꾼다.
    private void changeOrder(WorkOrder workOrder, String orderStatus) {
        CommonCode orderCode = commonCodeRepository
                .findByCodeGroup_GroupCodeAndCodeAndStatus("ORDER_STATUS", orderStatus, CodeStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("수주 상태 코드를 찾을 수 없습니다."));
        workOrder.getOrderProduct().getCustomerOrder().changeProgress(orderCode);
    }

    // 저장 전 하루 계획. 날짜와 수량을 한 건으로 둔다.
    private record DayPlan(LocalDate planDate, int quantity) {
    }

    private record DepositAssignment(OrderProduct item, Map<Line, List<DayPlan>> allocations) {
    }
}
