package net.likelion.bebc25.prodflow.holiday.dto;

import java.time.LocalDate;

// 공공데이터포털 공휴일 응답에서 추출한 날짜·명칭·공휴일 여부
public record HolidayApiResponse(
        LocalDate date,
        String name,
        String isHoliday
) {
}
