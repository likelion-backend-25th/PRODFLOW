package net.likelion.bebc25.prodflow.common.repository;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.common.enums.HistoryEntityType;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class StatusHistoryRepositoryTest {
    @Autowired
    private EntityManager em;

    @Autowired
    private StatusHistoryRepository statusHistoryRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().name("이력 담당자").email("history@test.com")
                .password("1234").phone("010-1111-2222").role(UserRole.ORDER_MANAGER).build();
        em.persist(user);
    }

    @Test
    @DisplayName("다른 수주와 같은 ID의 다른 도메인 이력을 제외한다")
    void findByEntityAndEntityId() {
        StatusHistory target = statusHistoryRepository.save(new StatusHistory(
                HistoryEntityType.ORDER, 900000L, "WAITING", "CANCELLED", user));
        statusHistoryRepository.save(new StatusHistory(HistoryEntityType.ORDER, 900001L, "WAITING", "CANCELLED", user));
        statusHistoryRepository.save(new StatusHistory(HistoryEntityType.WORK_ORDER, 900000L, "PREPARE", "PRODUCE", user));
        statusHistoryRepository.save(new StatusHistory(HistoryEntityType.LINE, 900000L, "AVAILABLE", "BROKEN", user));
        em.flush();
        em.clear();

        List<StatusHistory> histories = statusHistoryRepository
                .findByEntityAndEntityIdOrderByCreatedAtDescIdDesc(HistoryEntityType.ORDER, 900000L);

        assertThat(histories).extracting(StatusHistory::getId).containsExactly(target.getId());
        assertThat(statusHistoryRepository
                .findByEntityAndEntityIdOrderByCreatedAtDescIdDesc(HistoryEntityType.ORDER, Long.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("처리 시각 내림차순이고 같은 시각이면 ID 내림차순으로 조회한다")
    void orderByCreatedAtAndIdDescending() {
        LocalDateTime recentTime = LocalDateTime.of(2026, 11, 6, 10, 0);
        StatusHistory first = saveHistoryAt(recentTime);
        StatusHistory second = saveHistoryAt(recentTime);
        StatusHistory older = saveHistoryAt(recentTime.minusDays(1));
        em.clear();

        List<StatusHistory> histories = statusHistoryRepository
                .findByEntityAndEntityIdOrderByCreatedAtDescIdDesc(HistoryEntityType.ORDER, 900000L);

        assertThat(histories).extracting(StatusHistory::getId)
                .containsExactly(second.getId(), first.getId(), older.getId());
    }

    // 동일 시각 정렬을 검증하도록 DB의 생성 시각을 고정한다.
    private StatusHistory saveHistoryAt(LocalDateTime createdAt) {
        StatusHistory history = statusHistoryRepository.save(new StatusHistory(
                HistoryEntityType.ORDER, 900000L, "WAITING", "CANCELLED", user));
        em.flush();
        em.createQuery("update StatusHistory h set h.createdAt = :createdAt where h.id = :id")
                .setParameter("createdAt", createdAt).setParameter("id", history.getId()).executeUpdate();
        return history;
    }
}
