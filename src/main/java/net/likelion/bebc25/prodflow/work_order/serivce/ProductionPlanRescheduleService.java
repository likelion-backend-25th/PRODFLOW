package net.likelion.bebc25.prodflow.work_order.serivce;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.holiday.entity.Holiday;
import net.likelion.bebc25.prodflow.holiday.repository.HolidayRepository;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.line.repsitory.LineRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.enums.UserStatus;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrderHistory;
import net.likelion.bebc25.prodflow.work_order.repsitory.ProductionPlanRepository;
import net.likelion.bebc25.prodflow.work_order.repsitory.WorkOrderHistoryRepository;
import net.likelion.bebc25.prodflow.work_order.repsitory.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 점검에서는 준비 작업만 다른 라인으로 옮긴다. 생산이 있으면 점검으로 바꾸지 않는다.
// 고장에서는 준비와 생산·일시정지를 다른 라인으로 옮긴다.
// 옮길 라인이 없어도 고장 처리는 되고, 그 작업지시는 고장 라인에 남는다.
// 가동 가능으로 돌아올 때는 작업지시를 옮기지 않는다.
// 그 라인으로 작업지시를 직접 옮길 때만, 앞날이 비어 있으면 밀린 일정을 당긴다.
// 다른 라인으로의 일반 변경은 기존 날짜가 들어가는지만 본다.
// 생산능력을 줄여도 다른 라인으로 옮기지 않는다.
@Service
@RequiredArgsConstructor
public class ProductionPlanRescheduleService {

    // 점검·고장으로 원래 완료일 다음에 빈 날을 찾을 때 보는 범위다.
    // 그 안에 한 장이 들어가지 않으면 라인 상태를 바꾸지 않는다.
    private static final int AWAY_SEARCH_DAYS = 90;

    // 재배치가 끝날 때까지 라인 행을 잠근다. 용량을 두 요청이 같이 쓰지 않게 한다.
    private final LineRepository lineRepository;
    // 날짜별 계획. 비우기 전과 저장 뒤에 수량 합을 여기서 확인한다.
    private final ProductionPlanRepository productionPlanRepository;
    // 작업지시 저장, 번호 중복, 담당자의 생산 중 여부.
    private final WorkOrderRepository workOrderRepository;
    // 라인이 바뀐 작업지시에만 이력을 남긴다.
    private final WorkOrderHistoryRepository workOrderHistoryRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    // 예정 기간 안의 공휴일. 주말과 같이 배정에서 뺀다.
    private final HolidayRepository holidayRepository;
    // 지난 계획이 남아 미래만 빠질 때 만드는 새 장의 상태. 등록과 같이 PREPARE다.
    private final CommonCodeRepository commonCodeRepository;
    // 새 장의 담당자. 생산 중인 사람에게 준비 작업을 더 주지 않는다.
    private final UserRepository userRepository;

    // 오늘 이후 준비 계획을 같은 라인의 기간 안에 다시 넣는다.
    // 들어가지 않으면 다른 라인으로 옮기지 않고 생산량 변경을 거절한다.
    @Transactional
    public void rescheduleReducedCapacity(Line line, int newCapacity, User actor, String reason) {
        List<Line> locked = lineRepository.findAllForUpdate();
        // 지난 계획은 날짜를 다시 쓰지 않는다. 오늘부터가 재배치 대상이다.
        LocalDate today = LocalDate.now();
        List<LocalDate> exceeded = productionPlanRepository.findDatesExceedingCapacity(line.getId(), newCapacity)
                .stream()
                .sorted()
                .toList();
        // 줄어든 용량에도 모든 날이 들어가면 계획을 건드리지 않는다.
        if (exceeded.isEmpty()) {
            return;
        }
        for (LocalDate date : exceeded) {
            // 어제 이전은 준비 작업이어도 옮기지 않는다. 넘치면 생산량 변경 자체가 실패한다.
            if (date.isBefore(today)) {
                throw new IllegalArgumentException("기존 생산계획이 새 생산능력을 초과합니다.");
            }
        }
        // 이 라인의 오늘 이후 준비 계획 전부다. 넘는 날만 자르지 않고 한 장 단위로 다시 놓는다.
        List<ProductionPlan> targets = productionPlanRepository.findRescheduleTargets(line.getId(), today);
        Map<LocalDate, Integer> prepareByDate = new HashMap<>();
        for (ProductionPlan plan : targets) {
            prepareByDate.merge(plan.getPlanDate(), plan.getQuantity(), Integer::sum);
        }
        for (LocalDate date : exceeded) {
            long occupied = productionPlanRepository.sumQuantityByLineAndDate(line.getId(), date);
            int prepare = prepareByDate.getOrDefault(date, 0);
            // 생산 중·일시 정지·생산 중지는 비우지 않는다. 그 합만으로도 넘치면 생산량을 줄이지 않는다.
            if (occupied - prepare > newCapacity) {
                throw new IllegalArgumentException("기존 생산계획이 새 생산능력을 초과합니다.");
            }
        }
        // 줄어드는 라인만 새 생산능력을 쓴다. 다른 라인은 후보가 아니다.
        placeCleared(locked, line, targets, newCapacity, false, actor, reason,
                "기존 생산계획이 새 생산능력을 초과합니다.", false);
    }

    // 점검 라인의 오늘 이후 준비 계획을 다른 라인으로 옮긴다.
    // 원래 완료일까지 빈 곳이 없으면 그 다음 영업일로 한 장을 민다. 시작일은 실제로 들어간 첫날이다.
    // 생산·일시 정지가 남아 있으면 계획을 비우지 않고 점검으로 바꾸지 않는다.
    @Transactional
    public void rescheduleAway(Line line, LocalDate fromDate, User actor, String reason) {
        if (productionPlanRepository.countInProgressPlans(line.getId(), fromDate) > 0) {
            // 생산·일시 정지는 점검에서 자동으로 빼지 않는다.
            throw new IllegalArgumentException("생산 중인 작업이 있어 라인을 점검으로 바꿀 수 없습니다.");
        }
        List<Line> locked = lineRepository.findAllForUpdate();
        List<ProductionPlan> targets = productionPlanRepository.findRescheduleTargets(line.getId(), fromDate);
        // 옮길 준비가 없으면 상태만 바뀌면 된다.
        if (targets.isEmpty()) {
            return;
        }
        // 점검·고장 라인은 후보에서 뺀다. 완료일 다음 날까지 한 장이 들어갈 곳을 찾는다.
        placeCleared(locked, line, targets, null, true, actor, reason, "재배치할 생산 가능량이 없습니다.", false);
    }

    // 고장은 준비뿐 아니라 생산·일시정지도 다른 라인으로 옮긴다.
    // 점검은 생산이 남아 있으면 상태를 바꾸지 않는다.
    @Transactional
    public void rescheduleBroken(Line line, LocalDate fromDate, User actor, String reason) {
        List<Line> locked = lineRepository.findAllForUpdate();
        List<ProductionPlan> targets = productionPlanRepository.findBrokenTargets(line.getId(), fromDate);
        if (!targets.isEmpty()) {
            // 자리가 없는 작업은 고장 라인에 남긴다. 그래서 고장 처리가 거절되지 않는다.
            placeCleared(locked, line, targets, null, true, actor, reason, "재배치할 생산 가능량이 없습니다.", true);
        }
        workOrderRepository.flush();
        // 오늘 이후 계획이 없는 생산·일시정지도 가능하면 다른 라인으로 옮긴다.
        for (WorkOrder workOrder : workOrderRepository.findByLine_IdAndCommonCode_CodeIn(
                line.getId(), List.of("PRODUCE", "SUSPEND"))) {
            if ("CANCELLED".equals(workOrder.getOrderProduct().getCustomerOrder().getStatusCode().getCode())) {
                continue;
            }
            Line destination = lineForInProgress(locked, line, workOrder);
            // 받을 라인이 없으면 그 작업은 남겨 두고 고장 처리는 계속한다.
            if (destination == null) {
                continue;
            }
            moveLine(new Context(actor, reason, Set.of(), "재배치할 생산 가능량이 없습니다.",
                    workOrderRepository.count() + 1, line.getId(), null), workOrder, destination);
            markOperating(actor, destination);
        }
    }

    // 복구 라인으로 직접 옮길 때, 원래 시작일부터 현재 시작일 전이 비어 있으면 일정을 당긴다.
    // 그 앞에 다른 작업이 있으면 밀린 날짜를 유지한다.
    public boolean shouldPullForward(WorkOrder workOrder, Line line) {
        Displacement displacement = latestDisplacement(workOrder, line);
        if (displacement == null) {
            return false;
        }
        LocalDate from = pullStart(displacement);
        LocalDate currentStart = workOrder.getPlannedStartDate();
        if (currentStart == null || !currentStart.isAfter(from)) {
            return false;
        }
        Set<LocalDate> holidays = new HashSet<>();
        for (Holiday holiday : holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(from, currentStart)) {
            holidays.add(holiday.getHolidayDate());
        }
        for (LocalDate date = from; date.isBefore(currentStart); date = date.plusDays(1)) {
            if (isClosed(date, holidays)) {
                continue;
            }
            // 앞날에 다른 작업이 있으면 그 작업 앞으로 당기지 않는다.
            if (productionPlanRepository.sumQuantityByLineAndDate(line.getId(), date) > 0) {
                return false;
            }
        }
        return true;
    }

    // 복구된 라인으로 다시 넣는 작업지시만 일정을 다시 잡는다.
    // 오늘과 원래 시작 예정일 중 늦은 날부터 빈 자리만큼 넣고, 남은 수량은 다음 영업일로 넘긴다.
    @Transactional
    public void replanOnRecoveredLine(WorkOrder workOrder, Line destination, User actor) {
        Displacement displacement = latestDisplacement(workOrder, destination);
        if (displacement == null) {
            throw new IllegalArgumentException("복구된 라인으로의 재이동이 아닙니다.");
        }
        if ("PRODUCE".equals(workOrder.getCommonCode().getCode())
                && !canHostProduce(Set.of(), destination, workOrder)) {
            throw new IllegalArgumentException("이 라인에서 이미 생산 중인 작업이 있습니다.");
        }
        lineRepository.findAllForUpdate();
        LocalDate today = LocalDate.now();
        List<ProductionPlan> futurePlans = new ArrayList<>();
        int futureQuantity = 0;
        for (ProductionPlan plan : productionPlanRepository.findByWorkOrder_Id(workOrder.getId())) {
            // 지난 계획은 고치지 않는다. 오늘 이후 수량만 다시 넣는다.
            if (plan.getPlanDate().isBefore(today)) {
                continue;
            }
            futurePlans.add(plan);
            futureQuantity += plan.getQuantity();
        }
        if (futureQuantity <= 0) {
            workOrder.changeLine(destination);
            return;
        }
        for (ProductionPlan plan : futurePlans) {
            productionPlanRepository.delete(plan);
        }
        // 빼기 전 계획이 복구 라인 잔여에 섞이지 않게 반영한 뒤에 날짜를 고른다.
        productionPlanRepository.flush();
        // 원래 시작일이 아직 남았으면 그 날부터다. 이미 지났으면 오늘부터다.
        LocalDate from = pullStart(displacement);
        LocalDate limit = from.plusDays(AWAY_SEARCH_DAYS);
        Set<LocalDate> holidays = new HashSet<>();
        for (Holiday holiday : holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(from, limit)) {
            holidays.add(holiday.getHolidayDate());
        }
        Map<LocalDate, Integer> packed = new LinkedHashMap<>();
        int remaining = futureQuantity;
        for (LocalDate date = from; !date.isAfter(limit) && remaining > 0; date = date.plusDays(1)) {
            if (isClosed(date, holidays)) {
                continue;
            }
            long occupied = productionPlanRepository.sumQuantityByLineAndDate(destination.getId(), date);
            int space = destination.getProductionCapacity() - (int) Math.min(occupied, destination.getProductionCapacity());
            if (space <= 0) {
                continue;
            }
            int assigned = Math.min(space, remaining);
            packed.put(date, assigned);
            remaining -= assigned;
        }
        if (remaining > 0) {
            throw new IllegalArgumentException("변경할 라인의 생산능력을 초과합니다.");
        }
        savePlans(workOrder, packed, actor);
        workOrder.changeLine(destination);
        // 예정일은 실제로 다시 넣은 첫날과 마지막 날이다. 밀렸던 날짜를 복사하지 않는다.
        refreshQuantityAndDates(workOrder);
    }

    // 이 작업을 그 라인에서 고장·점검으로 빼낸 가장 최근 이력이다.
    private Displacement latestDisplacement(WorkOrder workOrder, Line line) {
        if (line.getId().equals(workOrder.getLine().getId())) {
            return null;
        }
        for (WorkOrderHistory history : workOrderHistoryRepository
                .findByWorkOrder_IdAndPreviousLine_IdOrderByIdDesc(workOrder.getId(), line.getId())) {
            String reason = history.getChangeReason();
            if (reason == null) {
                continue;
            }
            if (!reason.startsWith("라인 고장으로 재배치") && !reason.startsWith("라인 점검으로 재배치")) {
                continue;
            }
            return new Displacement(originalStartOf(reason));
        }
        return null;
    }

    // 일정을 당길 수 있는 첫날이다. 원래 시작일이 남았으면 그 날이고, 지났으면 오늘이다.
    private LocalDate pullStart(Displacement displacement) {
        LocalDate today = LocalDate.now();
        if (displacement.originalStart != null && displacement.originalStart.isAfter(today)) {
            return displacement.originalStart;
        }
        return today;
    }

    // 빠질 때 붙여 둔 원래 시작일이다. 없으면 오늘부터 다시 계산한다.
    private LocalDate originalStartOf(String reason) {
        String[] parts = reason.split("\\|");
        if (parts.length < 2) {
            return null;
        }
        try {
            return LocalDate.parse(parts[1]);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    // 고장·점검으로 빠지는 이력에 원래 시작·완료일을 남긴다. 복귀 때 그 시작일부터 다시 본다.
    private String withOriginalWindow(String reason, LocalDate start, LocalDate end) {
        if (reason == null || start == null || end == null) {
            return reason;
        }
        if (!reason.startsWith("라인 고장으로 재배치") && !reason.startsWith("라인 점검으로 재배치")) {
            return reason;
        }
        return reason + "|" + start + "|" + end;
    }

    // 작업지시 라인 변경은 재배치하지 않는다. 도착 라인의 그날 잔여보다 크면 이동을 거절한다.
    public void assertFits(WorkOrder workOrder, Line line) {
        String orderStatus = workOrder.getOrderProduct().getCustomerOrder().getStatusCode().getCode();
        if ("CANCELLED".equals(orderStatus)) {
            // 취소된 수주는 용량을 차지하지 않으므로 라인 이동을 막지 않는다.
            return;
        }
        if (line.getId().equals(workOrder.getLine().getId())) {
            // 같은 라인이면 용량이 달라지지 않는다.
            return;
        }
        for (ProductionPlan plan : productionPlanRepository.findByWorkOrder_Id(workOrder.getId())) {
            long occupied = productionPlanRepository.sumQuantityByLineAndDate(line.getId(), plan.getPlanDate());
            if (occupied + plan.getQuantity() > line.getProductionCapacity()) {
                throw new IllegalArgumentException("변경할 라인의 생산능력을 초과합니다.");
            }
        }
    }

    // 수량·기간·우선순위를 보관한 뒤 대상 계획을 비우고 다시 넣는다.
    // relocate가 아니면 같은 라인만 본다. 점검·고장이면 다른 라인으로 옮긴다.
    // 고장은 keepWhenUnplaced다. 받을 곳이 없는 작업은 지우지 않고 그 라인에 남긴다.
    private void placeCleared(List<Line> locked,
                              Line source,
                              List<ProductionPlan> targets,
                              Integer sourceCapacity,
                              boolean relocate,
                              User actor,
                              String reason,
                              String failureMessage,
                              boolean keepWhenUnplaced) {
        List<Job> jobs = snapshot(targets);
        // 비우기 전에 품목별 계획 합을 남긴다. 다시 넣은 뒤에도 이 값이어야 한다.
        Map<Long, Long> quantityBefore = quantityBefore(jobs);
        LocalDate today = LocalDate.now();
        // 완료 예정일이 빠른 장이 먼저다. 먼저 끝내야 하는 작업이 자리를 먼저 고른다.
        // 완료일이 같으면 시작 예정일이 빠른 장, 날짜까지 같으면 id가 작은 장이다.
        jobs.sort(Comparator
                .comparing((Job job) -> job.end)
                .thenComparing(job -> job.start)
                .thenComparing(job -> job.workOrder.getId()));
        if (!keepWhenUnplaced) {
            for (ProductionPlan plan : targets) {
                productionPlanRepository.delete(plan);
            }
            // 비운 수량이 잔여 계산에 다시 섞이지 않게 반영한 뒤에 라인을 고른다.
            productionPlanRepository.flush();
        }
        Context context = new Context(actor, reason, holidaysOf(jobs, today, relocate), failureMessage,
                workOrderRepository.count() + 1, source.getId(), sourceCapacity);
        List<Job> placed = new ArrayList<>();
        for (Job job : jobs) {
            // 지난 시작일은 당기지 않는다. 오늘보다 이른 기간은 오늘부터 본다.
            LocalDate from = job.start.isBefore(today) ? today : job.start;
            job.destination = chooseLine(locked, source, relocate, context, job, from);
            if (job.destination == null) {
                if (keepWhenUnplaced) {
                    continue;
                }
                throw new IllegalArgumentException(failureMessage);
            }
            // 고른 끝까지 하루 잔여로 채운다. 같은 날 다른 라인으로 나누지 않는다.
            job.packed = pack(context, job.destination, job.quantity, from, job.packUntil);
            if (job.packed == null) {
                job.destination = null;
                if (keepWhenUnplaced) {
                    continue;
                }
                throw new IllegalArgumentException(failureMessage);
            }
            if ("PRODUCE".equals(job.statusCode)) {
                context.claimedProduce.add(job.destination.getId());
            }
            // 아직 저장 전이다. 다음 작업지시가 이 자리를 다시 쓰지 않게 잡아 둔다.
            for (Map.Entry<LocalDate, Integer> entry : job.packed.entrySet()) {
                context.reserve(job.destination.getId(), entry.getKey(), entry.getValue());
            }
            placed.add(job);
        }
        if (keepWhenUnplaced) {
            Set<Long> placedIds = new HashSet<>();
            for (Job job : placed) {
                placedIds.add(job.workOrder.getId());
            }
            for (ProductionPlan plan : targets) {
                // 자리를 찾은 장만 비운다. 못 옮긴 장의 계획은 고장 라인에 남긴다.
                if (placedIds.contains(plan.getWorkOrder().getId())) {
                    productionPlanRepository.delete(plan);
                }
            }
            if (!placedIds.isEmpty()) {
                productionPlanRepository.flush();
            }
        }
        CommonCode prepare = prepareCode();
        for (Job job : placed) {
            apply(context, job, prepare);
        }
        for (Map.Entry<Long, Long> entry : quantityBefore.entrySet()) {
            // 라인을 옮겨도 품목의 계획 수량 합은 재배치 전과 같아야 한다.
            long after = productionPlanRepository.sumQuantityByOrderProductId(entry.getKey());
            if (after != entry.getValue()) {
                throw new IllegalArgumentException("재배치 전후 계획 수량 합계가 다릅니다.");
            }
        }
        for (Job job : placed) {
            int capacity = context.capacityOf(job.destination);
            for (LocalDate date : job.packed.keySet()) {
                long occupied = productionPlanRepository.sumQuantityByLineAndDate(job.destination.getId(), date);
                // 다시 넣은 날을 포함한 점유가 그 라인의 생산능력을 넘으면 전부 되돌린다.
                if (occupied > capacity) {
                    throw new IllegalArgumentException(failureMessage);
                }
            }
        }
    }

    // 작업지시 한 장에 옮길 수량과 원래 기간을 모아 둔다. 계획은 아직 지우기 전이다.
    private List<Job> snapshot(List<ProductionPlan> targets) {
        Map<Long, Job> jobs = new LinkedHashMap<>();
        for (ProductionPlan plan : targets) {
            Job job = jobs.computeIfAbsent(plan.getWorkOrder().getId(), id -> new Job(plan.getWorkOrder()));
            // 같은 장의 여러 날짜는 한 수량으로 합친다. 날짜마다 새 장을 만들지 않는다.
            job.quantity += plan.getQuantity();
        }
        return new ArrayList<>(jobs.values());
    }

    private Map<Long, Long> quantityBefore(List<Job> jobs) {
        Map<Long, Long> quantityBefore = new HashMap<>();
        for (Job job : jobs) {
            Long orderProductId = job.workOrder.getOrderProduct().getId();
            quantityBefore.computeIfAbsent(
                    orderProductId, productionPlanRepository::sumQuantityByOrderProductId);
        }
        return quantityBefore;
    }

    // 생산능력 감소는 같은 라인만 본다. 점검·고장은 다른 가동 라인을 보고, 완료일 다음도 본다.
    private Line chooseLine(List<Line> locked,
                            Line source,
                            boolean relocate,
                            Context context,
                            Job job,
                            LocalDate from) {
        if (!relocate) {
            if (pack(context, source, job.quantity, from, job.end) == null) {
                return null;
            }
            job.packUntil = job.end;
            return source;
        }
        List<Line> candidates = new ArrayList<>();
        for (Line candidate : locked) {
            if (candidate.getId().equals(source.getId())) {
                continue;
            }
            // 점검·고장 라인은 받지 않는다.
            if (candidate.getStatus() != LineStatus.AVAILABLE && candidate.getStatus() != LineStatus.OPERATING) {
                continue;
            }
            // 생산 중인 작업은 이미 생산이 있는 라인으로 옮기지 않는다.
            if ("PRODUCE".equals(job.statusCode) && !canHostProduce(context.claimedProduce, candidate, job.workOrder)) {
                continue;
            }
            candidates.add(candidate);
        }
        candidates.sort(Comparator
                // 배치를 시작하는 날의 빈 용량이 큰 라인부터다. 같으면 id가 작은 라인이다.
                .comparingInt((Line line) -> space(context, line, from))
                .reversed()
                .thenComparing(Line::getId));
        Line withinPeriod = firstThatFits(candidates, context, job, from, job.end);
        if (withinPeriod != null) {
            job.packUntil = job.end;
            return withinPeriod;
        }
        // 원래 완료일까지는 한 장이 들어가지 않는다. 고장 라인에 남기지 않고 다음 영업일을 본다.
        LocalDate extended = from.plusDays(AWAY_SEARCH_DAYS);
        Line later = firstThatFits(candidates, context, job, from, extended);
        if (later == null) {
            return null;
        }
        job.packUntil = extended;
        return later;
    }

    private Line firstThatFits(List<Line> candidates,
                               Context context,
                               Job job,
                               LocalDate from,
                               LocalDate end) {
        for (Line candidate : candidates) {
            if (pack(context, candidate, job.quantity, from, end) != null) {
                return candidate;
            }
        }
        return null;
    }

    // 한 라인의 기간 안에 수량을 하루씩 채운다. 다 못 채우면 null이다.
    // 같은 날 다른 라인으로 나누지 않는다. 남은 수량은 다음 영업일이다.
    private Map<LocalDate, Integer> pack(Context context,
                                         Line line,
                                         int quantity,
                                         LocalDate from,
                                         LocalDate end) {
        if (from.isAfter(end)) {
            return null;
        }
        Map<LocalDate, Integer> packed = new LinkedHashMap<>();
        int left = quantity;
        LocalDate cursor = from;
        while (!cursor.isAfter(end) && left > 0) {
            if (!isClosed(cursor, context.holidays)) {
                int room = space(context, line, cursor);
                if (room > 0) {
                    int take = Math.min(room, left);
                    packed.merge(cursor, take, Integer::sum);
                    left -= take;
                }
            }
            cursor = cursor.plusDays(1);
        }
        if (left > 0) {
            return null;
        }
        return packed;
    }

    // 한 장이 한 라인에 다시 들어간다. 지난 계획만 원본에 남을 때 미래 수량만 새 장이 된다.
    private void apply(Context context, Job job, CommonCode prepare) {
        WorkOrder workOrder = job.workOrder;
        List<ProductionPlan> remaining = productionPlanRepository.findByWorkOrder_Id(workOrder.getId());
        boolean sameLine = job.destination.getId().equals(workOrder.getLine().getId());
        // 생산·일시정지는 지난 계획이 있어도 작업지시 행을 고장 라인에 남기지 않는다.
        boolean inProgress = "PRODUCE".equals(job.statusCode) || "SUSPEND".equals(job.statusCode);
        if (remaining.isEmpty() || sameLine || inProgress) {
            if (!sameLine) {
                moveLine(context, workOrder, job.destination);
                if (inProgress) {
                    markOperating(context.actor, job.destination);
                }
            }
            savePlans(workOrder, job.packed, context.actor);
            refreshQuantityAndDates(workOrder);
            return;
        }
        // 어제 이전 계획은 원래 라인에 남긴다. 오늘 이후만 다른 라인의 새 작업지시다.
        // 새 장의 시작일은 다시 넣은 첫날이다. 원래 시작 예정일을 복사하지 않는다.
        refreshQuantityAndDates(workOrder);
        spawn(context, workOrder, job.destination, job.packed, prepare, job.start, job.end);
    }

    // 담당자는 그대로 둔 채 라인만 바꾸고 이력을 남긴다.
    private void moveLine(Context context, WorkOrder workOrder, Line destination) {
        Line previous = workOrder.getLine();
        assertAssignable(workOrder.getUser());
        workOrder.changeLine(destination);
        workOrderHistoryRepository.save(new WorkOrderHistory(
                workOrder,
                previous,
                destination,
                workOrder.getUser(),
                workOrder.getUser(),
                context.actor,
                // 예정일을 밀기 전에 원래 기간을 남긴다. 복귀 이동이 이 시작일부터 다시 계산한다.
                withOriginalWindow(context.reason, workOrder.getPlannedStartDate(), workOrder.getPlannedEndDate())
        ));
    }

    // 생산 작업은 그 라인의 다른 생산과 겹치지 않는다.
    private boolean canHostProduce(Set<Long> claimedProduce, Line line, WorkOrder workOrder) {
        if (claimedProduce.contains(line.getId())) {
            return false;
        }
        return !workOrderRepository.existsByLine_IdAndCommonCode_CodeAndIdNot(
                line.getId(), "PRODUCE", workOrder.getId());
    }

    // 미래 계획이 없는 생산·일시정지를 받을 가동 라인이다.
    private Line lineForInProgress(List<Line> locked, Line source, WorkOrder workOrder) {
        boolean produce = "PRODUCE".equals(workOrder.getCommonCode().getCode());
        for (Line candidate : locked) {
            if (candidate.getId().equals(source.getId())) {
                continue;
            }
            if (candidate.getStatus() != LineStatus.AVAILABLE && candidate.getStatus() != LineStatus.OPERATING) {
                continue;
            }
            if (produce && !canHostProduce(Set.of(), candidate, workOrder)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    // 생산·일시정지가 옮겨 가면 받는 라인은 가동 중이 된다.
    private void markOperating(User actor, Line line) {
        if (line.getStatus() == LineStatus.OPERATING) {
            return;
        }
        String before = line.getStatus().name();
        line.changeStatus(LineStatus.OPERATING);
        statusHistoryRepository.save(new StatusHistory(
                HistoryEntityType.LINE,
                line.getId(),
                before,
                LineStatus.OPERATING.name(),
                actor));
    }

    private void savePlans(WorkOrder workOrder, Map<LocalDate, Integer> quantities, User actor) {
        for (Map.Entry<LocalDate, Integer> entry : quantities.entrySet()) {
            ProductionPlan plan = new ProductionPlan(workOrder, entry.getKey(), entry.getValue());
            // 로그인 주체가 없으면 감사가 SYSTEM으로 채운다. 재배치를 시킨 사람 이름을 먼저 넣는다.
            plan.setCreatedBy(actor.getName());
            plan.setUpdatedBy(actor.getName());
            productionPlanRepository.save(plan);
        }
        productionPlanRepository.flush();
    }

    // 지난 계획이 남은 원본에서 떨어진 미래 수량이다. 시작일은 다시 배치한 첫날이다.
    private void spawn(Context context,
                       WorkOrder source,
                       Line destination,
                       Map<LocalDate, Integer> quantities,
                       CommonCode prepare,
                       LocalDate originalStart,
                       LocalDate originalEnd) {
        User assignee = assigneeFor(source.getUser());
        assertAssignable(assignee);
        LocalDate start = quantities.keySet().stream().min(Comparator.naturalOrder()).orElseThrow();
        LocalDate end = quantities.keySet().stream().max(Comparator.naturalOrder()).orElseThrow();
        int quantity = quantities.values().stream().mapToInt(Integer::intValue).sum();
        String workNo = nextWorkNo(context);
        String note = source.getOrderProduct().getProductName()
                + " "
                + destination.getLineName()
                + " "
                + start
                + " ~ "
                + end;
        WorkOrder created = new WorkOrder(
                workNo,
                source.getOrderProduct(),
                destination,
                assignee,
                quantity,
                start,
                end,
                note);
        created.setCreatedBy(context.actor.getName());
        created.setUpdatedBy(context.actor.getName());
        created.changeStatus(prepare);
        WorkOrder saved = workOrderRepository.save(created);
        savePlans(saved, quantities, context.actor);
        refreshQuantityAndDates(saved);
        // 새 장도 어느 라인에서 빠졌는지 남긴다. 그 라인으로 다시 넣을 때 원래 시작일부터 계산한다.
        workOrderHistoryRepository.save(new WorkOrderHistory(
                saved,
                source.getLine(),
                destination,
                source.getUser(),
                assignee,
                context.actor,
                withOriginalWindow(context.reason, originalStart, originalEnd)
        ));
    }

    // 작업지시 수량은 그 장의 계획 합이고, 기간은 남은 계획의 처음과 마지막 날짜다.
    private void refreshQuantityAndDates(WorkOrder workOrder) {
        List<ProductionPlan> plans = productionPlanRepository.findByWorkOrder_Id(workOrder.getId());
        int quantity = 0;
        LocalDate start = null;
        LocalDate end = null;
        for (ProductionPlan plan : plans) {
            quantity += plan.getQuantity();
            if (start == null || plan.getPlanDate().isBefore(start)) {
                start = plan.getPlanDate();
            }
            if (end == null || plan.getPlanDate().isAfter(end)) {
                end = plan.getPlanDate();
            }
        }
        if (quantity <= 0 || start == null) {
            throw new IllegalArgumentException("재배치 후 작업지시 수량이 남지 않았습니다.");
        }
        workOrder.changeQuantity(quantity);
        workOrder.changePlannedDates(start, end);
        // 계획 합과 작업지시 수량이 다르면 품목 합계 검증보다 먼저 막는다.
        if (workOrder.getQuantity() != quantity) {
            throw new IllegalArgumentException("재배치 전후 계획 수량 합계가 다릅니다.");
        }
    }

    // 원래 담당자가 생산 중이면 그 사람에게 새 준비 장을 주지 않는다.
    private User assigneeFor(User current) {
        if (canTakePrepare(current)) {
            return current;
        }
        User chosen = null;
        long least = Long.MAX_VALUE;
        for (User user : userRepository.findAll()) {
            if (!canTakePrepare(user)) {
                continue;
            }
            long load = workOrderRepository.sumPrepareQuantityByUserId(user.getId());
            // 준비 수량 합이 가장 적은 사람이다. 같으면 이름 순이다.
            if (chosen == null || load < least || (load == least && user.getName().compareTo(chosen.getName()) < 0)) {
                chosen = user;
                least = load;
            }
        }
        if (chosen == null) {
            throw new IllegalArgumentException("사용 중인 생산 현장 관리자만 담당자로 배정할 수 있습니다.");
        }
        return chosen;
    }

    // 생산 중인 담당자에게 준비 작업을 하나 더 만들지 않는다.
    private boolean canTakePrepare(User user) {
        if (user.getRole() != UserRole.PRODUCTION_MANAGER || user.getStatus() != UserStatus.ACTIVE) {
            return false;
        }
        return !workOrderRepository.existsByUser_IdAndCommonCode_Code(user.getId(), "PRODUCE");
    }

    // count()만 쓰면 이미 있는 WO-연도-번호와 겹칠 수 있다. 없을 때까지 올린다.
    private String nextWorkNo(Context context) {
        String workNo;
        do {
            workNo = "WO-" + Year.now().getValue() + "-" + String.format("%03d", context.nextNo);
            context.nextNo++;
        } while (workOrderRepository.existsByWorkNo(workNo));
        return workNo;
    }

    // 저장분에 잡은 수량까지 뺀 그날의 빈 자리다. 줄어드는 라인은 새 생산능력을 쓴다.
    private int space(Context context, Line line, LocalDate date) {
        int used = (int) productionPlanRepository.sumQuantityByLineAndDate(line.getId(), date);
        int reserved = context.reserved
                .getOrDefault(line.getId(), Map.of())
                .getOrDefault(date, 0);
        return context.capacityOf(line) - used - reserved;
    }

    // 배정 가능한 사람은 사용 중인 생산 현장 관리자다.
    private void assertAssignable(User assignee) {
        boolean isProductionManager = assignee.getRole() == UserRole.PRODUCTION_MANAGER;
        boolean isActive = assignee.getStatus() == UserStatus.ACTIVE;
        if (!isProductionManager || !isActive) {
            throw new IllegalArgumentException("사용 중인 생산 현장 관리자만 담당자로 배정할 수 있습니다.");
        }
    }

    // 새 작업지시도 등록과 같이 준비로 시작한다. 코드 PREPARE만 본다.
    private CommonCode prepareCode() {
        return commonCodeRepository
                .findByCodeGroup_GroupCodeAndStatus("WORK_ORDER_STATUS", CodeStatus.ACTIVE)
                .stream()
                .filter(code -> "PREPARE".equals(code.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("준비 상태 코드를 찾을 수 없습니다."));
    }

    // 배치를 시작하는 날부터 찾을 마지막 날까지의 공휴일이다.
    private Set<LocalDate> holidaysOf(List<Job> jobs, LocalDate today, boolean relocate) {
        LocalDate earliest = today;
        LocalDate latest = today;
        for (Job job : jobs) {
            LocalDate from = job.start.isBefore(today) ? today : job.start;
            if (from.isBefore(earliest)) {
                earliest = from;
            }
            LocalDate end = relocate ? from.plusDays(AWAY_SEARCH_DAYS) : job.end;
            if (job.end.isAfter(end)) {
                end = job.end;
            }
            if (end.isAfter(latest)) {
                latest = end;
            }
        }
        Set<LocalDate> holidays = new HashSet<>();
        for (Holiday holiday : holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(earliest, latest)) {
            holidays.add(holiday.getHolidayDate());
        }
        return holidays;
    }

    // 등록과 같이 주말과 공휴일에는 넣지 않는다.
    private boolean isClosed(LocalDate date, Set<LocalDate> holidays) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY || holidays.contains(date);
    }

    // 고장·점검으로 빠질 때 남긴 원래 시작일이다. 없으면 오늘부터 다시 계산한다.
    private static final class Displacement {
        private final LocalDate originalStart;

        private Displacement(LocalDate originalStart) {
            this.originalStart = originalStart;
        }
    }

    // 한 번의 재배치에서 공유하는 값이다. 예약 수량은 저장 전 용량 계산에만 쓴다.
    private static final class Context {
        private final User actor;
        private final String reason;
        private final Set<LocalDate> holidays;
        private final String failureMessage;
        // 라인 id → 날짜 → 아직 저장하지 않은 수량.
        private final Map<Long, Map<LocalDate, Integer>> reserved = new HashMap<>();
        // 이번 재배치에서 생산 작업을 받은 라인. 한 라인에 생산은 하나다.
        private final Set<Long> claimedProduce = new HashSet<>();
        private long nextNo;
        // 생산능력을 줄이는 라인. 이 라인만 sourceCapacity를 쓴다.
        private final Long sourceLineId;
        private final Integer sourceCapacity;

        private Context(User actor,
                        String reason,
                        Set<LocalDate> holidays,
                        String failureMessage,
                        long nextNo,
                        Long sourceLineId,
                        Integer sourceCapacity) {
            this.actor = actor;
            this.reason = reason;
            this.holidays = holidays;
            this.failureMessage = failureMessage;
            this.nextNo = nextNo;
            this.sourceLineId = sourceLineId;
            this.sourceCapacity = sourceCapacity;
        }

        // 용량을 줄이는 라인은 아직 저장 전이라 요청한 생산능력을 쓴다.
        private int capacityOf(Line line) {
            if (sourceCapacity != null && line.getId().equals(sourceLineId)) {
                return sourceCapacity;
            }
            return line.getProductionCapacity();
        }

        private void reserve(Long lineId, LocalDate date, int quantity) {
            reserved.computeIfAbsent(lineId, id -> new HashMap<>())
                    .merge(date, quantity, Integer::sum);
        }
    }

    // 재배치 대상 작업지시 한 장. 수량과 기간은 계획을 비우기 전에 보관한다.
    private static final class Job {
        private final WorkOrder workOrder;
        // 이 장에서 비울 계획 수량의 합이다.
        private int quantity;
        // 작업지시의 원래 시작·종료. 생산능력 감소는 이 종료일을 넘기지 않는다.
        private final LocalDate start;
        private final LocalDate end;
        // PREPARE, PRODUCE, SUSPEND. 생산은 다른 생산 라인으로 겹치지 않는다.
        private final String statusCode;
        private Line destination;
        // 다시 넣는 마지막 날. 점검·고장에서 원래 완료일 다음으로 밀리면 그 날까지다.
        private LocalDate packUntil;
        // 다시 넣을 날짜와 수량. 첫날이 새 계획이 시작되는 날이다.
        private Map<LocalDate, Integer> packed = Map.of();

        private Job(WorkOrder workOrder) {
            this.workOrder = workOrder;
            this.start = workOrder.getPlannedStartDate();
            this.end = workOrder.getPlannedEndDate();
            this.statusCode = workOrder.getCommonCode().getCode();
            this.packUntil = end;
        }
    }
}
