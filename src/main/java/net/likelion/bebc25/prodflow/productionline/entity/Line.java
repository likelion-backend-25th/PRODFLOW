package net.likelion.bebc25.prodflow.productionline.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.likelion.bebc25.prodflow.common.entity.BaseTimeEntity;
import net.likelion.bebc25.prodflow.productionline.enums.LineStatus;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Line extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto_increment
    private Long id;

    @Column(unique = true, nullable = false)
    private String lineName;

    @Column(nullable = false) // check 사용
    private int productionCapacity;

    // 라인 초기 상태는 가동 가능이다.
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LineStatus status = LineStatus.AVAILABLE;


    // 처음 라인 생성시
    public Line(String lineName, int productionCapacity) {
        this.lineName = lineName;
        this.productionCapacity = productionCapacity;
    }

    // 라인 생산량 수정
    public void editProduction(int productionCapacity) {
        this.productionCapacity = productionCapacity;
    }

    // 라인 상태 변경
    public void changeStatus(LineStatus status) {
        this.status = status;
    }
}
