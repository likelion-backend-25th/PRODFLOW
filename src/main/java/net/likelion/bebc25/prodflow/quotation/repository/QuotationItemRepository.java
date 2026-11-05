package net.likelion.bebc25.prodflow.quotation.repository;

import net.likelion.bebc25.prodflow.quotation.entity.QuotationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotationItemRepository extends JpaRepository<QuotationItem, Long> {
    // 견적 품목 등록순 조회
    List<QuotationItem> findByQuotationIdOrderByIdAsc(Long quotationId);

}
