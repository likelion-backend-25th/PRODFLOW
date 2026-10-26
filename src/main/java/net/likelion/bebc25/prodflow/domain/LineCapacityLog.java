package net.likelion.bebc25.prodflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class LineCapacityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto_increment
    private Long id;

    @Column(name = "line_id", nullable = false)
    private Long lineId;

    @Column(nullable = false)
    private int before_capacity;

    @Column(nullable = false)
    private int after_capacity;

    @Column(nullable = false)
    private String reason;

    @Column(name = "user_id", nullable = false)
    private Long changedBy;

    @Column(nullable = false)
    private LocalDateTime changedAt;

}
