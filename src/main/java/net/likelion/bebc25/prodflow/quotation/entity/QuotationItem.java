package net.likelion.bebc25.prodflow.quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.likelion.bebc25.prodflow.product.entity.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "quotation_item", uniqueConstraints = @UniqueConstraint(columnNames = {"quotation_id", "product_id"}))
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
        this.lineAmount = calculateAmount(quantity, unitPrice, quotation.isVatIncluded());
        this.createdAt = LocalDateTime.now();
    }

    public void changePrice(int quantity, Long unitPrice) {
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineAmount = calculateAmount(quantity, unitPrice, quotation.isVatIncluded());
    }

    // 견적 총액 사전 계산·품목 금액 저장 공통 규칙
    public static long calculateAmount(int quantity, Long unitPrice, boolean vatIncluded) {
        if (quantity < 100 || unitPrice == null || unitPrice < 0) {
            throw new IllegalArgumentException("견적 수량은 100개 이상, 단가는 0원 이상이어야 합니다.");
        }
        try {
            // 금액 계산에는 소수 오차가 없는 BigDecimal을 사용한다.
            BigDecimal price = BigDecimal.valueOf(unitPrice);
            BigDecimal count = BigDecimal.valueOf(quantity);
            BigDecimal amount = price.multiply(count);
            if (!vatIncluded) {
                amount = amount.multiply(new BigDecimal("1.1"));
            }
            // 원 단위로 반올림한다. long 범위를 넘으면 예외를 발생시킨다.
            return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("견적 품목 금액이 허용 범위를 초과했습니다.", ex);
        }
    }
}
