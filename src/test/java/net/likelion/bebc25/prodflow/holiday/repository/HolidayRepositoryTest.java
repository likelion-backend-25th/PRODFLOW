package net.likelion.bebc25.prodflow.holiday.repository;

import jakarta.persistence.EntityManager;
import net.likelion.bebc25.prodflow.holiday.entity.Holiday;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class HolidayRepositoryTest {
    @Autowired
    private EntityManager em;

    @Autowired
    private HolidayRepository holidayRepository;

    private Long holidayId;

    // 기간 조회에 필요한 공휴일 직접 준비
    @BeforeEach
    void setUp() {
        Holiday christmas = new Holiday(LocalDate.of(2099, 12, 25), "크리스마스");
        holidayRepository.save(christmas);
        holidayId = christmas.getId();
        holidayRepository.save(new Holiday(LocalDate.of(2099, 1, 1), "새해"));
        holidayRepository.save(new Holiday(LocalDate.of(2098, 12, 25), "지난해 크리스마스"));

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("레포지토리로 저장한 공휴일 조회")
    void findById() {
        Holiday holiday = holidayRepository.findById(holidayId).orElseThrow();

        assertThat(holiday.getHolidayDate()).isEqualTo(LocalDate.of(2099, 12, 25));
        assertThat(holiday.getName()).isEqualTo("크리스마스");
    }

    @Test
    @DisplayName("기간 안의 공휴일만 날짜 오름차순 조회")
    void findByDateRange() {
        List<Holiday> holidays = holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(
                LocalDate.of(2099, 1, 1), LocalDate.of(2099, 12, 31));

        assertThat(holidays).hasSize(2);
        assertThat(holidays).extracting(Holiday::getHolidayDate)
                .containsExactly(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 12, 25));
    }

    @Test
    @DisplayName("조회 기간의 시작일과 종료일 포함")
    void includeBoundaryDate() {
        List<Holiday> holidays = holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(
                LocalDate.of(2099, 12, 25), LocalDate.of(2099, 12, 25));

        assertThat(holidays).hasSize(1);
        assertThat(holidays.getFirst().getId()).isEqualTo(holidayId);
    }

    @Test
    @DisplayName("공휴일이 없는 기간은 빈 목록 반환")
    void findEmptyDateRange() {
        List<Holiday> holidays = holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(
                LocalDate.of(2099, 2, 1), LocalDate.of(2099, 2, 28));

        assertThat(holidays).isEmpty();
    }
}
