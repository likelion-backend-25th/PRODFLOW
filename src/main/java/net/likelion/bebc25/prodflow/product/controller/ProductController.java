package net.likelion.bebc25.prodflow.product.controller;


import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.product.dto.ProductDto;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;
import net.likelion.bebc25.prodflow.product.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductDto.Response> create(@RequestBody ProductDto.CreateRequest request) {
        ProductDto.Response response = productService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/products/" + response.id()))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProductDto.Response>> getList(
            @RequestParam(required = false) ProductStatus status
    ) {
        List<ProductDto.Response> response = productService.getList(status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto.Response> getDetail(@PathVariable Long id) {
        ProductDto.Response response = productService.getDetail(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductDto.Response>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus status
    ) {
        List<ProductDto.Response> response = productService.productSearch(keyword, status);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ProductDto.Response> updateStatus(
            @PathVariable Long id,
            @RequestBody ProductDto.UpdateStatusRequest request
    ) {
        ProductDto.Response response = productService.updateStatus(id, request);
        return ResponseEntity.ok(response);
    }
}
