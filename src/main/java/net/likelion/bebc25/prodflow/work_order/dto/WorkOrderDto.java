package net.likelion.bebc25.prodflow.work_order.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;

import java.time.LocalDate;
import java.util.List;

public class WorkOrderDto {

    public record CreateRequest(
            @NotNull(message = "수주 품목을 입력해주세요.")
            @Positive(message = "수주 품목을 입력해주세요.")
            Long orderProductId, // 생산 품목
            @NotNull(message = "라인을 입력해주세요.")
            @Positive(message = "라인을 입력해주세요.")
            Long lineId, // 작업 라인
            @NotNull(message = "담당자를 입력해주세요.")
            @Positive(message = "담당자를 입력해주세요.")
            Long assignedId, // 담당자
            @Positive(message = "작업지시 수량은 1 이상이어야 합니다.")
            int quantity, // 이 라인에 배정하는 수량
            @NotNull(message = "생산 시작 예정일을 입력해주세요.")
            LocalDate plannedStartDate,
            @NotNull(message = "생산 완료 예정일을 입력해주세요.")
            LocalDate plannedEndDate
    ) {
        // 날짜가 비어 있으면 @NotNull이 알린다. 둘 다 있을 때만 순서를 본다.
        @AssertTrue(message = "생산 예정 기간이 올바르지 않습니다.")
        public boolean isPlannedPeriodValid() {
            if (plannedStartDate == null || plannedEndDate == null) {
                return true;
            }
            return !plannedStartDate.isAfter(plannedEndDate);
        }
    }

    // 작업 상태 변경. status는 WORK_ORDER_STATUS의 code다. note는 생산 중지 사유다.
    public record UpdateStatusRequest(
            @NotBlank(message = "작업 상태를 입력해주세요.")
            String status,
            String note
    ) {
    }

    // 담당 라인, 작업자 변경 요청 dto. 라인과 담당자는 하나만 바꿔도 된다.
    public record UpdatedRequest(
            @Positive(message = "라인을 입력해주세요.")
            Long lineId,
            @Positive(message = "담당자를 입력해주세요.")
            Long assignedId
    ) {
        @AssertTrue(message = "변경된 라인 또는 담당자가 없습니다.")
        public boolean hasLineOrAssignee() {
            return lineId != null || assignedId != null;
        }
    }

    // 등록 후 응답
    public record Response(
            Long id, // 저장된 작업지시서 고유 번호
            String workNo, // 작업 지시 번호
            String note,
            List<ProductionPlanDto.Response> plans // 이 장에 저장된 하루 계획
    ) {
    }

    // 목록 응답
    public record PageResponse(
            Long id,
            String workNo, // 작업 지시 번호
            String orderNo, // 수주 번호
            String clientName, // 거래처명
            String productName, // 품목명
            int quantity,
            String lineName,
            String assignedName,
            LocalDate workOrderDate,
            LocalDate plannedEndDate,
            String status,
            Long orderProductId, // 수주 품목 ID
            LocalDate plannedStartDate,
            LocalDate completedAt
    ) {
        public static PageResponse from(WorkOrder workOrder) {
            return new PageResponse(
                    workOrder.getId(),
                    workOrder.getWorkNo(),
                    workOrder.getOrderProduct().getCustomerOrder().getOrderNo(),
                    workOrder.getOrderProduct().getCustomerOrder().getClient().getCompany(),
                    workOrder.getOrderProduct().getProductName(),
                    workOrder.getQuantity(),
                    workOrder.getLine().getLineName(),
                    workOrder.getUser() == null ? null : workOrder.getUser().getName(),
                    workOrder.getWorkOrderDate(),
                    workOrder.getPlannedEndDate(),
                    workOrder.getCommonCode().getCode(),
                    workOrder.getOrderProduct().getId(),
                    workOrder.getPlannedStartDate(),
                    workOrder.getCompletedAt()
            );
        }
    }

    // 상세 응답
    public record DetailResponse(
            Long id,
            String workNo, // 작업 지시 번호
            String orderNo, // 수주 번호
            String clientName, // 거래처명
            String productName, // 품목명
            int quantity,
            Long lineId,
            String lineName,
            Long assignedId,
            String assignedName,
            LocalDate workOrderDate, // 지시일
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate completedAt,
            String status,
            String note,
            LocalDate estimatedDueDate, // 수주 납기
            List<ProductionPlanDto.Response> plans // 날짜별 계획 수량
    ) {
        public static DetailResponse from(WorkOrder workOrder, List<ProductionPlanDto.Response> plans) {
            return new DetailResponse(
                    workOrder.getId(),
                    workOrder.getWorkNo(),
                    workOrder.getOrderProduct().getCustomerOrder().getOrderNo(),
                    workOrder.getOrderProduct().getCustomerOrder().getClient().getCompany(),
                    workOrder.getOrderProduct().getProductName(),
                    workOrder.getQuantity(),
                    workOrder.getLine().getId(),
                    workOrder.getLine().getLineName(),
                    workOrder.getUser() == null ? null : workOrder.getUser().getId(),
                    workOrder.getUser() == null ? null : workOrder.getUser().getName(),
                    workOrder.getWorkOrderDate(),
                    workOrder.getPlannedStartDate(),
                    workOrder.getPlannedEndDate(),
                    workOrder.getCompletedAt(),
                    workOrder.getCommonCode().getCode(),
                    workOrder.getNote(),
                    workOrder.getOrderProduct().getCustomerOrder().getEstimatedDueDate(),
                    plans
            );
        }
    }
}
