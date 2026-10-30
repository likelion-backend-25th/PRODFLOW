package net.likelion.bebc25.prodflow.client.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.client.dto.ClientDto;
import net.likelion.bebc25.prodflow.client.service.ClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/clients")
@RequiredArgsConstructor
public class ClientController {
    private final ClientService clientService;

    @Operation(
            summary = "거래처 정보 상세 조회",
            description = "거래처 ID값을 받아와 해당하는 거래처의 상세 정보를 조회하여 반환한다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ClientDto.DetailResponse> getClientDetail(
            @PathVariable("id") Long clientId
    ) {
        return ResponseEntity.ok(clientService.getClientDetail(clientId));
    }
}
