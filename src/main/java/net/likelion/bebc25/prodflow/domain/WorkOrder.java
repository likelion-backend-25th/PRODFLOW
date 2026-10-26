package net.likelion.bebc25.prodflow.domain;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Getter
@NoArgsConstructor
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto_increment
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "line_id", nullable = false)
    private Long lineId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 작업지시서 하나에 담당 품목은 1개
    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, unique = true)
    private String workNo;

    @Column(name = "work_order_date", nullable = false)
    private LocalDate workOrderDate;

    @Column(nullable = false)
    private LocalDate workStartDate;

    @Column(nullable = false)
    private LocalDate workEndDate;

    @Column(columnDefinition = "TEXT")
    private String workingContent;

    @CreatedDate // Entity 최초 저장될 때 생성일시를 자동으로 주입
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate // Entity 마지막으로 저장될 때 수정일시를 자동으로 주입
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
