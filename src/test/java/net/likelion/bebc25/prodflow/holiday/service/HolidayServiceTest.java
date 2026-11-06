package net.likelion.bebc25.prodflow.holiday.service;

import net.likelion.bebc25.prodflow.holiday.dto.HolidayResponse;
import net.likelion.bebc25.prodflow.holiday.entity.Holiday;
import net.likelion.bebc25.prodflow.holiday.repository.HolidayRepository;
import net.likelion.bebc25.prodflow.holiday.service.HolidayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class HolidayServiceTest {
    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private HolidayService holidayService;

    private int year;

    // 외부 API 대신 조회에 필요한 공휴일 직접 준비
    @BeforeEach
    void setUp() {
        year = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        holidayRepository.deleteAllInBatch();
        holidayRepository.saveAllAndFlush(List.of(
                new Holiday(LocalDate.of(year, 12, 25), "크리스마스"),
                new Holiday(LocalDate.of(year, 1, 1), "새해"),
                new Holiday(LocalDate.of(year - 1, 12, 25), "지난해 크리스마스"),
                new Holiday(LocalDate.of(year + 1, 1, 1), "다음해 새해")));
    }

    @Test
    @DisplayName("현재 연도 공휴일만 날짜순 조회")
    void findCurrentYearHolidays() {
        List<HolidayResponse> holidays = holidayService.findCurrentYearHolidays();

        assertThat(holidays).extracting(HolidayResponse::holidayDate)
                .containsExactly(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 25));
        assertThat(holidays).extracting(HolidayResponse::name)
                .containsExactly("새해", "크리스마스");
    }

    @Test
    @DisplayName("공휴일이 없으면 빈 목록 반환")
    void findNoHolidays() {
        holidayRepository.deleteAllInBatch();

        assertThat(holidayService.findCurrentYearHolidays()).isEmpty();
    }
}
