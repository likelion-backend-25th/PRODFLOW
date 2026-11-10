package net.likelion.bebc25.prodflow.customer_order.dto;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.customer_order.enums.NotifyChannel;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class CustomerOrderDetailDto {
    // 수주 상세 응답
    public record Response(
            Long id,
            String orderNo,
            Long quotationId,
            String quotationNo,
            Long userId,
            String userName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            ConfirmationStatus confirmationStatus,
            Long statusCodeId,
            String status,
            String statusName,
            OrderPaymentStatus paymentStatus,
            ClientResponse client,
            List<ItemResponse> items,
            boolean vatIncluded,
            long supplyAmount,
            long vatAmount,
            long totalAmount,
            long totalQuantity,
            LocalDate requestedDueDate,
            LocalDate dueDate,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate productionCompletedAt,
            LocalDate depositDueDate,
            String shippingAddress,
            String receiverName,
            String receiverPhone,
            NotifyChannel notifyChannel,
            String memo,
            CustomerOrderListDto.DelayResponse delay
    ) {
        // 수주 상세 응답 변환
        public static Response from(CustomerOrder order, List<ItemResponse> items,
                                    long supplyAmount, CustomerOrderListDto.DelayResponse delay) {
            long totalQuantity = 0;
            for (ItemResponse item : items) {
                totalQuantity += item.quantity();
            }
            return new Response(
                    order.getId(),
                    order.getOrderNo(),
                    order.getQuotation().getId(),
                    order.getQuotation().getQuotationNo(),
                    order.getUser().getId(),
                    order.getUser().getName(),
                    order.getCreatedAt(),
                    order.getUpdatedAt(),
                    order.getConfirmationStatus(),
                    order.getStatusCode().getId(),
                    order.getStatusCode().getCode(),
                    order.getStatusCode().getName(),
                    order.getPaymentStatus(),
                    ClientResponse.from(order),
                    items,
                    order.isVatIncluded(),
                    supplyAmount,
                    order.getTotalAmount() - supplyAmount,
                    order.getTotalAmount(),
                    totalQuantity,
                    order.getRequestedDueDate(),
                    order.getEstimatedDueDate(),
                    order.getPlannedStartDate(),
                    order.getPlannedEndDate(),
                    order.getEndDate(),
                    order.getDepositDueDate(),
                    order.getShippingAddress(),
                    order.getReceiverName(),
                    order.getReceiverPhone(),
                    order.getNotifyChannel(),
                    order.getMemo(),
                    delay);
        }
    }

    // 담당자·주소는 수주 등록 시 저장한 값을 사용한다.
    public record ClientResponse(
            Long id,
            String companyName,
            String contactName,
            String email,
            String phone,
            String address
    ) {
        // 수주 등록 당시 연락처와 주소로 응답 변환
        public static ClientResponse from(CustomerOrder order) {
            return new ClientResponse(
                    order.getClient().getId(),
                    order.getClient().getCompany(),
                    order.getContactName(),
                    order.getEmail(),
                    order.getPhone(),
                    order.getAddress());
        }
    }

    public record ItemResponse(
            Long id,
            Long productId,
            String productName,
            String deviceName,
            int quantity,
            long unitPrice,
            long supplyAmount,
            long vatAmount,
            long lineAmount
    ) {
        // 저장된 수주 품목과 계산한 공급가액으로 응답 변환
        public static ItemResponse from(OrderProduct item, long supplyAmount) {
            long lineAmount = item.lineAmount();
            return new ItemResponse(
                    item.getId(),
                    item.getProduct().getId(),
                    item.getProductName(),
                    item.getProduct().getDevice().getName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    supplyAmount,
                    lineAmount - supplyAmount,
                    lineAmount);
        }
    }

}
