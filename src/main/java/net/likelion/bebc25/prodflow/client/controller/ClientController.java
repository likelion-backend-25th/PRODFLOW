package net.likelion.bebc25.prodflow.client.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.client.dto.ClientDto;
import net.likelion.bebc25.prodflow.client.service.ClientService;
import net.likelion.bebc25.prodflow.global.security.principal.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

@Tag(name = "거래처", description = "거래처 조회,등록,수정 API")
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {
    private final ClientService clientService;

    @Operation(
            summary = "거래처 정보 등록",
            description = "RequestBody로 거래처 정보를 받아 등록한 뒤 상세 정보를 반환한다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공")
    })
    @PostMapping
    public ResponseEntity<ClientDto.DetailResponse> createClient(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ClientDto.Request request
    ) {
        ClientDto.DetailResponse response = clientService.createClient(request);
        return ResponseEntity.created(URI.create("/api/v1/clients/" + response.id())).body(response);
    }

    @Operation(
            summary = "거래처 정보 수정",
            description = "RequestBody로 거래처 정보를 받아 등록한 뒤 상세 정보를 반환한다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ClientDto.DetailResponse> updateClient(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "거래처 ID", example = "1")
            @PathVariable("id") Long clientId,
            @RequestBody ClientDto.Request request
    ) {
        ClientDto.DetailResponse response = clientService.updateClient(clientId, request);
        return ResponseEntity.created(URI.create("/api/v1/clients/" + clientId)).body(response);
    }

    @Operation(
            summary = "거래처 정보 일괄 등록",
            description = "RequestPart로 엑셀파일을 받아 거래처 정보들을 일괄 등록한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공")
    })
    @PostMapping("/import")
    public ResponseEntity<List<ClientDto.ImportResponse>> registerClients(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart(value = "file") MultipartFile multipartFile
    ) {
        List<ClientDto.ImportResponse> list = clientService.registerClients(multipartFile);
        return ResponseEntity.created(URI.create("/api/v1/clients/")).body(list);
    }

    @Operation(
            summary = "거래처 정보 상세 조회",
            description = "거래처 ID값을 받아와 해당하는 거래처의 상세 정보를 조회하여 반환한다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ClientDto.DetailResponse> getClientDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "거래처 ID", example = "1")
            @PathVariable("id") Long clientId
    ) {
        return ResponseEntity.ok(clientService.getClientDetail(clientId));
    }

    @Operation(
            summary = "거래처 정보 리스트 조회",
            description = "거래처들의 리스트를 페이지네이션하여 조회한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public ResponseEntity<Page<ClientDto.Response>> getClients(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "검색 키워드", example = "미래")
            @RequestParam(required = false) String keyword,
            @PageableDefault(
                    page = 0,
                    size = 8,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        return ResponseEntity.ok(clientService.findClients(keyword, pageable));
    }

    @Operation(
            summary = "거래처 수와 해당 거래처의 거래 요청 수 조회",
            description = "전체 거래처 수, 최근 한달간 등록된 거래처 수, 최근 한달간 거래 요청한 거래처 수를 계산하여 조회한다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/counts")
    public ResponseEntity<ClientDto.CountResponse> countClients() {
        return ResponseEntity.ok(clientService.countClients());
    }
}
