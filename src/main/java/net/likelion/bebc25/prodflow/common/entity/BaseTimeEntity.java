package net.likelion.bebc25.prodflow.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass // 다른 Entity의 부모 클래스로 사용
@EntityListeners(AuditingEntityListener.class) // 아래의 이벤트 감지
public abstract class BaseTimeEntity { // 추상 클래스 new 생성 안됨

    @CreatedDate // Entity 최초 저장될 때 생성일시를 자동으로 주입
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate // Entity 마지막으로 저장될 때 수정일시를 자동으로 주입
    @Column(nullable = false)
    private LocalDateTime updatedAt;

}