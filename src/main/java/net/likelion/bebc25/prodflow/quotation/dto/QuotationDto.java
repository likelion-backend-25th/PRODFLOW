package net.likelion.bebc25.prodflow.quotation.dto;

import jakarta.validation.constraints.NotNull;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class QuotationDto {

    public record CreateRequest(
            @NotNull(message = "거래처를 선택해주세요.")
            Long clientId,
            @NotNull(message = "희망 납기일을 입력해주세요.")
            LocalDate requestedDueDate,
            @NotNull(message = "예상 납기일을 입력해주세요.")
            LocalDate estimatedDueDate,
            @NotNull(message = "생산 시작 예정일을 입력해주세요.")
            LocalDate plannedStartDate,
            @NotNull(message = "생산 종료 예정일을 입력해주세요.")
            LocalDate plannedEndDate,
            @NotNull(message = "견적 유효기간을 입력해주세요.")
            LocalDate validUntil,
            boolean vatIncluded
    ) {
        public Quotation toEntity(String quotationNo, Client client, User user) {
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

    public record Response(
            Long id,
            String quotationNo,
            Long clientId,
            String clientCompany,
            Long userId,
            String userName,
            LocalDate requestedDueDate,
            LocalDate estimatedDueDate,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate validUntil,
            boolean vatIncluded,
            QuotationStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static Response from(Quotation quotation) {
            return new Response(
                    quotation.getId(),
                    quotation.getQuotationNo(),
                    quotation.getClient().getId(),
                    quotation.getClient().getCompany(),
                    quotation.getUser().getId(),
                    quotation.getUser().getName(),
                    quotation.getRequestedDueDate(),
                    quotation.getEstimatedDueDate(),
                    quotation.getPlannedStartDate(),
                    quotation.getPlannedEndDate(),
                    quotation.getValidUntil(),
                    quotation.isVatIncluded(),
                    quotation.getStatus(),
                    quotation.getCreatedAt(),
                    quotation.getUpdatedAt()
            );
        }
    }
}
