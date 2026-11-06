package net.likelion.bebc25.prodflow.production_calender.controller;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.production_calender.dto.ProductionCalenderDto;
import net.likelion.bebc25.prodflow.production_calender.service.ProductionCalenderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/production/calender")
@RequiredArgsConstructor
public class ProductionCalenderController {

    private final ProductionCalenderService productionCalenderService;

    // 생산 일정 캘린더 조회
    @GetMapping
    public List<ProductionCalenderDto.Response> getCalendarSchedules() {
        return productionCalenderService.getCalenderSchedules();
    }

}
