package net.likelion.bebc25.prodflow.quotation.entity;

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
import net.likelion.bebc25.prodflow.product.entity.Product;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuotationItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "device_name", nullable = false)
    private String deviceName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "line_amount", nullable = false)
    private Long lineAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public QuotationItem(
            Quotation quotation,
            Product product,
            String productName,
            String deviceName,
            int quantity,
            Long unitPrice
    ) {
        this.quotation = quotation;
        this.product = product;
        this.productName = productName;
        this.deviceName = deviceName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineAmount = unitPrice * quantity;
        this.createdAt = LocalDateTime.now();
    }

    public void changePrice(int quantity, Long unitPrice) {
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineAmount = unitPrice * quantity;
    }
}
