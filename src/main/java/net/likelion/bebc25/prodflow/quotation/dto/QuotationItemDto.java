package net.likelion.bebc25.prodflow.quotation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import net.likelion.bebc25.prodflow.quotation.entity.QuotationItem;

public class QuotationItemDto {

    public record CreateRequest(
            @NotNull(message = "품목을 선택해주세요.")
            @Positive(message = "품목 ID는 양수여야 합니다.")
            Long productId,
            @Min(value = 100, message = "견적 수량은 최소 100개 이상이어야 합니다.")
            int quantity,
            @NotNull(message = "견적 단가를 입력해주세요.")
            @PositiveOrZero(message = "견적 단가는 0원 이상이어야 합니다.")
            Long unitPrice
    ) {
    }

    public record Response(
            Long id,
            Long productId,
            String productName,
            String deviceName,
            int quantity,
            Long unitPrice,
            Long lineAmount
    ) {
        // 견적 품목 응답 변환
        public static Response from(QuotationItem item) {
            return new Response(
                    item.getId(),
                    item.getProduct().getId(),
                    item.getProductName(),
                    item.getDeviceName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getLineAmount()
            );
        }
    }

}
