package net.likelion.bebc25.prodflow.product.repository;

import net.likelion.bebc25.prodflow.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    // 중복 확인메서드.
    boolean existsByModelCode(String modelCode);

    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.device d
            WHERE :keyword IS NULL
               OR p.modelCode LIKE CONCAT('%', :keyword, '%')
               OR p.productName LIKE CONCAT('%', :keyword, '%')
               OR d.name LIKE CONCAT('%', :keyword, '%')
               OR p.productDescription LIKE CONCAT('%', :keyword, '%')
            """)
    List<Product> search(@Param("keyword") String keyword);
}

