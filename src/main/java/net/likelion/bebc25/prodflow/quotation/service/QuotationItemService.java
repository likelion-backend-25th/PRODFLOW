package net.likelion.bebc25.prodflow.quotation.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.product.entity.Product;
import net.likelion.bebc25.prodflow.product.enums.ProductStatus;
import net.likelion.bebc25.prodflow.product.repository.ProductRepository;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationItemDto;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.quotation.entity.QuotationItem;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class QuotationItemService {
    private final QuotationItemRepository quotationItemRepository;
    private final ProductRepository productRepository;

    // DTO 검증을 통과한 견적 품목의 중복 여부 검증
    public void validateItems(List<QuotationItemDto.CreateRequest> requests) {
        Set<Long> productIds = new HashSet<>();

        for (QuotationItemDto.CreateRequest item : requests) {
            // 이미 저장된 품목 ID는 add()가 false를 반환하므로 중복으로 판단
            if (!productIds.add(item.productId())) {
                throw new IllegalArgumentException("같은 품목을 중복으로 추가할 수 없습니다.");
            }
        }
    }

    // 견적에 포함할 품목을 생성하고 저장
    @Transactional
    public List<QuotationItem> create(Quotation quotation, List<QuotationItemDto.CreateRequest> requests) {
        List<QuotationItem> items = new ArrayList<>();
        for (QuotationItemDto.CreateRequest request : requests) {
            // 요청한 품목 ID로 생산품목 조회
            Product product = productRepository.findById(request.productId())
                    .orElseThrow(() -> new IllegalArgumentException("생산품목을 찾을 수 없습니다: " + request.productId()));

            // 생산 가능한 상태가 아니면 견적 품목 등록 불가
            if (product.getStatus() != ProductStatus.AVAILABLE) {
                throw new IllegalArgumentException("생산 중단된 품목은 견적에 추가할 수 없습니다.");
            }

            // 견적과 생산품목을 연결하고, 작성 당시 품목명·기종명·수량·단가 저장
            QuotationItem item = new QuotationItem(quotation, product,
                    product.getProductName(), product.getDevice().getName(), request.quantity(), request.unitPrice());

            items.add(item);
        }
        // 전체 견적 품목을 저장하고 저장된 목록 반환
        return quotationItemRepository.saveAll(items);
    }
}
