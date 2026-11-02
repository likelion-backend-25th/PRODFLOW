package net.likelion.bebc25.prodflow.quotation.repository;

import net.likelion.bebc25.prodflow.quotation.entity.QuotationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotationItemRepository extends JpaRepository<QuotationItem, Long> {
    // ID에 해당하는 견적서에 속한 품목들을 ID 오름차순으로 조회
    List<QuotationItem> findByQuotationIdOrderByIdAsc(Long quotationId);

}
