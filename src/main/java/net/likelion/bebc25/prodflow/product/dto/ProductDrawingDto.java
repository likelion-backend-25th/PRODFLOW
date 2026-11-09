package net.likelion.bebc25.prodflow.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import net.likelion.bebc25.prodflow.product.entity.ProductDrawing;

import java.time.Instant;
import java.time.LocalDateTime;

// 응답에 objectKey·버킷명은 넣지 않는다.
public class ProductDrawingDto {

    @Schema(name = "ProductDrawingResponse")
    public record Response(
            Long id,
            String fileName,
            LocalDateTime createdAt
    ) {
        public static Response from(ProductDrawing drawing) {
            return new Response(
                    drawing.getId(),
                    drawing.getFileName(),
                    drawing.getCreatedAt()
            );
        }
    }

    @Schema(name = "ProductDrawingCurrentResponse")
    public record CurrentResponse(
            Long id,
            String fileName,
            LocalDateTime createdAt,
            String previewUrl,
            Instant expiresAt
    ) {
        public static CurrentResponse from(ProductDrawing drawing, String previewUrl, Instant expiresAt) {
            return new CurrentResponse(
                    drawing.getId(),
                    drawing.getFileName(),
                    drawing.getCreatedAt(),
                    previewUrl,
                    expiresAt
            );
        }
    }
}
