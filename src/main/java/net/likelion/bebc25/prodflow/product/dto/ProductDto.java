package net.likelion.bebc25.prodflow.product.dto;

import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;

import java.time.LocalDateTime;

public class ProductDto {


    public record UpdateStatusRequest(
            ProductStatus status
    ){

    }

    public record CreateRequest(
            String modelCode,
            String productName,
            Long deviceCode,
            String productDescription,
            Long currentPrice

    ) {
    }

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


}
