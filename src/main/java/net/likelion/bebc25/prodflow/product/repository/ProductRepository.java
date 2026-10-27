package net.likelion.bebc25.prodflow.product.repository;

import net.likelion.bebc25.prodflow.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    // 중복 확인메서드.
    boolean existsByModelCode(String modelCode);
}
