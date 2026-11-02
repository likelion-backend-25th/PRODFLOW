package net.likelion.bebc25.prodflow.product;

import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.product.dto.ProductDrawingDto.Response;
import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.product.entity.ProductDrawing;
import net.likelion.bebc25.prodflow.product.repository.ProductDrawingRepository;
import net.likelion.bebc25.prodflow.product.repository.ProductRepository;
import net.likelion.bebc25.prodflow.product.service.ProductDrawingService;
import net.likelion.bebc25.prodflow.product.storage.StorageClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductDrawingServiceTest {

    @Mock
    ProductRepository productRepository;

    @Mock
    ProductDrawingRepository productDrawingRepository;

    @Mock
    StorageClient storageClient;

    @InjectMocks
    ProductDrawingService productDrawingService;

    @Test
    @DisplayName("업로드 성공 시 S3 업로드 1회 호출하고 DB에 파일명을 저장한다")
    void uploadSuccess() {
        Long productId = 1L;
        CommonCode device = mock(CommonCode.class);
        Product product = Product.create("PF-CS-A100", "테스트", device, "설명", 10000L);
        ReflectionTestUtils.setField(product, "id", productId);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(productDrawingRepository.save(any(ProductDrawing.class))).willAnswer(invocation -> {
            ProductDrawing drawing = invocation.getArgument(0);
            ReflectionTestUtils.setField(drawing, "id", 10L);
            ReflectionTestUtils.setField(drawing, "createdAt", LocalDateTime.of(2026, 11, 2, 13, 0));
            return drawing;
        });

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "drawing.pdf",
                "application/pdf",
                "hello".getBytes()
        );

        Response response = productDrawingService.upload(productId, file);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.fileName()).isEqualTo("drawing.pdf");

        verify(storageClient).upload(anyString(), any(), eq("application/pdf"), eq(5L));

        ArgumentCaptor<ProductDrawing> captor = ArgumentCaptor.forClass(ProductDrawing.class);
        verify(productDrawingRepository).save(captor.capture());
        assertThat(captor.getValue().getFileName()).isEqualTo("drawing.pdf");
        assertThat(captor.getValue().getObjectKey()).startsWith("products/1/");
        assertThat(captor.getValue().getReplacedAt()).isNull();
    }
}
