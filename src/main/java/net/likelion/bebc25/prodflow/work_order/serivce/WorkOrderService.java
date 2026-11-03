package net.likelion.bebc25.prodflow.work_order.serivce;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.repository.OrderProductRepository;
import net.likelion.bebc25.prodflow.holiday.entity.Holiday;
import net.likelion.bebc25.prodflow.holiday.repository.HolidayRepository;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.line.repsitory.LineRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto;
import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.repsitory.ProductionPlanRepository;
import net.likelion.bebc25.prodflow.work_order.repsitory.WorkOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final OrderProductRepository orderProductRepository;
    private final LineRepository lineRepository;
    private final UserRepository userRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final HolidayRepository holidayRepository;
    private final ProductionPlanRepository productionPlanRepository;

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
        // 화면에서 고른 첫 배정 라인이다.
        Line line = lineRepository.findById(request.lineId())
                .orElseThrow(() -> new IllegalArgumentException("라인을 찾을 수 없습니다."));

        // 첫 라인이 점검·고장이면 배정을 시작하지 않는다.
        if (line.getStatus() != LineStatus.AVAILABLE && line.getStatus() != LineStatus.OPERATING) {
            throw new IllegalArgumentException("점검·고장 라인에는 배정할 수 없습니다.");
        }

        // ORDER_STATUS 그룹의 사용 중인 코드를 가져온다.
        CommonCode prepare = commonCodeRepository
                .findByCodeGroup_GroupCodeAndStatus("ORDER_STATUS", CodeStatus.ACTIVE)
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

        // 이번 등록의 첫 지시번호다. 장을 저장할 때마다 1 올린다.
        long nextNo = workOrderRepository.count() + 1;
        // 아직 라인에 넣지 못한 수량이다.
        int remaining = request.quantity();
        // 저장된 작업지시서 응답이다. 장이 여러 개일 수 있다.
        List<WorkOrderDto.Response> responses = new ArrayList<>();

        // 요청한 라인을 먼저 채운다.
        List<Line> lines = new ArrayList<>();
        lines.add(line);
        for (Line candidate : lineRepository.findAll()) {
            // 이미 넣은 요청 라인은 다시 넣지 않는다.
            if (candidate.getId().equals(line.getId())) {
                continue;
            }
            // 남은 수량을 넘길 라인은 가동 가능·가동 중만 둔다.
            if (candidate.getStatus() == LineStatus.AVAILABLE || candidate.getStatus() == LineStatus.OPERATING) {
                lines.add(candidate);
            }
        }

        for (Line target : lines) {
            // 수량이 다 들어갔으면 다음 라인을 보지 않는다.
            if (remaining == 0) {
                break;
            }
            // 이 라인 한 장의 날짜 목록이다. 라인이 바뀌면 비운 뒤 다시 쌓는다.
            List<LocalDate> planDates = new ArrayList<>();
            // planDates와 같은 위치의 그날 수량이다.
            List<Integer> planQuantities = new ArrayList<>();
            // 이 라인에 넣어 볼 남은 수량이다.
            int remainingToAssign = remaining;
            // 이 라인의 첫 검사일이다.
            LocalDate date = request.plannedStartDate();

            // 종료일까지, 이 라인에 더 넣을 수량이 있는 동안 하루씩 본다.
            while (!date.isAfter(request.plannedEndDate()) && remainingToAssign > 0) {
                // 주말인지 확인
                boolean isWeekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
                // 주말이거나 공휴일이면 다음 날로 넘긴다.
                if (isWeekend || holidays.contains(date)) {
                    date = date.plusDays(1);
                    continue;
                }
                // 이 라인, 이 날짜에 이미 저장된 계획 수량을 합한다.
                long alreadyPlanned = productionPlanRepository.sumQuantityByLineAndDate(target.getId(), date);
                // 하루 생산량에서 이미 잡힌 수량을 빼 남은 자리를 구한다.
                int room = target.getProductionCapacity() - (int) alreadyPlanned;
                // 남은 자리가 있을 때만 수량을 넣는다.
                if (room > 0) {
                    // 남은 자리와 아직 못 넣은 수량 중 작은 쪽을 그날 배정량으로 한다.
                    int assigned = Math.min(room, remainingToAssign);
                    // 배정한 날짜를 쌓는다.
                    planDates.add(date);
                    // 같은 위치에 그날 수량을 쌓는다.
                    planQuantities.add(assigned);
                    // 이 라인에 아직 못 넣은 수량에서 오늘 넣은 양을 뺀다.
                    remainingToAssign -= assigned;
                }
                // 생산 가능한 날도 다음 날로 넘어간다.
                date = date.plusDays(1);
            }

            // 이 라인에 실제로 들어간 수량 합이다.
            int assignedOnThisLine = 0;
            for (Integer quantity : planQuantities) {
                // 날짜별 수량을 더해 이 장에 들어갈 총수량을 만든다.
                assignedOnThisLine += quantity;
            }
            // 들어간 수량이 없으면 작업지시서를 만들지 않고 다음 라인으로 간다.
            if (assignedOnThisLine == 0) {
                continue;
            }

            // 이번 장의 지시번호다. count()를 다시 부르지 않는다.
            String workNo = "WI-" + Year.now().getValue() + "-" + String.format("%03d", nextNo);
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
            for (int i = 0; i < planDates.size(); i++) {
                // i번째 날짜와 i번째 수량이 하루 계획이다.
                productionPlanRepository.save(new ProductionPlan(saved, planDates.get(i), planQuantities.get(i)));
            }
            // 저장된 장 하나를 응답 목록에 넣는다.
            responses.add(new WorkOrderDto.Response(saved.getId(), saved.getWorkNo(), saved.getNote()));
            // 다음 장의 지시번호를 1 올린다.
            nextNo++;
            // 전체 남은 수량에서 이 장에 넣은 수량을 뺀다.
            remaining -= assignedOnThisLine;
        }

        // 모든 라인을 봤는데도 수량이 남으면 기간 안 생산 가능량으로는 부족하다.
        if (remaining > 0) {
            throw new IllegalArgumentException("선택한 기간의 생산 가능량을 초과했습니다.");
        }
        // 만들어진 작업지시서 목록이다. 한 장이면 원소가 하나다.
        return responses;
    }

    // 작업지시서 페이징 조회
    public Page<WorkOrderDto.PageResponse> findAll(Pageable pageable) {
        // 완료 예정일이 빠른 순이다. 같은 날이면 id가 큰 최근 등록이 앞이다.
        return workOrderRepository.findAllByOrderByPlannedEndDateAscIdDesc(pageable)
                // 작업지시 엔티티를 페이징 응답으로 바꾼다.
                .map(WorkOrderDto.PageResponse::from);
    }

    // 작업지시서 상세 조회
    public WorkOrderDto.DetailResponse getDetail(Long workOrderId) {
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new NoSuchElementException("해당 지시서를 찾을 수 없습니다."));
        return WorkOrderDto.DetailResponse.from(workOrder);
    }
}
