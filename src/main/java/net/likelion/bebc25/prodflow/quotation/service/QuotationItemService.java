package net.likelion.bebc25.prodflow.quotation.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;
import net.likelion.bebc25.prodflow.product.repository.ProductRepository;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationItemDto;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.entity.QuotationItem;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class QuotationItemService {
    private final QuotationItemRepository quotationItemRepository;
    private final ProductRepository productRepository;

    // 중복·존재 여부·생산 가능 상태를 검증하고 품목을 ID별로 반환
    public Map<Long, Product> validateProducts(List<Long> productIds) {
        Set<Long> uniqueIds = new HashSet<>(productIds);

        if (uniqueIds.size() != productIds.size()) {
            throw new IllegalArgumentException("같은 품목을 중복으로 추가할 수 없습니다.");
        }

        List<Product> products = productRepository.findAllById(uniqueIds);
        if (products.size() != uniqueIds.size()) {
            throw new IllegalArgumentException("생산품목을 찾을 수 없습니다.");
        }

        Map<Long, Product> productsById = new HashMap<>();
        for (Product product : products) {
            if (product.getStatus() != ProductStatus.AVAILABLE) {
                throw new IllegalArgumentException("생산 중단된 품목은 견적에 추가할 수 없습니다.");
            }
            productsById.put(product.getId(), product);
        }

        return productsById;
    }

    // 기존 품목을 제거한 뒤 재등록한다. 실패하면 외부 수정 트랜잭션 전체가 롤백된다.
    @Transactional
    public List<QuotationItem> replaceDraftItems(Quotation quotation, List<QuotationItemDto.CreateRequest> requests) {
        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new IllegalArgumentException("초안 상태의 견적만 품목을 수정할 수 있습니다.");
        }
        validateProducts(requests.stream().map(QuotationItemDto.CreateRequest::productId).toList());
        quotationItemRepository.deleteAll(quotationItemRepository.findByQuotationIdOrderByIdAsc(quotation.getId()));
        // 같은 product_id를 재등록할 때 UNIQUE 제약에 걸리지 않도록 DELETE부터 반영한다.
        quotationItemRepository.flush();
        return create(quotation, requests);
    }

    // 검증한 생산품목으로 견적 품목을 생성하고 저장
    @Transactional
    public List<QuotationItem> create(Quotation quotation, List<QuotationItemDto.CreateRequest> requests) {
        List<Long> productIds = requests.stream()
                .map(QuotationItemDto.CreateRequest::productId)
                .toList();
        Map<Long, Product> productsById = validateProducts(productIds);

        List<QuotationItem> items = new ArrayList<>();
        for (QuotationItemDto.CreateRequest request : requests) {
            Product product = productsById.get(request.productId());

            // 견적과 생산품목을 연결하고, 작성 당시 품목명·기종명·수량·단가 저장
            QuotationItem item = new QuotationItem(quotation, product,
                    product.getProductName(), product.getDevice().getName(), request.quantity(), request.unitPrice());

            items.add(item);
        }
        // 전체 견적 품목을 저장하고 저장된 목록 반환
        return quotationItemRepository.saveAll(items);
    }
}
