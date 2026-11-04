package net.likelion.bebc25.prodflow.quotation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.entity.QuotationItem;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class QuotationDto {

    public record CreateRequest(
            @Positive(message = "거래처 ID는 양수여야 합니다.")
            Long clientId,
            @NotNull(message = "희망 납기일을 입력해주세요.")
            LocalDate requestedDueDate,
            LocalDate validUntil,
            boolean vatIncluded,
            @NotEmpty(message = "견적 품목을 하나 이상 입력해주세요.")
            List<QuotationItemDto.@NotNull(message = "견적 품목은 비어 있을 수 없습니다.") @Valid CreateRequest> items,
            @Valid ClientRequest newClient
    ) {
        // 예상 납기와 생산 일정은 서버에서 계산한 값을 전달받는다.
        public Quotation toEntity(
                String quotationNo,
                Client client,
                User user,
                LocalDate estimatedDueDate,
                LocalDate plannedStartDate,
                LocalDate plannedEndDate,
                LocalDate effectiveValidUntil,
                Long totalAmount
        ) {
            return new Quotation(
                    quotationNo,
                    client,
                    client.getEmployee(),
                    user,
                    requestedDueDate,
                    estimatedDueDate,
                    plannedStartDate,
                    plannedEndDate,
                    effectiveValidUntil,
                    vatIncluded,
                    QuotationStatus.DRAFT,
                    totalAmount
            );
        }
    }

    public record ClientRequest(
            @NotBlank @Size(max = 255) String company,
            @NotBlank(message = "거래처 담당자명을 입력해주세요.") @Size(max = 50) String employee,
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Pattern(regexp = "[0-9+() -]{8,50}") String phone,
            @NotBlank @Size(max = 10) String zipcode,
            @NotBlank @Size(max = 100) String roadAddress,
            @NotBlank @Size(max = 100) String detailAddress
    ) {
        public Address toAddress() {
            return new Address(zipcode, roadAddress, detailAddress);
        }
    }

    // 목록 총액은 견적에 저장된 값 사용
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
        public static Response from(Quotation quotation, CustomerOrder customerOrder) {
            return new Response(
                    quotation.getId(),
                    quotation.getQuotationNo(),
                    quotation.getClient().getId(),
                    quotation.getClient().getCompany(),
                    quotation.getRequestedDueDate(),
                    quotation.getEstimatedDueDate(),
                    quotation.getTotalAmount(),
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
            String clientName,
            String clientPhone,
            String clientEmail,
            String clientAddress,
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
            LocalDateTime updatedAt,
            String createdBy
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
            long totalQuantity = 0;
            List<QuotationItemDto.Response> itemResponses = new ArrayList<>();
            for (QuotationItem item : items) {
                totalQuantity += item.getQuantity();
                itemResponses.add(QuotationItemDto.Response.from(item));
            }

            Long customerOrderId = null;
            String orderNo = null;
            if (customerOrder != null) {
                customerOrderId = customerOrder.getId();
                orderNo = customerOrder.getOrderNo();
            }
            return new DetailResponse(
                    quotation.getId(),
                    quotation.getQuotationNo(),
                    quotation.getStatus(),
                    client.getId(),
                    client.getCompany(),
                    quotation.getClientName(),
                    quotation.getClientPhone(),
                    quotation.getClientEmail(),
                    quotation.getClientAddress(),
                    items.size(),
                    totalQuantity,
                    itemResponses,
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
                    customerOrderId,
                    orderNo,
                    quotation.getCreatedAt(),
                    quotation.getUpdatedAt(),
                    quotation.getCreatedBy()
            );
        }
    }
}
