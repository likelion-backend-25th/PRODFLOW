package net.likelion.bebc25.prodflow.quotation.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    // 인터페이스 선언만으로 기본 CRUD(save, findById, findAll, deleteById)가 자동 제공됨

    // 견적 조회 및 쓰기 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quotation q where q.id = :id")
    Optional<Quotation> findByIdForUpdate(@Param("id") Long id);

    // 유효기간이 지난 발송 견적 조회 및 쓰기 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Quotation> findByStatusAndValidUntilBeforeOrderByIdAsc(QuotationStatus status, LocalDate today);

    // 거래처·작성자 포함 견적 목록 조회
    @Override
    @EntityGraph(attributePaths = {"client", "user"})
    Page<Quotation> findAll(Pageable pageable);

    // 검색·상태 조건별 견적 조회
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
    Page<Quotation> search(
            @Param("keyword") String keyword, @Param("status") QuotationStatus status, Pageable pageable);

    // 거래처·작성자 포함 견적 상세 조회
    @Query("""
            select q from Quotation q
            join fetch q.client
            join fetch q.user
            where q.id = :id
            """)
    Optional<Quotation> findDetailById(@Param("id") Long id);

}
