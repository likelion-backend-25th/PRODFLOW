package net.likelion.bebc25.prodflow.quotation.repository;

import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    // 인터페이스 선언만으로 기본 CRUD(save, findById, findAll, deleteById)가 자동 제공됨

    // 견적서 등록 시 견적번호 중복 확인
    boolean existsByQuotationNo(String quotationNo);

    // 견적 ID로 견적, 거래처, 작성자를 함께 조회
    // join fetch 연관 데이터를 함께 조회
    @Query("""
            select q from Quotation q
            join fetch q.client
            join fetch q.user
            where q.id = :id
            """)
    Optional<Quotation> findDetailById(@Param("id") Long id);

}
