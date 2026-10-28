package net.likelion.bebc25.prodflow.quotation.dto;

import net.likelion.bebc25.prodflow.quotation.entity.QuotationProductionPlan;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationProductionPlanStatus;

import java.time.LocalDate;
import java.util.List;

public class QuotationProductionPlanDto {

    public record CreateRequest(
            Long quotationId, // 견적서 Id
            Long quotationItemId, // 그 견적서 안의 어느 품목인지
            int itemQuantity, // 그 폼목에 적힌 수량 ex) 1000
            List<LinePlan> lines // 1000개를 라인별로 나눈 계획
    ) {
    }

    public record LinePlan(
            Long lineId, // 라인 Id
            int plannedQuantity, // 그 라인에 넣을 수량
            LocalDate plannedStartDate, // 작업 시작일
            LocalDate plannedEndDate // 작업 종료일
    ) {
    }

    public record Response(
            Long id,
            Long quotationId,
            Long quotationItemId,
            Long lineId,
            int plannedQuantity,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            QuotationProductionPlanStatus status   // 생성 직후는 ACTIVE
    ) {
        public static Response from(QuotationProductionPlan plan) {
            return new Response(
                    plan.getId(), plan.getQuotation().getId(), plan.getQuotationItem().getId(),
                    plan.getLine().getId(), plan.getPlannedQuantity(),
                    plan.getPlannedStartDate(), plan.getPlannedEndDate(), plan.getStatus()
            );
        }
    }
}
