package net.likelion.bebc25.prodflow.line.repository;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.line.entity.LineCapacityLog;
import net.likelion.bebc25.prodflow.line.repository.LineCapacityLogRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
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
class LineCapacityLogRepositoryTest {

    @Autowired
    EntityManager em;

    @Autowired
    LineCapacityLogRepository lineCapacityLogRepository;

    Long lineId;
    Long olderId;
    Long newerId;

    @BeforeEach
    void setUp() {
        Line line = new Line("이력-조회", 1000);
        line.setCreatedBy("서도윤");
        line.setUpdatedBy("서도윤");
        em.persist(line);
        User user = em.find(User.class, 1L);
        LineCapacityLog older = new LineCapacityLog(line, 1100, "먼저", user);
        LineCapacityLog newer = new LineCapacityLog(line, 1200, "나중", user);
        em.persist(older);
        em.persist(newer);
        em.flush();
        // 같은 초에 저장돼도 최근 변경이 앞으로 오게 시각을 고정한다.
        em.createNativeQuery("update line_capacity_log set changed_at = :changedAt where id = :id")
                .setParameter("changedAt", LocalDateTime.of(2026, 1, 1, 0, 0))
                .setParameter("id", older.getId())
                .executeUpdate();
        em.createNativeQuery("update line_capacity_log set changed_at = :changedAt where id = :id")
                .setParameter("changedAt", LocalDateTime.of(2026, 6, 1, 0, 0))
                .setParameter("id", newer.getId())
                .executeUpdate();
        lineId = line.getId();
        olderId = older.getId();
        newerId = newer.getId();
        em.clear();
    }

    @Test
    @DisplayName("생산량 이력은 최근 변경이 앞이다")
    void findByLineOrdersByChangedAtDesc() {
        List<LineCapacityLog> logs = lineCapacityLogRepository.findByLine_IdOrderByChangedAtDesc(lineId);

        assertThat(logs).extracting(LineCapacityLog::getId).containsExactly(newerId, olderId);
        assertThat(logs).extracting(LineCapacityLog::getReason).containsExactly("나중", "먼저");
    }

    @Test
    @DisplayName("이력이 없는 라인은 빈 목록이다")
    void findEmptyLine() {
        Line line = new Line("이력-없음", 1000);
        line.setCreatedBy("서도윤");
        line.setUpdatedBy("서도윤");
        em.persist(line);
        em.flush();

        assertThat(lineCapacityLogRepository.findByLine_IdOrderByChangedAtDesc(line.getId())).isEmpty();
    }
}
