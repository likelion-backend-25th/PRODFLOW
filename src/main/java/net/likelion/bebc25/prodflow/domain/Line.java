package net.likelion.bebc25.prodflow.domain;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class Line {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto_increment
    private Long id;

    @Column(unique = true, nullable = false)
    private String lineName;

    @Column(nullable = false) // check 사용
    private int productionCapacity;

    @Column(nullable = false, columnDefinition = "AVAILABLE")
    @Enumerated(EnumType.STRING)
    private String status;

    @LastModifiedDate // Entity 마지막으로 저장될 때 수정일시를 자동으로 주입
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private LocalDate introducedDate;

}
