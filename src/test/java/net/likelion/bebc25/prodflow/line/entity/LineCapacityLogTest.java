package net.likelion.bebc25.prodflow.line.entity;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// CI 에러 임시 해결 (@Sql 해당 부분 제거할 때 @Sql import 도 같이 삭제)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Sql("/data.sql")
@Transactional
class LineCapacityLogTest {

    @Autowired
    EntityManager em;

    Line line;
    User user;

    @BeforeEach
    void setUp() {
        line = new Line("이력-라인", 1000);
        line.setCreatedBy("서도윤");
        line.setUpdatedBy("서도윤");
        em.persist(line);
        user = em.find(User.class, 1L);
        em.flush();
        em.clear();
        line = em.find(Line.class, line.getId());
        user = em.find(User.class, user.getId());
    }

    @Test
    @DisplayName("변경 전 생산량은 이력 생성 시점의 라인이고 변경 후 생산량은 인자다")
    void captureBeforeCapacityBeforeLineChanges() {
        LineCapacityLog log = new LineCapacityLog(line, 1200, "설비 증설", user);
        line.editProduction(1200);
        em.persist(log);
        em.flush();
        em.clear();

        LineCapacityLog saved = em.find(LineCapacityLog.class, log.getId());
        assertThat(saved.getBeforeCapacity()).isEqualTo(1000);
        assertThat(saved.getAfterCapacity()).isEqualTo(1200);
        assertThat(saved.getReason()).isEqualTo("설비 증설");
        assertThat(saved.getLine().getId()).isEqualTo(line.getId());
        assertThat(saved.getChangedBy().getName()).isEqualTo("서도윤");
        assertThat(saved.getChangedAt()).isNotNull();
    }
}
