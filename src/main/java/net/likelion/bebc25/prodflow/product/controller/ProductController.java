package net.likelion.bebc25.prodflow.product.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.product.dto.ProductDrawingDto;
import net.likelion.bebc25.prodflow.product.dto.ProductDto;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;
import net.likelion.bebc25.prodflow.product.service.ProductDrawingService;
import net.likelion.bebc25.prodflow.product.service.ProductService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductDrawingService productDrawingService;


    @Operation(
            summary = "생산품목 등록",
            description = "RequestBody로 생산품목 정보를 받아 등록한 뒤 상세 정보를 반환한다. 기존 기종 ID(deviceCode) 또는 새 기종명(deviceName) 중 하나만 입력한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공")
    })
    @PostMapping
    public ResponseEntity<ProductDto.Response> create(@RequestBody ProductDto.CreateRequest request) {
        ProductDto.Response response = productService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/products/" + response.id()))
                .body(response);
    }

    @Operation(
            summary = "생산품목 리스트 조회",
            description = "생산품목 목록을 조회한다. status로 생산가능/생산중단을 필터할 수 있다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public ResponseEntity<List<ProductDto.Response>> getList(
            @Parameter(description = "생산품목 상태", example = "AVAILABLE")
            @RequestParam(required = false) ProductStatus status
    ) {
        List<ProductDto.Response> response = productService.getList(status);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "생산품목 상세 조회",
            description = "생산품목 ID로 상세 정보를 조회하여 반환한다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto.Response> getDetail(
            @Parameter(description = "생산품목 ID", example = "1")
            @PathVariable Long id
    ) {
        ProductDto.Response response = productService.getDetail(id);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "품목 관련 수주·생산 이력 조회",
            description = "해당 품목이 포함된 최근 수주 이력을 조회한다. 최근 연결 수주 카드와 관련 생산 및 수주 이력 표에서 사용한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/{id}/orders")
    public ResponseEntity<List<ProductDto.OrderHistoryResponse>> getOrderHistories(
            @Parameter(description = "생산품목 ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "조회 건수", example = "3")
            @RequestParam(defaultValue = "3") int size
    ) {
        return ResponseEntity.ok(productService.getOrderHistories(id, size));
    }

    @Operation(
            summary = "생산품목 검색",
            description = "키워드와 상태로 생산품목을 검색한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/search")
    public ResponseEntity<List<ProductDto.Response>> search(
            @Parameter(description = "검색 키워드", example = "쉬폰")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "생산품목 상태", example = "AVAILABLE")
            @RequestParam(required = false) ProductStatus status
    ) {
        List<ProductDto.Response> response = productService.productSearch(keyword, status);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "생산품목 상태 변경",
            description = "생산품목의 생산가능/생산중단 상태를 변경한다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<ProductDto.Response> updateStatus(
            @Parameter(description = "생산품목 ID", example = "1")
            @PathVariable Long id,
            @RequestBody ProductDto.UpdateStatusRequest request
    ) {
        ProductDto.Response response = productService.updateStatus(id, request);
        return ResponseEntity.ok(response);
    }


    @Operation(
            summary = "생산품목 도면 업로드",
            description = "RequestPart files로 도면 파일을 하나 이상 받아 해당 생산품목에 업로드한다. "
                    + "같은 파트 이름으로 여러 파일을 보내면 한 번에 여러 장이 등록된다. "
                    + "기존 단일 업로드용 file 파트도 그대로 받을 수 있다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "업로드 성공")
    })
    @PostMapping(value = "/{id}/drawings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ProductDrawingDto.Response>> uploadDrawings(
            @Parameter(description = "생산품목 ID", example = "1")
            @PathVariable Long id,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        List<MultipartFile> targets = new ArrayList<>();
        if (files != null) {
            targets.addAll(files);
        }
        if (file != null) {
            targets.add(file);
        }
        List<ProductDrawingDto.Response> responses = productDrawingService.uploadAll(id, targets);
        return ResponseEntity
                .created(URI.create("/api/v1/products/" + id + "/drawings/" + responses.getFirst().id()))
                .body(responses);
    }

    @Operation(
            summary = "생산품목 현재 도면 조회",
            description = "생산품목의 현재 도면 파일 목록을 조회한다. 정보 수정 화면에서 기존 파일 목록을 그릴 때 사용한다. "
                    + "각 항목에 10분 유효 S3 미리보기 주소(previewUrl)를 포함한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/{id}/drawings/current")
    public ResponseEntity<List<ProductDrawingDto.CurrentResponse>> getCurrentDrawings(
            @Parameter(description = "생산품목 ID", example = "1")
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(productDrawingService.getCurrentFiles(id));
    }

    @Operation(
            summary = "생산품목 도면 삭제",
            description = "현재 도면 파일 하나를 삭제한다. 목록에서 빠지고 저장소 객체도 지운다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "품목 또는 도면 없음")
    })
    @DeleteMapping("/{id}/drawings/{drawingId}")
    public ResponseEntity<Void> deleteDrawing(
            @Parameter(description = "생산품목 ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "도면 ID", example = "10")
            @PathVariable Long drawingId
    ) {
        productDrawingService.delete(id, drawingId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "생산품목 정보 수정",
            description = "RequestBody로 생산품목 정보를 받아 수정한 뒤 상세 정보를 반환한다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ProductDto.Response> update(
            @Parameter(description = "생산품목 ID", example = "1")
            @PathVariable Long id,
            @RequestBody ProductDto.UpdateRequest request
    ) {
        ProductDto.Response response = productService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "모델코드 중복 검사",
            description = "모델코드 사용 가능 여부를 반환한다. available=true이면 등록 가능"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/model-code/exists")
    public ResponseEntity<ProductDto.ModelCodeExistsResponse> checkModelCodeExists(
            @Parameter(description = "모델코드", example = "IP16-CASE-001")
            @RequestParam String modelCode
    ) {
        ProductDto.ModelCodeExistsResponse response = productService.checkModelCodeExists(modelCode);
        return ResponseEntity.ok(response);
    }
}
