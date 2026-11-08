package net.likelion.bebc25.prodflow.line.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import net.likelion.bebc25.prodflow.line.enums.LineStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// CI 에러 임시 해결 (@Sql 해당 부분 제거할 때 @Sql import 도 같이 삭제)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Sql("/data.sql")
@Transactional
class LineTest {

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("라인은 이름과 생산량으로 저장되고 초기 상태는 가동 가능이다")
    void saveWithAvailableStatus() {
        Line line = new Line("엔티티-라인", 500);
        line.setCreatedBy("서도윤");
        line.setUpdatedBy("서도윤");
        em.persist(line);
        em.flush();
        em.clear();

        Line saved = em.find(Line.class, line.getId());
        assertThat(saved.getLineName()).isEqualTo("엔티티-라인");
        assertThat(saved.getProductionCapacity()).isEqualTo(500);
        assertThat(saved.getStatus()).isEqualTo(LineStatus.AVAILABLE);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("이름, 생산량, 상태 변경이 저장된다")
    void changeNameCapacityAndStatus() {
        Line line = new Line("엔티티-변경", 500);
        line.setCreatedBy("서도윤");
        line.setUpdatedBy("서도윤");
        em.persist(line);
        em.flush();

        line.changeName("엔티티-개정");
        line.editProduction(800);
        line.changeStatus(LineStatus.BROKEN);
        em.flush();
        em.clear();

        Line saved = em.find(Line.class, line.getId());
        assertThat(saved.getLineName()).isEqualTo("엔티티-개정");
        assertThat(saved.getProductionCapacity()).isEqualTo(800);
        assertThat(saved.getStatus()).isEqualTo(LineStatus.BROKEN);
    }

    @Test
    @DisplayName("같은 이름의 라인은 저장하지 않는다")
    void rejectDuplicateName() {
        Line line = new Line("A라인", 500);
        line.setCreatedBy("서도윤");
        line.setUpdatedBy("서도윤");

        assertThatThrownBy(() -> {
            em.persist(line);
            em.flush();
        }).isInstanceOf(PersistenceException.class);
    }
}
