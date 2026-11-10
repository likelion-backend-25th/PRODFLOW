package net.likelion.bebc25.prodflow.product;

import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.product.dto.ProductDto;
import net.likelion.bebc25.prodflow.product.dto.ProductDto.Response;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;
import net.likelion.bebc25.prodflow.product.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ProductServiceTest {

    @Autowired
    ProductService productService;

    @BeforeEach
    void authenticateAsOrderManager() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "test-user",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ORDER_MANAGER"))
                )
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("품목 상세 및 목록 조회")
    void getProductDetailAndList() {
        Response detail = productService.getDetail(1L);

        assertThat(detail.modelCode()).isEqualTo("PF-CS-A100");
        assertThat(detail.productName()).isEqualTo("아이폰 16 맥세이프 클리어 케이스");
        assertThat(detail.deviceName()).isEqualTo("iPhone 16");
        assertThat(detail.currentPrice()).isEqualTo(10000L);
        assertThat(detail.status()).isEqualTo(ProductStatus.AVAILABLE);
        assertThat(detail.createdAt()).isNotNull();

        PageResponse<Response> list = productService.getList(null, PageRequest.of(0, 10));
        assertThat(list.getContent()).hasSize(5);
        assertThat(list.getTotalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("검색테스트")
    void searchProduct() {
        PageResponse<Response> all = productService.productSearch(null, null, PageRequest.of(0, 10));
        assertThat(all.getContent()).hasSize(5);

        PageResponse<Response> byName = productService.productSearch("클리어", null, PageRequest.of(0, 10));
        assertThat(byName.getContent()).hasSize(1);
        assertThat(byName.getContent().getFirst().modelCode()).isEqualTo("PF-CS-A100");
        PageResponse<Response> byKeyword = productService.productSearch("맥세이프", null, PageRequest.of(0, 10));
        assertThat(byKeyword.getContent()).hasSize(2);
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

    @Test
    @DisplayName("품목 정보 수정")
    void updateProduct() {
        Response updated = productService.update(
                1L,
                new ProductDto.UpdateRequest("수정된 케이스", 1L, "수정 설명", 15000L)
        );

        assertThat(updated.productName()).isEqualTo("수정된 케이스");
        assertThat(updated.productDescription()).isEqualTo("수정 설명");
        assertThat(updated.currentPrice()).isEqualTo(15000L);
        assertThat(updated.modelCode()).isEqualTo("PF-CS-A100"); // modelCode 는 안 바뀜

        Response detail = productService.getDetail(1L);
        assertThat(detail.productName()).isEqualTo("수정된 케이스");
        assertThat(detail.currentPrice()).isEqualTo(15000L);
    }

    @Test
    @DisplayName("모델코드 중복확인 — 없으면 available=true")
    void modelCodeAvailable_whenNotExists() {
        ProductDto.ModelCodeExistsResponse result =
                productService.checkModelCodeExists("NEW-CODE-9999");

        assertThat(result.available()).isTrue();
        assertThat(result.modelCode()).isEqualTo("NEW-CODE-9999");
    }

    @Test
    @DisplayName("모델코드 중복확인 — 있으면 available=false")
    void modelCodeNotAvailable_whenExists() {
        ProductDto.ModelCodeExistsResponse result =
                productService.checkModelCodeExists("PF-CS-A100");

        assertThat(result.available()).isFalse();
        assertThat(result.modelCode()).isEqualTo("PF-CS-A100");
    }

    @Test
    @DisplayName("새 기종명으로 품목 등록 — 기종 자동 생성")
    void createProduct_withNewDeviceName() {
        Response created = productService.create(
                new ProductDto.CreateRequest(
                        "PF-CS-NEW01",
                        "신규 테스트 케이스",
                        null,
                        "iPhone 17 Pro Max",
                        "새 기종 자동등록 테스트",
                        15000L
                )
        );

        assertThat(created.id()).isNotNull();
        assertThat(created.modelCode()).isEqualTo("PF-CS-NEW01");
        assertThat(created.productName()).isEqualTo("신규 테스트 케이스");
        assertThat(created.deviceName()).isEqualTo("iPhone 17 Pro Max");
        assertThat(created.currentPrice()).isEqualTo(15000L);
        assertThat(created.status()).isEqualTo(ProductStatus.AVAILABLE);
    }
}
