package net.likelion.bebc25.prodflow.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.likelion.bebc25.prodflow.ai.enums.AiMessageRole;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "ai_message",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ai_conversation_id", "sequence_no"})
)
public class AiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto_increment
    private Long id;

    // 대화방이 지워지면 
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_conversation_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AiConversation aiConversation;

    @Column(nullable = false)
    private int sequenceNo;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AiMessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String generatedSql;

    @CreatedDate // Entity 최초 저장될 때 생성일시를 자동으로 주입
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    // 대화방에서 번호순서에 맞춰 누가 말하고 어떠한 내용을 담고 있는가
    public AiMessage(AiConversation aiConversation, int sequenceNo, AiMessageRole role, String content) {
        this.aiConversation = aiConversation;
        this.sequenceNo = sequenceNo;
        this.role = role;
        this.content = content;
    }

    // ai가 의도를 어떻게 해석하고 sql을 생성하여 저장했는가
    public void applyResult(String generatedSql) {
        this.generatedSql = generatedSql;
    }
}
