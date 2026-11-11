package net.likelion.bebc25.prodflow.product.repository;

import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    // 중복 확인메서드.
    boolean existsByModelCode(String modelCode);

    @EntityGraph(attributePaths = "device")
    Page<Product> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "device")
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "device")
    @Query(
            value = """
                    SELECT p FROM Product p
                    JOIN p.device d
                    WHERE (:status IS NULL OR p.status = :status)
                      AND (
                            :keyword IS NULL
                         OR p.modelCode LIKE CONCAT('%', :keyword, '%')
                         OR p.productName LIKE CONCAT('%', :keyword, '%')
                         OR d.name LIKE CONCAT('%', :keyword, '%')
                         OR p.productDescription LIKE CONCAT('%', :keyword, '%')
                      )
                    """,
            countQuery = """
                    SELECT count(p) FROM Product p
                    JOIN p.device d
                    WHERE (:status IS NULL OR p.status = :status)
                      AND (
                            :keyword IS NULL
                         OR p.modelCode LIKE CONCAT('%', :keyword, '%')
                         OR p.productName LIKE CONCAT('%', :keyword, '%')
                         OR d.name LIKE CONCAT('%', :keyword, '%')
                         OR p.productDescription LIKE CONCAT('%', :keyword, '%')
                      )
                    """
    )
    Page<Product> search(
            @Param("keyword") String keyword,
            @Param("status") ProductStatus status,
            Pageable pageable
    );

}

