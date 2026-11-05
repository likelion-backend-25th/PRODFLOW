package net.likelion.bebc25.prodflow.product.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.commoncode.service.CommonCodeService;
import net.likelion.bebc25.prodflow.product.dto.ProductDto;
import net.likelion.bebc25.prodflow.product.dto.ProductDto.Response;
import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;
import net.likelion.bebc25.prodflow.product.repository.ProductRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final CommonCodeService commonCodeService;

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ORDER_MANAGER')")
    public Response create(ProductDto.CreateRequest request) {

        //modelCode 중복이면 예외
        if (productRepository.existsByModelCode(request.modelCode())) {
            throw new IllegalArgumentException("이미 존재하는 모델코드입니다.");
        }

        CommonCode device = resolveDeviceForCreate(request.deviceCode(), request.deviceName());

        Product product = Product.create(
                request.modelCode(),
                request.productName(),
                device,
                request.productDescription(),
                request.currentPrice()
        );

        Product save = productRepository.save(product);

        return Response.from(save);
    }

    private CommonCode resolveDeviceForCreate(Long deviceCode, String deviceName) {
        boolean hasCode = deviceCode != null;
        boolean hasName = deviceName != null && !deviceName.isBlank();

        if (hasCode && hasName) {
            throw new IllegalArgumentException("기존 기종 ID 또는 새 기종명 중 하나만 입력해주세요.");
        }
        if (!hasCode && !hasName) {
            throw new IllegalArgumentException("기존 기종 ID 또는 새 기종명을 입력해주세요.");
        }
        if (hasCode) {
            return commonCodeRepository.findById(deviceCode)
                    .orElseThrow(() -> new EntityNotFoundException("기종 코드를 찾을 수 없습니다."));
        }
        return commonCodeService.resolveOrCreateDevice(deviceName);
    }

    //목록 조회
    public List<Response> getList(ProductStatus status) {
        List<Product> products;

        if (status == null) {
            products = productRepository.findAll();
        } else {
            products = productRepository.findByStatus(status);
        }
        List<Response> responses = new ArrayList<>();
        for (Product product : products) {
            responses.add(Response.from(product));
        }


        return responses;
    }

    //단건
    public Response getDetail(Long id) {
        // 1) id 로 품목 찾기 — 없으면 예외
        Optional<Product> optionalProduct = productRepository.findById(id);
        if (optionalProduct.isEmpty()) {
            throw new EntityNotFoundException("품목을 찾을 수 없습니다: id=" + id);
        }
        Product product = optionalProduct.get();

        // 2) Response 로 변환해서 return
        return Response.from(product);
    }


    public List<Response> productSearch(String keyword, ProductStatus status) {
        if (keyword == null || keyword.isBlank()) {
            return getList(status);
        }

        List<Product> products = productRepository.search(keyword, status);
        List<Response> responses = new ArrayList<>();
        for (Product product : products) {
            responses.add(Response.from(product));
        }
        return responses;
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ORDER_MANAGER')")
    public Response updateStatus(Long id, ProductDto.UpdateStatusRequest request) {
        Optional<Product> optionalProduct = productRepository.findById(id);
        if (optionalProduct.isEmpty()) {
            throw new EntityNotFoundException("품목을 찾을 수 없습니다.");
        }
        Product product = optionalProduct.get();

        product.changeStatus(request.status());
        return Response.from(product);


    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ORDER_MANAGER')")
    public Response update(Long id, ProductDto.UpdateRequest request) {
        Optional<Product> optionalProduct = productRepository.findById(id);
        if (optionalProduct.isEmpty()) {
            throw new EntityNotFoundException("품목을 찾을 수 없습니다: id=" + id);
        }
        Product product = optionalProduct.get();

        if (request.currentPrice() == null || request.currentPrice() <= 0) {
            throw new IllegalArgumentException("기준 단가는 0보다 커야 합니다.");
        }

        Optional<CommonCode> deviceExist = commonCodeRepository.findById(request.deviceCode());
        if (deviceExist.isEmpty()) {
            throw new EntityNotFoundException("기종 코드를 찾을 수 없습니다.");
        }
        CommonCode device = deviceExist.get();

        product.update(
                request.productName(),
                device,
                request.productDescription(),
                request.currentPrice()
        );

        return Response.from(product);
    }

    @Transactional(readOnly = true)
    public ProductDto.ModelCodeExistsResponse checkModelCodeExists(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            throw new IllegalArgumentException("모델코드는 필수입니다.");
        }
        String trimmed = modelCode.trim();
        boolean exists = productRepository.existsByModelCode(trimmed);
        return new ProductDto.ModelCodeExistsResponse(trimmed, !exists);
    }
}
