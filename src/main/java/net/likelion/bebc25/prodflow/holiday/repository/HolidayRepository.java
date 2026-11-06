package net.likelion.bebc25.prodflow.holiday.repository;

import net.likelion.bebc25.prodflow.holiday.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    // 시작일·종료일 포함, 공휴일 날짜 오름차순 조회
    List<Holiday> findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate startDate, LocalDate endDate);
}

