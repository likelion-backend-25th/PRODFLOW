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
import net.likelion.bebc25.prodflow.code.entity.Code;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String modelCode;

    @Column(nullable = false)
    private String productName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_code", nullable = false)
    private Code device;

    @Column
    private String productDescription;

    @Column(nullable = false)
    private Long currentPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.AVAILABLE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;


    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
