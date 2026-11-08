package net.likelion.bebc25.prodflow.holiday.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class HolidayTest {
    @Autowired
    private EntityManager em;

    private Long holidayId;

    // 조회할 공휴일 직접 준비
    @BeforeEach
    void setUp() {
        Holiday holiday = new Holiday(LocalDate.of(2099, 12, 25), "크리스마스");
        em.persist(holiday);
        holidayId = holiday.getId();

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("공휴일 등록과 조회")
    void saveAndFind() {
        Holiday holiday = new Holiday(LocalDate.of(2099, 1, 1), "새해");
        em.persist(holiday);
        em.flush();
        em.clear();

        Holiday findHoliday = em.find(Holiday.class, holiday.getId());
        assertThat(findHoliday).isNotNull();
        assertThat(findHoliday.getHolidayDate()).isEqualTo(LocalDate.of(2099, 1, 1));
        assertThat(findHoliday.getName()).isEqualTo("새해");
        assertThat(findHoliday.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("공휴일 조회와 1차 캐시")
    void read() {
        Holiday holiday = em.find(Holiday.class, holidayId);
        Holiday sameHoliday = em.find(Holiday.class, holidayId);

        assertThat(holiday).isSameAs(sameHoliday);
        assertThat(holiday.getName()).isEqualTo("크리스마스");
    }

    @Test
    @DisplayName("같은 날짜의 공휴일 중복 등록 실패")
    void rejectDuplicateDate() {
        Holiday duplicate = new Holiday(LocalDate.of(2099, 12, 25), "중복 크리스마스");

        assertThatThrownBy(() -> {
            em.persist(duplicate);
            em.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("공휴일 삭제")
    void delete() {
        Holiday holiday = em.find(Holiday.class, holidayId);
        em.remove(holiday);
        em.flush();
        em.clear();

        assertThat(em.find(Holiday.class, holidayId)).isNull();
    }
}
