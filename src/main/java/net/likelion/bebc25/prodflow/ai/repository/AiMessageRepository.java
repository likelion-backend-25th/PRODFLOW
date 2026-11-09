package net.likelion.bebc25.prodflow.ai.repository;

import net.likelion.bebc25.prodflow.ai.entity.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiMessageRepository
        extends JpaRepository<AiMessage, Long> {

    // 대화방의 마지막 메시지 순서번호 조회
    @Query("""
            SELECT COALESCE(MAX(m.sequenceNo), 0)
            FROM AiMessage m
            WHERE m.aiConversation.id = :conversationId
            """)
    int findMaxSequenceNo(
            @Param("conversationId") Long conversationId
    );

    // 대화방의 메시지 기록을 순서대로 조회
    List<AiMessage> findByAiConversation_IdOrderBySequenceNoAsc(
            Long conversationId
    );
}
