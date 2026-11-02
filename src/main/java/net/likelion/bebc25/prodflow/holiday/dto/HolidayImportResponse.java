package net.likelion.bebc25.prodflow.holiday.dto;

public record HolidayImportResponse(
        int year,
        int addedCount
) {
    // record 선언만으로 equals(), hashCode(), toString() 자동 생성됨.
}
