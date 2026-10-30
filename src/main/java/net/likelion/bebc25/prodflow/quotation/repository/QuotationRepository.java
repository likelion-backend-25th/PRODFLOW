package net.likelion.bebc25.prodflow.quotation.repository;

import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    // 인터페이스 선언만으로 기본 CRUD(save, findById, findAll, deleteById)가 자동 제공됨

    // 견적서 등록 시 견적번호 중복 확인
    boolean existsByQuotationNo(String quotationNo);

}
