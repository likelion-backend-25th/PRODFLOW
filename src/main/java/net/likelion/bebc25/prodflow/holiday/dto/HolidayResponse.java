package net.likelion.bebc25.prodflow.holiday.dto;

import net.likelion.bebc25.prodflow.holiday.entity.Holiday;

import java.time.LocalDate;

public record HolidayResponse(Long id, LocalDate holidayDate, String name) {
    public static HolidayResponse from(Holiday holiday) {
        return new HolidayResponse(holiday.getId(), holiday.getHolidayDate(), holiday.getName());
    }
}
