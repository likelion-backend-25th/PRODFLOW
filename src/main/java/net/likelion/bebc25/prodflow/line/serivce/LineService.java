package net.likelion.bebc25.prodflow.line.serivce;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.common.repository.StatusHistoryRepository;
import net.likelion.bebc25.prodflow.line.dto.LineDto;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.line.entity.LineCapacityLog;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import net.likelion.bebc25.prodflow.line.repsitory.LineCapacityLogRepository;
import net.likelion.bebc25.prodflow.line.repsitory.LineRepository;
import net.likelion.bebc25.prodflow.line_notification.service.LineNotificationService;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import net.likelion.bebc25.prodflow.work_order.serivce.WorkOrderService;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.repsitory.WorkOrderRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LineService {

    private final LineRepository lineRepository;
    private final WorkOrderRepository workOrderRepository;
    private final UserRepository userRepository;
    private final LineCapacityLogRepository lineCapacityLogRepository; // 라인 변경시 이력을 저장
    private final StatusHistoryRepository statusHistoryRepository; // 상태 변경시 이력을 저장
    private final WorkOrderService workOrderService;

    // 생산라인 알림 발송
    private final LineNotificationService lineNotificationService;

    // 생산라인 알림 발송
    private final LineNotificationService lineNotificationService;

    // 생산라인 목록. 카드 한 장과 상단 집계를 한 응답으로 만든다.
    public LineDto.ListResponse findAll() {
        // 쿼리는 라인 기준 아우터 조인이고, 라인 id 다음 작업 id 순이다.
        // 생산 중 작업이 없으면 작업 번호, 품목, 수주, 담당자는 null이다.
        // 같은 라인이 바로 다시 나오면 두 번째 생산 작업이라 건너뛴다. 먼저 나온 작업만 남는다.
        List<LineDto.LineList> lines = new ArrayList<>();
        // 가장 먼저 배정된 라인 정보
        Long previousLineId = null;
        for (LineDto.LineList row : lineRepository.findLineList()) {
            // 이미 해당 라인에 정보가 있는경우 건너띔
            if (row.lineId().equals(previousLineId)) {
                continue;
            }
            lines.add(row);
            previousLineId = row.lineId();
        }
        return new LineDto.ListResponse(
                lines.size(), // 전체 라인 수. 작업이 없는 라인도 포함한다.
                count(lines, LineStatus.OPERATING), // 가동 중
                count(lines, LineStatus.AVAILABLE), // 가동 가능
                count(lines, LineStatus.INSPECTION) + count(lines, LineStatus.BROKEN), // 점검과 고장
                lines
        );
    }

    // 상단 집계는 작업 수가 아니라 라인 상태만 센다.
    private int count(List<LineDto.LineList> lines, LineStatus status) {
        return (int) lines.stream().filter(line -> line.status() == status).count();
    }

    // 생산라인 상세. 현재 작업은 이 라인의 PRODUCE 중 먼저 등록된 한 건이다.
    public LineDto.DetailResponse findByLineId(Long lineId) {
        Line line = lineRepository.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("라인을 찾을 수 없습니다."));
        // 이 라인 작업 중에서 현재·예정만 남긴다. 포장, 포장완료, 생산중지는 제외한다.
        List<WorkOrder> workOrders = new ArrayList<>();
        for (WorkOrder workOrder : workOrderRepository.search(
                lineId, null, null, null, null, Pageable.unpaged()).getContent()) {
            String status = workOrder.getCommonCode().getCode();
            if ("PRODUCE".equals(status) || "PREPARE".equals(status) || "SUSPEND".equals(status)) {
                workOrders.add(workOrder);
            }
        }
        WorkOrder current = currentWork(workOrders);
        // 생산 중인 작업이 없으면 이 여섯 값은 비운다.
        String workNo = null;
        String productName = null;
        String orderNo = null;
        LocalDate plannedStartDate = null;
        LocalDate plannedEndDate = null;
        String managerName = null;
        if (current != null) {
            workNo = current.getWorkNo();
            productName = current.getOrderProduct().getProductName();
            orderNo = current.getOrderProduct().getCustomerOrder().getOrderNo();
            plannedStartDate = current.getPlannedStartDate();
            plannedEndDate = current.getPlannedEndDate();
            managerName = current.getUser().getName();
        }
        List<LineDto.CapacityLog> capacityLogs = lineCapacityLogRepository
                .findByLine_IdOrderByChangedAtDesc(lineId).stream()
                .map(this::toCapacityLog)
                .toList();
        return new LineDto.DetailResponse(
                line.getId(),
                line.getLineName(),
                line.getStatus(),
                line.getProductionCapacity(),
                workOrders.size(), // 현재·예정 작업 수
                countWork(workOrders, "PREPARE"), // 라인에 배정된 준비중인 작업 수
                workNo,
                productName,
                orderNo,
                plannedStartDate,
                plannedEndDate,
                managerName,
                workOrders.stream().map(this::toDetailWorkOrder).toList(),
                capacityLogs
        );
    }

    // 생산 중 작업이 여러 건이면 id가 작은 작업을 현재 작업으로 둔다.
    private WorkOrder currentWork(List<WorkOrder> workOrders) {
        WorkOrder current = null;
        for (WorkOrder workOrder : workOrders) {
            if (!"PRODUCE".equals(workOrder.getCommonCode().getCode())) {
                continue;
            }
            if (current == null || workOrder.getId() < current.getId()) {
                current = workOrder;
            }
        }
        return current;
    }

    private int countWork(List<WorkOrder> workOrders, String status) {
        return (int) workOrders.stream()
                .filter(workOrder -> status.equals(workOrder.getCommonCode().getCode()))
                .count();
    }

    private LineDto.DetailWorkOrder toDetailWorkOrder(WorkOrder workOrder) {
        return new LineDto.DetailWorkOrder(
                workOrder.getWorkNo(),
                workOrder.getOrderProduct().getCustomerOrder().getOrderNo(),
                workOrder.getOrderProduct().getProductName(),
                workOrder.getQuantity(),
                workOrder.getPlannedStartDate(),
                workOrder.getPlannedEndDate(),
                workOrder.getCommonCode().getCode()
        );
    }

    private LineDto.CapacityLog toCapacityLog(LineCapacityLog log) {
        return new LineDto.CapacityLog(
                log.getChangedAt(),
                log.getBeforeCapacity(),
                log.getAfterCapacity(),
                log.getReason(),
                log.getChangedBy().getName()
        );
    }

    /**
     * 라인 등록 (권한 : 최고 관리자, 수주 관리자)
     *
     * @param request (라인 이름, 생산량, 도입일)
     * @param userId  액세스 토큰 subject의 직원 id. 이름은 토큰에 없다.
     * @return save가 INSERT하고 Id를 채운다.
     * updated_at 처음 생성시 null 허용
     */
    @Transactional
    public LineDto.Response create(LineDto.CreateRequest request, Long userId) {

        String lineName = request.lineName() == null ? "" : request.lineName().trim();
        if (lineName.isBlank()) {
            throw new IllegalArgumentException("라인 이름은 공백 허용 안함");
        }
        // 등록 화면 규칙: 하루 생산량은 1 이상. 0과 음수는 저장하지 않음
        if (request.productionCapacity() < 1) {
            throw new IllegalArgumentException("하루 생산량은 1 이상");
        }
        if (lineRepository.existsByLineName(lineName)) {
            throw new IllegalArgumentException("이미 존재하는 이름");
        }

        // 토큰의 id로 로그인 직원을 찾는다. 없으면 라인을 만들지 않는다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("등록한 직원을 찾을 수 없습니다."));


        Line line = new Line(lineName, request.productionCapacity());
        // NOT NULL인 createdBy에 등록한 직원 이름을 넣는다.
        line.setCreatedBy(user.getName());
        // NOT NULL인 updatedBy도 등록 시점에는 같은 이름으로 채운다.
        line.setUpdatedBy(user.getName());
        return LineDto.Response.from(lineRepository.save(line));
    }

    @Transactional
    public LineDto.Response update(Long lineId, LineDto.UpdateRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("수정한 직원을 찾을 수 없습니다."));
        Line line = lineRepository.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("라인을 찾을 수 없습니다."));

        String lineName = request.lineName() == null ? "" : request.lineName().trim();
        // 라인 이름을 변경했는지 여부
        boolean isNameChanged = !line.getLineName().equals(lineName);
        // 생산량을 수정했는지 여부
        boolean isCapacityChanged = line.getProductionCapacity() != request.productionCapacity();
        // 하루 생산량은 수주 관리자도 바꾸지만, 라인 이름은 최고 관리자만 바꾼다.
        if (isNameChanged && user.getRole() != UserRole.SUPER_ADMIN) {
            throw new AccessDeniedException("라인 이름은 최고 관리자만 수정할 수 있습니다.");
        }
        // 줄어든 생산능력이 기존 계획을 담지 못하면 라인과 이력을 바꾸지 않는다.
        if (isCapacityChanged && request.productionCapacity() < line.getProductionCapacity()) {
            workOrderService.assertPlansWithinCapacity(line.getId(), request.productionCapacity());
        }

        if (isNameChanged && isCapacityChanged) {
            line.changeName(lineName);
            // 변경 전은 현재 라인 수량, 변경 후는 request.productionCapacity()로 이력에 저장된다.
            lineCapacityLogRepository.save(new LineCapacityLog(
                    line,
                    request.productionCapacity(),
                    request.reason(),
                    user));
            // 이력에 변경 후 수량이 들어간 뒤에 라인 생산량을 바꾼다.
            line.editProduction(request.productionCapacity());

            line.setUpdatedBy(user.getName());
        } else if (isNameChanged) {
            line.changeName(lineName);
        } else if (isCapacityChanged) {
            // 변경 후 수량은 라인 수정 전에 요청값으로 이력에 저장된다.
            lineCapacityLogRepository.save(new LineCapacityLog(
                    line,
                    request.productionCapacity(),
                    request.reason(),
                    user));
            line.editProduction(request.productionCapacity());
        }
        return LineDto.Response.from(line);
    }

    @Transactional
    public LineDto.Response changeStatus(Long lineId, LineDto.UpdateStatusRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("수정한 직원을 찾을 수 없습니다."));
        Line line = lineRepository.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("라인을 찾을 수 없습니다."));

        // 현재 상태와 변경 요청 상태가 동일한경우
        LineStatus beforeStatus = line.getStatus();
        if (beforeStatus == request.status()) {
            throw new IllegalArgumentException("이미 같은 상태입니다.");
        }
        line.changeStatus(request.status()); // 요청에 맞도록 상태 변형
        line.setUpdatedBy(user.getName()); // 상태변경한 직원 이름
        statusHistoryRepository.save(new StatusHistory(
                HistoryEntityType.LINE, // 라인 타입으로 등록
                line.getId(),       // 라인 번호
                beforeStatus.name(), // 변경전 상태
                request.status().name(), // 변경후 상태
                user)); // 변경한 직원

        // 생산라인 상태 변경 알림 (생산라인 알림 도메인)
        lineNotificationService.notifyLineStatusChanged(
                line.getId(),
                request.status()
        );
        return LineDto.Response.from(line);
    }

}
