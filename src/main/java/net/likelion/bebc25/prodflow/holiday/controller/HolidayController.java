package net.likelion.bebc25.prodflow.holiday.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.holiday.dto.HolidayImportResponse;
import net.likelion.bebc25.prodflow.holiday.dto.HolidayResponse;
import net.likelion.bebc25.prodflow.holiday.service.HolidayService;
import net.likelion.bebc25.prodflow.holiday.service.HolidaySyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/holidays")
@RequiredArgsConstructor
public class HolidayController {
    private final HolidaySyncService holidaySyncService;
    private final HolidayService holidayService;

    @Operation(summary = "올해 공휴일 조회", description = "한국 시간 기준 올해 DB에 저장된 공휴일을 주말 포함 날짜순으로 조회한다.")
    @GetMapping
    public ResponseEntity<List<HolidayResponse>> findCurrentYearHolidays() {
        return ResponseEntity.ok(holidayService.findCurrentYearHolidays());
    }

    @Operation(summary = "연도별 공휴일 수집", description = "최고 관리자가 공공데이터포털 한국천문연구원 특일 정보의 공휴일을 수집해 신규 날짜만 저장한다.")
    @PostMapping("/import")
    public ResponseEntity<HolidayImportResponse> importHolidays(
            @RequestParam(name = "year", required = false) String year
    ) {
        // 연도 누락·형식 오류·범위 초과 시 API 호출 전 400 처리
        int requestedYear;
        try {
            requestedYear = Integer.parseInt(year);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("연도는 1~9999의 정수로 입력해주세요.", e);
        }
        if (requestedYear < 1 || requestedYear > 9999) {
            throw new IllegalArgumentException("연도는 1~9999의 정수로 입력해주세요.");
        }

        int addedCount = holidaySyncService.importHolidays(requestedYear);
        return ResponseEntity.ok(new HolidayImportResponse(requestedYear, addedCount));
    }
}
