package net.likelion.bebc25.prodflow.ai.entity;


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
import net.likelion.bebc25.prodflow.common.entity.BaseTimeEntity;
import net.likelion.bebc25.prodflow.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiConversation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto_increment
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = true)
    private String title;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean is_closed;

    private LocalDateTime closedAt;

    // 대화방 생성
    public AiConversation(User user, String title) {
        this.user = user;
        this.title = title;
        this.is_closed = false;
    }

    // 대화방 종료 (종료 상태가 아닌경우 종료하고, 종료시간을 갱신)
    public void close() {
        if (Boolean.TRUE.equals(this.is_closed)) {
            throw new IllegalArgumentException("종료된 대화방입니다.");
        }
        this.is_closed = true;
        this.closedAt = LocalDateTime.now();
    }

    // 대화방 제목 변경
    public void changeTitle(String title) {
        this.title = title;
    }


}
