package net.likelion.bebc25.prodflow.product;

import net.likelion.bebc25.prodflow.product.dto.ProductDto;
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
    @DisplayName("품목 상세 및 목록 조회")
    void getProductDetailAndList() {
        Response detail = productService.getDetail(1L);

        assertThat(detail.modelCode()).isEqualTo("PF-CS-A100");
        assertThat(detail.productName()).isEqualTo("디자인 A 투명 케이스");
        assertThat(detail.deviceName()).isEqualTo("iPhone 16");
        assertThat(detail.currentPrice()).isEqualTo(10000L);
        assertThat(detail.status()).isEqualTo(ProductStatus.AVAILABLE);
        assertThat(detail.createdAt()).isNotNull();

        List<Response> list = productService.getList(null);
        assertThat(list).hasSize(5);
    }

    @Test
    @DisplayName("검색테스트")
    void searchProduct() {
        List<Response> all = productService.productSearch(null, null);
        assertThat(all).hasSize(5);

        List<Response> byName = productService.productSearch("디자인 A", null);
        assertThat(byName).hasSize(1);
        assertThat(byName.getFirst().modelCode()).isEqualTo("PF-CS-A100");
        List<Response> byKeyword = productService.productSearch("맥세이프", null);
        assertThat(byKeyword).hasSize(2);

    }

    @Test
    @DisplayName("품목 상태 변경")
    void updateProductStatus() {
        // 1. 시드 id=1 을 DISCONTINUED 로
        Response updated = productService.updateStatus(
                1L, new ProductDto.UpdateStatusRequest(ProductStatus.DISCONTINUED)
        );
        assertThat(updated.status()).isEqualTo(ProductStatus.DISCONTINUED);

        // 2. 상세 조회해도 유지되는지
        Response detail = productService.getDetail(1L);
        assertThat(detail.status()).isEqualTo(ProductStatus.DISCONTINUED);
    }
}
