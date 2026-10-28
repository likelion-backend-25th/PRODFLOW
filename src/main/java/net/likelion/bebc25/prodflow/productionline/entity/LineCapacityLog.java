package net.likelion.bebc25.prodflow.productionline.entity;

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
import net.likelion.bebc25.prodflow.user.entity.User;
import org.hibernate.annotations.CreationTimestamp;


import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineCapacityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto_increment
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id", nullable = false)
    private Line line;

    // 변경전 생산 능력
    @Column(nullable = false)
    private int beforeCapacity;

    // 변경 후 생산 능력
    @Column(nullable = false)
    private int afterCapacity;

    // 변경 사유
    @Column(nullable = false)
    private String reason;

    // 담당 직원
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User changedBy;

    // 변경 일시, 수정하는것이 아닌 이력을 생성
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime changedAt;

    public LineCapacityLog(Line line, int beforeCapacity, int afterCapacity, String reason, User changedBy) {
        this.line = line;
        this.beforeCapacity = beforeCapacity;
        this.afterCapacity = afterCapacity;
        this.reason = reason;
        this.changedBy = changedBy;
    }
}
