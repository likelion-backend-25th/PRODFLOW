package net.likelion.bebc25.prodflow.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.likelion.bebc25.prodflow.common.entity.BaseTimeEntity;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductDrawing extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String objectKey;

    @Column(nullable = false)
    private String fileName;

    // null이면 현재 도면
    @Column
    private LocalDateTime replacedAt;


    public static ProductDrawing create(Product product, String objectKey, String fileName) {
        ProductDrawing drawing = new ProductDrawing();
        drawing.product = product;
        drawing.objectKey = objectKey;
        drawing.fileName = fileName;
        drawing.replacedAt = null; // 현재 유효한 파일
        return drawing;
    }

    // 현재 목록에서 빼고 이력만 남긴다.
    public void markReplaced(LocalDateTime replacedAt) {
        if (replacedAt == null) {
            throw new IllegalArgumentException("삭제 시각이 필요합니다.");
        }
        this.replacedAt = replacedAt;
    }
}
