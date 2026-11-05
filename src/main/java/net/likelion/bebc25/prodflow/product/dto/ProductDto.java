package net.likelion.bebc25.prodflow.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ProductDto {


    @Schema(name = "ProductUpdateStatusRequest")
    public record UpdateStatusRequest(
            ProductStatus status
    ) {

    }

    @Schema(name = "ProductCreateRequest")
    public record CreateRequest(
            String modelCode,
            String productName,
            Long deviceCode,
            String deviceName,
            String productDescription,
            Long currentPrice
    ) {
    }


    @Schema(name = "ProductResponse")
    public record Response(
            Long id,
            String modelCode,
            String productName,
            String deviceName,
            String productDescription,
            Long currentPrice,
            ProductStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static Response from(Product product) {
            return new Response(
                    product.getId(),
                    product.getModelCode(),
                    product.getProductName(),
                    product.getDevice().getName(),
                    product.getProductDescription(),
                    product.getCurrentPrice(),
                    product.getStatus(),
                    product.getCreatedAt(),
                    product.getUpdatedAt()
            );
        }
    }

    @Schema(name = "ProductUpdateRequest")
    public record UpdateRequest(
            String productName,
            Long deviceCode,
            String productDescription,
            Long currentPrice
    ) {
    }

    @Schema(name = "ProductModelCodeExistsResponse")
    public record ModelCodeExistsResponse(
            String modelCode,
            boolean available
    ) {
    }

    @Schema(name = "ProductOrderHistoryResponse")
    public record OrderHistoryResponse(
            Long orderId,
            String orderNo,
            String clientName,
            String productName,
            int quantity,
            Long unitPrice,
            Long lineAmount,
            Long totalAmount,
            LocalDate estimatedDueDate,
            String statusCode,
            String statusName
    ) {
    }

}


