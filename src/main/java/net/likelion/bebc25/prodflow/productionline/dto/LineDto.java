package net.likelion.bebc25.prodflow.productionline.dto;

import net.likelion.bebc25.prodflow.productionline.entity.Line;
import net.likelion.bebc25.prodflow.productionline.enums.LineStatus;

import java.time.LocalDateTime;

public class LineDto {

    // 라인 등록
    public record CreateRequest(
            String lineName,
            int productionCapacity
    ) {
    }

    public record UpdateCapacityRequest(
            int productionCapacity
    ) {
    }

    public record UpdateStatusRequest(
            LineStatus status
    ) {
    }

    public record Response(
            Long id,
            String lineName,
            int productionCapacity,
            LineStatus status,
            LocalDateTime updatedAt
    ) {
        public static Response from(Line line) {
            return new Response(
                    line.getId(),
                    line.getLineName(),
                    line.getProductionCapacity(),
                    line.getStatus(),
                    line.getUpdatedAt());
        }
    }
}
