package net.likelion.bebc25.prodflow.product.dto;

import net.likelion.bebc25.prodflow.product.entity.ProductDrawing;

import java.time.LocalDateTime;

// 응답에 objectKey 넣지 않음 (화면에는 파일 이름만)
public class ProductDrawingDto {

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
}
