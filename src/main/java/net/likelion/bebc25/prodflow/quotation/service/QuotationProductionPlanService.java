package net.likelion.bebc25.prodflow.quotation.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.productionline.entity.Line;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationProductionPlanDto;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.entity.QuotationItem;
import net.likelion.bebc25.prodflow.quotation.entity.QuotationProductionPlan;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationProductionPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuotationProductionPlanService {

    private final QuotationProductionPlanRepository planRepository;
    private final EntityManager entityManager;

    @Transactional
    public List<QuotationProductionPlanDto.Response> create(
            Long quotationId,
            QuotationProductionPlanDto.CreateRequest request) {
        // 1. 라인 수량 합이 품목 수량과 같은지
        int sum = 0;
        for (QuotationProductionPlanDto.LinePlan line : request.lines()) {
            sum += line.plannedQuantity();
        }
        if (sum != request.itemQuantity()) {
            throw new IllegalArgumentException("계획 수량 합이 품목 수량과 다릅니다.");
        }

        // 2. Repository 없이 id만 연결
        Quotation quotation = entityManager.getReference(Quotation.class, quotationId);
        QuotationItem item = entityManager.getReference(QuotationItem.class, request.quotationItemId());

        // 3. 라인 한 줄당 ACTIVE 한 행
        List<QuotationProductionPlanDto.Response> responses = new ArrayList<>();
        for (QuotationProductionPlanDto.LinePlan linePlan : request.lines()) {
            Line line = entityManager.getReference(Line.class, linePlan.lineId());
            QuotationProductionPlan plan = new QuotationProductionPlan(
                    quotation,
                    item,
                    line,
                    linePlan.plannedQuantity(),
                    linePlan.plannedStartDate(),
                    linePlan.plannedEndDate()
            );
            responses.add(QuotationProductionPlanDto.Response.from(planRepository.save(plan)));
        }
        return responses;
    }
}
