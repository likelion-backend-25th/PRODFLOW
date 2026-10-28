package net.likelion.bebc25.prodflow.quotation.controller;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationProductionPlanDto;
import net.likelion.bebc25.prodflow.quotation.service.QuotationProductionPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("api/v1/quotations")
@RequiredArgsConstructor
public class QuotationController {
    private final QuotationProductionPlanService planService;

    // 견적 품목의 라인별 임시 계획 등록. 수주 가능하면 저장
    @PostMapping("/{quotationId}/temp")
    public ResponseEntity<List<QuotationProductionPlanDto.Response>> create(
            @PathVariable Long quotationId,
            @RequestBody QuotationProductionPlanDto.CreateRequest request
    ) {
        List<QuotationProductionPlanDto.Response> response = planService.create(quotationId, request);
        return ResponseEntity
                .created(URI.create("api/v1/quotations/" + quotationId + "/temp"))
                .body(response);
    }
}
