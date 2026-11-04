package net.likelion.bebc25.prodflow.shipment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


//외부 배송조회 API(스마트택배) 응답을 담는 상자.
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrackingApiDto(
        String invoiceNo,  // 운송장 번호
        String result,     // 조회 결과 Y/N
        Boolean complete,  // 배송 완료 여부
        Integer level,     // 진행단계 0~6
        String estimate    // 배송 예정 시간 (shipment 캐시용)
) {
}
