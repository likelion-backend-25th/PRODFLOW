package net.likelion.bebc25.prodflow.quotation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.entity.QuotationItem;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.user.entity.User;

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
        // 견적 초안 엔티티 변환
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
            @NotBlank(message = "거래처명을 입력해주세요.")
            @Size(max = 255, message = "거래처명은 255자 이하여야 합니다.")
            String company,
            @NotBlank(message = "거래처 담당자명을 입력해주세요.")
            @Size(max = 50, message = "거래처 담당자명은 50자 이하여야 합니다.")
            String employee,
            @NotBlank(message = "이메일을 입력해주세요.")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
            String email,
            @NotBlank(message = "연락처를 입력해주세요.")
            @Pattern(regexp = "[0-9+() -]{8,50}", message = "연락처는 숫자와 +, 괄호, 공백, -로 8~50자여야 합니다.")
            String phone,
            @NotBlank(message = "우편번호를 입력해주세요.")
            @Size(max = 10, message = "우편번호는 10자 이하여야 합니다.")
            String zipcode,
            @NotBlank(message = "도로명 주소를 입력해주세요.")
            @Size(max = 100, message = "도로명 주소는 100자 이하여야 합니다.")
            String roadAddress,
            @NotBlank(message = "상세 주소를 입력해주세요.")
            @Size(max = 100, message = "상세 주소는 100자 이하여야 합니다.")
            String detailAddress
    ) {
        // 거래처 주소 변환
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
        // 견적 목록 응답 변환
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
        // 견적 상세 응답 변환
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
