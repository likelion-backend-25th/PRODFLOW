package net.likelion.bebc25.prodflow.product.controller;


import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.product.dto.ProductDto;
import net.likelion.bebc25.prodflow.product.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ResponseEntity<List<ProductDto.Response>> getList() {
        List<ProductDto.Response> response = productService.getList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto.Response> getDetail(@PathVariable Long id) {
        ProductDto.Response response = productService.getDetail(id);
        return ResponseEntity.ok(response);
    }


}
