package net.likelion.bebc25.prodflow.ai.repository;

import jakarta.persistence.LockModeType;
import net.likelion.bebc25.prodflow.ai.entity.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    // 메시지 순서번호 생성 시 동시 저장 충돌 방지
    // AI 답변이 완료될 때까지 추가 질문 전송을 막음
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM AiConversation c
            WHERE c.id = :conversationId
            """)
    Optional<AiConversation> findByIdForUpdate(
            @Param("conversationId") Long conversationId
    );

    // 활성화된 대화방 목록 조회
    List<AiConversation> findByUser_IdAndIsClosedFalseOrderByCreatedAtDesc(
            Long userId
    );

    // 본인의 활성 대화방만 조회
    Optional<AiConversation> findByIdAndUser_IdAndIsClosedFalse(
            Long conversationId,
            Long userId
    );
}
