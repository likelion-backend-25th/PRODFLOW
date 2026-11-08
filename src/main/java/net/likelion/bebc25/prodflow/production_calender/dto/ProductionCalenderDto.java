package net.likelion.bebc25.prodflow.production_calender.dto;

import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;

import java.time.LocalDate;

public class ProductionCalenderDto {

    public record Response(
            Long lineId,
            String lineName,
            Long workOrderId,
            String workNo,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            String status
    ) {
        public static Response from(WorkOrder workOrder) {
            return new Response(
                    workOrder.getLine().getId(),
                    workOrder.getLine().getLineName(),
                    workOrder.getId(),
                    workOrder.getWorkNo(),
                    workOrder.getPlannedStartDate(),
                    workOrder.getPlannedEndDate(),
                    workOrder.getCommonCode().getCode()
            );
        }
    }

}
