package net.likelion.bebc25.prodflow.shipment.entity;

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

import java.time.LocalDateTime;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "shipment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_shipment_carrier_tracking",
                columnNames = {"carrier_code", "tracking_number"}
        )
)
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrier_code", nullable = false)
    private Carrier carrier;

    @Column(nullable = false)
    private String trackingNumber;


    @Column
    private Integer level;

    // 조회결과.
    @Column(length = 1)
    private String result;


    @Column
    private Boolean complete;

    // 배송예쩡 시간
    @Column
    private String estimate;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime deliveredAt;

    public static Shipment start(Long orderId, Carrier carrier, String trackingNumber, LocalDateTime startedAt) {
        Shipment shipment = new Shipment();
        shipment.orderId = orderId;
        shipment.carrier = carrier;
        shipment.trackingNumber = trackingNumber;
        shipment.startedAt = startedAt;
        shipment.complete = false;
        return shipment;
    }

    public void applyTracking(
            Integer level,
            String result,
            Boolean complete,
            String estimate
    ) {
        this.level = level;
        this.result = result;
        this.complete = complete;
        this.estimate = estimate;

        // 배송 완료인데 완료 시각이 없으면 지금 시각으로 기록
        if (Boolean.TRUE.equals(complete) && this.deliveredAt == null) {
            this.deliveredAt = LocalDateTime.now();
        }
    }

    // 화면에서 수동 배송 완료할 때 사용
    public void markDelivered() {
        this.complete = true;
        if (this.level == null || this.level < 6) {
            this.level = 6;
        }
        if (this.deliveredAt == null) {
            this.deliveredAt = LocalDateTime.now();
        }
    }

}
