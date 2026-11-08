package net.likelion.bebc25.prodflow.work_order.dto;

import net.likelion.bebc25.prodflow.work_order.entity.ProductionPlan;

import java.time.LocalDate;

public class ProductionPlanDto {

    // 작업지시 등록이 저장한 하루 계획. 날짜별 목록은 요청으로 받지 않는다.
    public record Response(
            Long id,
            Long workOrderId, // 작업지시서 고유 번호
            LocalDate planDate, // 그날의 생산 예정일
            int quantity // 그 날짜의 계획 수량
    ) {
        public static Response from(ProductionPlan plan) {
            return new Response(
                    plan.getId(),
                    plan.getWorkOrder().getId(),
                    plan.getPlanDate(),
                    plan.getQuantity()
            );
        }
    }
}
