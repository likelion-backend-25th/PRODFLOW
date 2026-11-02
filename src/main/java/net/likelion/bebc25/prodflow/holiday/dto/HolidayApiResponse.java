package net.likelion.bebc25.prodflow.holiday.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HolidayApiResponse(
        LocalDate date,
        String localName,
        String countryCode,
        List<String> types
) {
}
