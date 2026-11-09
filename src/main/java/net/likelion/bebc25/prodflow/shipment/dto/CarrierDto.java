package net.likelion.bebc25.prodflow.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import net.likelion.bebc25.prodflow.shipment.entity.Carrier;

public class CarrierDto {

    private CarrierDto() {
    }

    @Schema(name = "CarrierResponse")
    public record Response(
            String code,
            String name
    ) {
        public static Response from(Carrier carrier) {
            return new Response(carrier.getCode(), carrier.getName());
        }
    }
}
