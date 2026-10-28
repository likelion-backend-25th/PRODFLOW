package net.likelion.bebc25.prodflow.quotation.repository;

import net.likelion.bebc25.prodflow.quotation.entity.QuotationProductionPlan;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationProductionPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotationProductionPlanRepository extends JpaRepository<QuotationProductionPlan, Long> {

    // 견적 작품의 현재 유효한 임시 계획만 조회
    List<QuotationProductionPlan> findByQuotationItem_IdAndStatus(Long quotationItemId, QuotationProductionPlanStatus status);
}
