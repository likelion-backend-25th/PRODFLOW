package net.likelion.bebc25.prodflow.product;

import net.likelion.bebc25.prodflow.product.dto.ProductDto.CreateRequest;
import net.likelion.bebc25.prodflow.product.dto.ProductDto.Response;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;
import net.likelion.bebc25.prodflow.product.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ProductServiceTest {

    @Autowired
    ProductService productService;

    @Test
    @DisplayName("품목 등록 및 상세 조회")
    void createProductAndGetDetail() {
        // 1. 품목 등록 (deviceCode=1 → 시드 iPhone 16)
        CreateRequest request = new CreateRequest(
                "TEST-SVC-001",
                "서비스 테스트 품목",
                1L,
                "서비스 테스트 설명",
                15000L
        );
        Response created = productService.create(request);

        // 2. 상세 조회
        Response detail = productService.getDetail(created.id());

        assertThat(detail.modelCode()).isEqualTo("TEST-SVC-001");
        assertThat(detail.productName()).isEqualTo("서비스 테스트 품목");
        assertThat(detail.deviceName()).isEqualTo("iPhone 16");
        assertThat(detail.currentPrice()).isEqualTo(15000L);
        assertThat(detail.status()).isEqualTo(ProductStatus.AVAILABLE);
        assertThat(detail.createdAt()).isNotNull();

        // 3. 목록 조회 — data.sql 품목 5개 + 방금 등록 1개
        List<Response> list = productService.getList();
        assertThat(list).hasSize(6);
    }
}