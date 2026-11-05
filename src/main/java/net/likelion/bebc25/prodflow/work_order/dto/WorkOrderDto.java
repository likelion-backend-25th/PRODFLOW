package net.likelion.bebc25.prodflow.work_order.dto;

import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;

import java.time.LocalDate;
import java.util.List;

public class WorkOrderDto {

    public record CreateRequest(
            Long orderProductId, // 생산 품목
            Long lineId, // 작업 라인
            Long assignedId, // 담당자
            int quantity, // 이 라인에 배정하는 수량
            LocalDate plannedStartDate,
            LocalDate plannedEndDate
    ) {
    }

    // 작업 상태 변경. status는 WORK_ORDER_STATUS의 code다. note는 생산 중지 사유다.
    public record UpdateStatusRequest(
            String status,
            String note
    ) {
    }

    // 담당 라인, 작업자 변경 요청 dto
    public record UpdatedRequest(
            Long lineId,
            Long assignedId
    ) {
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
            String status
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
                    workOrder.getUser().getName(),
                    workOrder.getWorkOrderDate(),
                    workOrder.getPlannedEndDate(),
                    workOrder.getCommonCode().getCode()
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
                    workOrder.getUser().getId(),
                    workOrder.getUser().getName(),
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
