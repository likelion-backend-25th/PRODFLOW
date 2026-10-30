package net.likelion.bebc25.prodflow.quotation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.entity.QuotationItem;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class QuotationDto {

    public record CreateRequest(
            @NotNull(message = "거래처를 선택해주세요.")
            @Positive(message = "거래처 ID는 양수여야 합니다.")
            Long clientId,
            @NotNull(message = "희망 납기일을 입력해주세요.")
            LocalDate requestedDueDate,
            @NotNull(message = "견적 유효기간을 입력해주세요.")
            LocalDate validUntil,
            boolean vatIncluded,
            @NotEmpty(message = "견적 품목을 하나 이상 입력해주세요.")
            List<QuotationItemDto.@NotNull(message = "견적 품목은 비어 있을 수 없습니다.") @Valid CreateRequest> items
    ) {
        // 예상 납기와 생산 일정은 서버에서 계산한 값을 전달받는다.
        public Quotation toEntity(
                String quotationNo,
                Client client,
                User user,
                LocalDate estimatedDueDate,
                LocalDate plannedStartDate,
                LocalDate plannedEndDate
        ) {
            return new Quotation(
                    quotationNo,
                    client,
                    user,
                    requestedDueDate,
                    estimatedDueDate,
                    plannedStartDate,
                    plannedEndDate,
                    validUntil,
                    vatIncluded,
                    QuotationStatus.SENT
            );
        }
    }

    // 목록에서는 품목 전체를 불러오지 않고 집계된 총액을 전달받는다.
    public record Response(
            Long id,
            String quotationNo,
            Long clientId,
            String clientCompany,
            LocalDate requestedDueDate,
            LocalDate estimatedDueDate,
            Long totalAmount,
            QuotationStatus status,
            Long customerOrderId,
            String orderNo,
            LocalDate validUntil,
            Long userId,
            String userName,
            LocalDateTime createdAt
    ) {
        public static Response from(Quotation quotation, Long totalAmount, CustomerOrder customerOrder) {
            return new Response(
                    quotation.getId(),
                    quotation.getQuotationNo(),
                    quotation.getClient().getId(),
                    quotation.getClient().getCompany(),
                    quotation.getRequestedDueDate(),
                    quotation.getEstimatedDueDate(),
                    totalAmount,
                    quotation.getStatus(),
                    customerOrder == null ? null : customerOrder.getId(),
                    customerOrder == null ? "-" : customerOrder.getOrderNo(),
                    quotation.getValidUntil(),
                    quotation.getUser().getId(),
                    quotation.getUser().getName(),
                    quotation.getCreatedAt()
            );
        }
    }

    public record DetailResponse(
            Long id,
            String quotationNo,
            QuotationStatus status,
            Long clientId,
            String clientCompany,
            String clientEmployee,
            String clientPhone,
            String clientEmail,
            String zipcode,
            String roadAddress,
            String detailAddress,
            int itemCount,
            long totalQuantity,
            List<QuotationItemDto.Response> items,
            boolean vatIncluded,
            Long supplyAmount,
            Long vatAmount,
            Long totalAmount,
            Long depositAmount,
            Long balanceAmount,
            LocalDate requestedDueDate,
            LocalDate estimatedDueDate,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate validUntil,
            Long userId,
            String userName,
            Long customerOrderId,
            String orderNo,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        // 품목과 연결 수주는 별도로 조회하고, 금액은 서비스에서 계산해 전달한다.
        public static DetailResponse from(
                Quotation quotation,
                List<QuotationItem> items,
                CustomerOrder customerOrder,
                Long supplyAmount,
                Long vatAmount,
                Long totalAmount,
                Long depositAmount,
                Long balanceAmount
        ) {
            Client client = quotation.getClient();
            Address address = client.getAddress();
            return new DetailResponse(
                    quotation.getId(),
                    quotation.getQuotationNo(),
                    quotation.getStatus(),
                    client.getId(),
                    client.getCompany(),
                    client.getEmployee(),
                    client.getPhone(),
                    client.getEmail(),
                    address == null ? null : address.getZipcode(),
                    address == null ? null : address.getRoadAddress(),
                    address == null ? null : address.getDetailAddress(),
                    items.size(),
                    items.stream().mapToLong(QuotationItem::getQuantity).sum(),
                    items.stream().map(QuotationItemDto.Response::from).toList(),
                    quotation.isVatIncluded(),
                    supplyAmount,
                    vatAmount,
                    totalAmount,
                    depositAmount,
                    balanceAmount,
                    quotation.getRequestedDueDate(),
                    quotation.getEstimatedDueDate(),
                    quotation.getPlannedStartDate(),
                    quotation.getPlannedEndDate(),
                    quotation.getValidUntil(),
                    quotation.getUser().getId(),
                    quotation.getUser().getName(),
                    customerOrder == null ? null : customerOrder.getId(),
                    customerOrder == null ? null : customerOrder.getOrderNo(),
                    quotation.getCreatedAt(),
                    quotation.getUpdatedAt()
            );
        }
    }
}
