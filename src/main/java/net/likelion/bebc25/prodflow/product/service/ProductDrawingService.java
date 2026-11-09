package net.likelion.bebc25.prodflow.product.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.storage.StorageClient;
import net.likelion.bebc25.prodflow.product.dto.ProductDrawingDto.Response;
import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.product.entity.ProductDrawing;
import net.likelion.bebc25.prodflow.product.repository.ProductDrawingRepository;
import net.likelion.bebc25.prodflow.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductDrawingService {

    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024; // 100MB

    private final ProductRepository productRepository;
    private final ProductDrawingRepository productDrawingRepository;
    private final StorageClient storageClient;


    @Transactional
    public Response upload(Long productId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("파일은 100MB 이하여야 합니다.");
        }
        Optional<Product> optionalProduct = productRepository.findById(productId);
        if (optionalProduct.isEmpty()) {
            throw new EntityNotFoundException("품목을 찾을 수 없습니다: id=" + productId);
        }
        Product product = optionalProduct.get();

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            fileName = "file";
        }
        String objectKey = "products/" + productId + "/" + UUID.randomUUID() + "_" + fileName;

        try (InputStream inputStream = file.getInputStream()) {
            storageClient.upload(
                    objectKey,
                    inputStream,
                    file.getContentType(),
                    file.getSize()
            );
        } catch (IOException e) {
            throw new IllegalStateException("파일 업로드에 실패했습니다.", e);
        }


        ProductDrawing drawing = ProductDrawing.create(product, objectKey, fileName);
        ProductDrawing saved = productDrawingRepository.save(drawing);
        return Response.from(saved);
    }

    public List<Response> getCurrentFiles(Long productId) {

        if (!productRepository.existsById(productId)) {
            throw new EntityNotFoundException("품목을 찾을 수 없습니다: id=" + productId);
        }
        return productDrawingRepository.findByProductIdAndReplacedAtIsNull(productId).stream()
                .map(Response::from)
                .toList();
    }


    public List<Response> getList(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new EntityNotFoundException("품목을 찾을 수 없습니다: id=" + productId);
        }
        return productDrawingRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(Response::from)
                .toList();
    }
}
