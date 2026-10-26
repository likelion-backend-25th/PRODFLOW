package net.likelion.bebc25.prodflow.shipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Carrier {

    // 조회 api에 보내는 택배사 코드.
    @Id
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;


    @Column(length = 1)
    private String international;
}