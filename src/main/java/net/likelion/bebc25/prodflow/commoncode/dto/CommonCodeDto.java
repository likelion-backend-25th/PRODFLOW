package net.likelion.bebc25.prodflow.commoncode.dto;

import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;

public class CommonCodeDto {

    public record Response(
            Long id,
            String code,
            String name,
            Integer sortOrder
    ) {
        public static Response from(CommonCode commonCode) {
            return new Response(
                    commonCode.getId(),
                    commonCode.getCode(),
                    commonCode.getName(),
                    commonCode.getSortOrder()
            );
        }
    }
}
