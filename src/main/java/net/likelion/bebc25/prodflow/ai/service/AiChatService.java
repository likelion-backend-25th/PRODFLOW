package net.likelion.bebc25.prodflow.ai.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.ai.dto.AiDto;
import net.likelion.bebc25.prodflow.ai.entity.AiConversation;
import net.likelion.bebc25.prodflow.ai.entity.AiMessage;
import net.likelion.bebc25.prodflow.ai.enums.AiMessageRole;
import net.likelion.bebc25.prodflow.ai.repository.AiConversationRepository;
import net.likelion.bebc25.prodflow.ai.repository.AiMessageRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AiChatService {

    private final AiConversationRepository aiConversationRepository;
    private final AiMessageRepository aiMessageRepository;
    private final UserRepository userRepository;

    // 대화방 생성
    @Transactional
    public Long createConversation(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자 입니다."));

        AiConversation aiConversation =
                new AiConversation(user, "새 대화");

        return aiConversationRepository.save(aiConversation).getId();
    }

    // 대화방 소유자 및 종료 여부 확인
    public void validateConversation(Long conversationId, Long userId) {

        AiConversation conversation = aiConversationRepository
                .findById(conversationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 대화방입니다."));

        validateAccess(conversation, userId);
    }

    // 대화방 접근 권한 및 종료 상태 검증
    private void validateAccess(AiConversation conversation, Long userId) {

        // 현재 로그인한 사용자의 대화방인지 확인
        if (!conversation.getUser().getId().equals(userId)) {
            throw new SecurityException("대화방 접근 권한이 없습니다.");
        }

        // 종료된 대화방인지 확인
        if (Boolean.TRUE.equals(conversation.getIsClosed())) {
            throw new IllegalStateException("종료된 대화방입니다.");
        }
    }

    // 사용자 질문 및 Ai 답변 저장
    @Transactional
    public void saveMessage(
            Long conversationId,
            Long userId,
            String question,
            String answer
    ) {
        // 동시 메시지 저장 시 순서번호 충돌 방지
        AiConversation conversation = aiConversationRepository
                .findByIdForUpdate(conversationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 대화방입니다."));

        validateAccess(conversation, userId);

        // 마지막 메세지 순서번호 조회
        int lastSequence = aiMessageRepository.
                findMaxSequenceNo(conversationId);

        // 최초 질문인 경우에만 제목 설정
        if (lastSequence == 0) {
            conversation.setInitialTitle(question);
        }

        // 사용자 질문 저장
        AiMessage userMessage = new AiMessage(
                conversation,
                lastSequence + 1, // 저장 위치
                AiMessageRole.USER,
                question
        );

        // AI 답변 저장
        AiMessage assistantMessage = new AiMessage(
                conversation,
                lastSequence + 2,
                AiMessageRole.ASSISTANT,
                answer
        );
        // 유저의 질문과 ai 답변을 저장
        aiMessageRepository.saveAll(List.of(userMessage, assistantMessage));
    }

    // 본인 대화방 목록 조회
    public List<AiDto.ConversationResponse> getMyConversations(Long userId) {

        return aiConversationRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(conversation -> new AiDto.ConversationResponse(
                        conversation.getId(),
                        conversation.getTitle(),
                        conversation.getIsClosed(),
                        conversation.getCreatedAt()
                ))
                .toList();
    }

    // 대화방 상세 조회
    public AiDto.ConversationDetailResponse getConversationDetail(
            Long conversationId,
            Long userId
    ) {
        // 본인 대화방인지 확인
        AiConversation conversation = aiConversationRepository
                .findByIdAndUser_Id(conversationId, userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "대화방이 존재하지 않거나 접근 권한이 없습니다."
                        ));

        // 이전 대화 메시지 조회 (순서대로)
        List<AiDto.MessageResponse> messages = aiMessageRepository
                .findByAiConversation_IdOrderBySequenceNoAsc(conversationId)
                .stream()
                .map(message -> new AiDto.MessageResponse(
                        message.getId(),
                        message.getSequenceNo(),
                        message.getRole(),
                        message.getContent(),
                        message.getResultJson(),
                        message.getCreatedAt()
                ))
                .toList();

        return new AiDto.ConversationDetailResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getIsClosed(),
                messages
        );
    }


    // 사용자 직접 대화방 제목 변경
    @Transactional
    public void changeConversationTitle(
            Long conversationId,
            Long userId,
            String title
    ) {
        AiConversation conversation = aiConversationRepository
                .findById(conversationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 대화방입니다."));

        // 본인 대화방인지 확인
        validateAccess(conversation, userId);

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("제목을 입력해주세요.");
        }

        if (title.strip().length() > 50) {
            throw new IllegalArgumentException("제목은 50자 이하로 입력해주세요.");
        }

        conversation.changeTitle(title.strip());
    }
}
