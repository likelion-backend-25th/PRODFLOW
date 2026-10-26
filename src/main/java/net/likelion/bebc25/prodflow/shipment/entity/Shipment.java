package net.likelion.bebc25.prodflow.shipment.entity;

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

import java.time.LocalDateTime;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
}
