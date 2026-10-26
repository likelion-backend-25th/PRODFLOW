package net.likelion.bebc25.prodflow.workorder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class WorkOrderLineChangeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto_increment
    private Long id;

    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = true)
    private int beforeLine;

    @Column(nullable = false)
    private int afterLine;

    @Column(nullable = false)
    private String note;

    @CreatedDate // Entity 최초 저장될 때 생성일시를 자동으로 주입
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
