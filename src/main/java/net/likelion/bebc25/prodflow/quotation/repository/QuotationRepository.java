package net.likelion.bebc25.prodflow.quotation.repository;

import jakarta.persistence.LockModeType;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    // 인터페이스 선언만으로 기본 CRUD(save, findById, findAll, deleteById)가 자동 제공됨

    // 동일 초안의 수정 트랜잭션을 들어온 순서대로 처리
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quotation q where q.id = :id")
    Optional<Quotation> findByIdForUpdate(@Param("id") Long id);

    // 거래처·작성자 포함 견적 페이징 조회, 정렬은 Pageable로 지정
    @Override
    @EntityGraph(attributePaths = {"client", "user"})
    Page<Quotation> findAll(Pageable pageable);

    // 검색어·상태 선택 조건, 둘 다 지정하면 AND 적용
    // 페이지 정렬은 Pageable 적용, 전체 건수는 동일 조건으로 계산
    @EntityGraph(attributePaths = {"client", "user"})
    @Query(value = """
            select q from Quotation q
            where (:status is null or q.status = :status)
              and (:keyword is null or lower(q.quotationNo) like lower(concat('%', :keyword, '%'))
               or lower(q.client.company) like lower(concat('%', :keyword, '%'))
               or lower(q.user.name) like lower(concat('%', :keyword, '%')))
            """, countQuery = """
            select count(q) from Quotation q
            where (:status is null or q.status = :status)
              and (:keyword is null or lower(q.quotationNo) like lower(concat('%', :keyword, '%'))
               or lower(q.client.company) like lower(concat('%', :keyword, '%'))
               or lower(q.user.name) like lower(concat('%', :keyword, '%')))
            """)
    Page<Quotation> search(@Param("keyword") String keyword, @Param("status") QuotationStatus status, Pageable pageable);

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
