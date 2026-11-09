package net.likelion.bebc25.prodflow.customer_order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.common.entity.StatusHistory;
import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.customer_order.enums.NotifyChannel;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class CustomerOrderDto {
    // 수주 상태 변경 이력 응답
    public record HistoryResponse(
            Long id,
            String fromStatus,
            String toStatus,
            Long userId,
            String userName,
            String note,
            LocalDateTime createdAt
    ) {
        // 처리자 이름은 현재 직원 이름이 아닌 이력에 저장된 이름을 사용한다.
        public static HistoryResponse from(StatusHistory history) {
            return new HistoryResponse(
                    history.getId(),
                    history.getFromStatus(),
                    history.getToStatus(),
                    history.getUser() == null ? null : history.getUser().getId(),
                    history.getUserName(),
                    history.getNote(),
                    history.getCreatedAt());
        }
    }

    // 수주 등록 요청
    public record CreateRequest(
            @Schema(description = "수주 등록 기준 승인 견적 ID", example = "17")
            @NotNull @Positive Long quotationId,
            @NotEmpty List<@NotNull @Valid ItemRequest> items,
            @Schema(description = "화면에서 최종 확정한 납기일. 저장 직전 서버에서 재검증")
            @NotNull LocalDate dueDate,
            @NotNull Boolean useCompanyAddress,
            @Valid ShippingAddress shippingAddress,
            @NotBlank @Size(max = 255) String receiverName,
            @NotBlank @Pattern(regexp = "[0-9+() -]{8,50}") String receiverPhone,
            @NotNull NotifyChannel notifyChannel,
            @Size(max = 255) String memo
    ) {
        // 직접 배송지 입력 여부 확인
        @AssertTrue(message = "직접 배송 시 우편번호, 도로명 주소, 상세 주소를 입력해주세요.")
        @Schema(hidden = true)
        public boolean isShippingAddressPresent() {
            return !Boolean.FALSE.equals(useCompanyAddress) || shippingAddress != null;
        }
    }

    // 수주 품목 등록 요청
    public record ItemRequest(
            @Schema(description = "견적 상세 응답 items의 id. productId가 아님")
            @NotNull @Positive Long quotationItemId,
            @NotNull @Min(value = 100, message = "수주 수량은 품목별 100개 이상이어야 합니다.") Integer quantity
    ) {
    }

    // 배송지 입력
    public record ShippingAddress(
            @NotBlank @Size(max = 10) String zipcode,
            @NotBlank @Size(max = 100) String roadAddress,
            @NotBlank @Size(max = 100) String detailAddress
    ) {
    }

    // 수주 등록 응답
    public record CreateResponse(
            Long id,
            String orderNo,
            Long quotationId,
            Long totalAmount,
            LocalDate dueDate,
            LocalDate depositDueDate,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            String status,
            ConfirmationStatus confirmationStatus,
            OrderPaymentStatus paymentStatus
    ) {
        // 수주 등록 응답 변환
        public static CreateResponse from(CustomerOrder order) {
            return new CreateResponse(
                    order.getId(),
                    order.getOrderNo(),
                    order.getQuotation().getId(),
                    order.getTotalAmount(),
                    order.getEstimatedDueDate(),
                    order.getDepositDueDate(),
                    order.getPlannedStartDate(),
                    order.getPlannedEndDate(),
                    order.getStatusCode().getCode(),
                    order.getConfirmationStatus(),
                    order.getPaymentStatus());
        }
    }
}
