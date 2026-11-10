package net.likelion.bebc25.prodflow.product.repository;

import net.likelion.bebc25.prodflow.product.entity.ProductDrawing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductDrawingRepository extends JpaRepository<ProductDrawing, Long> {

    // 현재 유효한 파일들 (replacedAt == null)
    List<ProductDrawing> findByProductIdAndReplacedAtIsNull(Long productId);

    // 해당 품목 파일 전체 (최신순, 이력 포함)
    List<ProductDrawing> findByProductIdOrderByCreatedAtDesc(Long productId);

    // 삭제 대상. 이미 삭제된 파일은 찾지 않는다.
    Optional<ProductDrawing> findByIdAndProduct_IdAndReplacedAtIsNull(Long id, Long productId);
}
