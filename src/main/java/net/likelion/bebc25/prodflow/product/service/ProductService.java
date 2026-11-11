package net.likelion.bebc25.prodflow.product.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.common.dto.PageResponse;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.repository.CommonCodeRepository;
import net.likelion.bebc25.prodflow.commoncode.service.CommonCodeService;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.repository.OrderProductRepository;
import net.likelion.bebc25.prodflow.product.dto.ProductDto;
import net.likelion.bebc25.prodflow.product.dto.ProductDto.OrderHistoryResponse;
import net.likelion.bebc25.prodflow.product.dto.ProductDto.Response;
import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;
import net.likelion.bebc25.prodflow.product.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final CommonCodeService commonCodeService;
    private final OrderProductRepository orderProductRepository;

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
    public PageResponse<Response> getList(ProductStatus status, Pageable pageable) {
        Page<Product> products = status == null
                ? productRepository.findAllBy(pageable)
                : productRepository.findByStatus(status, pageable);

        return PageResponse.from(products.map(Response::from));
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


    public PageResponse<Response> productSearch(String keyword, ProductStatus status, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return getList(status, pageable);
        }

        Page<Product> products = productRepository.search(keyword.trim(), status, pageable);
        return PageResponse.from(products.map(Response::from));
    }

    @Transactional
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

    @Transactional
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

    public ProductDto.ModelCodeExistsResponse checkModelCodeExists(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            throw new IllegalArgumentException("모델코드는 필수입니다.");
        }
        String trimmed = modelCode.trim();
        boolean exists = productRepository.existsByModelCode(trimmed);
        return new ProductDto.ModelCodeExistsResponse(trimmed, !exists);
    }

    public List<OrderHistoryResponse> getOrderHistories(Long productId, int size) {
        if (!productRepository.existsById(productId)) {
            throw new EntityNotFoundException("품목을 찾을 수 없습니다: id=" + productId);
        }
        int limit = size <= 0 ? 3 : size;
        List<OrderProduct> orderProducts = orderProductRepository.findRecentByProductId(
                productId,
                PageRequest.of(0, limit)
        );

        List<OrderHistoryResponse> responses = new ArrayList<>();
        for (OrderProduct orderProduct : orderProducts) {
            CustomerOrder order = orderProduct.getCustomerOrder();
            responses.add(new OrderHistoryResponse(
                    order.getId(),
                    order.getOrderNo(),
                    order.getClient().getCompany(),
                    orderProduct.getProductName(),
                    orderProduct.getQuantity(),
                    orderProduct.getUnitPrice(),
                    orderProduct.lineAmount(),
                    order.getTotalAmount(),
                    order.getEstimatedDueDate(),
                    order.getStatusCode().getCode(),
                    order.getStatusCode().getName()
            ));
        }
        return responses;
    }
}
