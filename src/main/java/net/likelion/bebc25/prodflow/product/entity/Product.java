package net.likelion.bebc25.prodflow.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String modelCode;

    @Column(nullable = false)
    private String productName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_code", nullable = false)
    private CommonCode device;

    @Column
    private String productDescription;

    @Column(nullable = false)
    private Long currentPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.AVAILABLE;

    public static Product create(
            String modelCode,
            String productName,
            CommonCode device,
            String productDescription,
            Long currentPrice
    ) {
        Product product = new Product();
        product.modelCode = modelCode;
        product.productName = productName;
        product.device = device;
        product.productDescription = productDescription;
        product.currentPrice = currentPrice;
        product.status = ProductStatus.AVAILABLE;
        return product;
    }

    public void changeStatus(ProductStatus status) {
        this.status = status;
    }

}
