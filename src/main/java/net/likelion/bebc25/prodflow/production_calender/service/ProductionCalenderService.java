package net.likelion.bebc25.prodflow.production_calender.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.production_calender.dto.ProductionCalenderDto;
import net.likelion.bebc25.prodflow.production_calender.repository.ProductionCalenderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductionCalenderService {

    private final ProductionCalenderRepository productionCalenderRepository;

    // 생산 일정 캘린더 조회
    public List<ProductionCalenderDto.Response> getCalenderSchedules() {
        List<String> statuses = List.of(
                "PREPARE",
                "PRODUCE",
                "PACK",
                "SUSPEND"
        );
        return productionCalenderRepository.findCalenderSchedules(ConfirmationStatus.CONFIRMED, statuses)
                .stream()
                .map(ProductionCalenderDto.Response::from)
                .toList();

    }

}
