package net.likelion.bebc25.prodflow.holiday.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.holiday.dto.HolidayResponse;
import net.likelion.bebc25.prodflow.holiday.repository.HolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HolidayService {
    private final HolidayRepository holidayRepository;

    // 한국 시간 기준 올해 계산
    // DB 조회만 수행, 외부 호출·자동 수집 없음
    public List<HolidayResponse> findCurrentYearHolidays() {
        int year = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        return holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(
                        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream().map(HolidayResponse::from).toList();
    }
}
