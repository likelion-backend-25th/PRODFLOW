package net.likelion.bebc25.prodflow.quotation.enums;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;

@RequiredArgsConstructor
public enum QuotationSortType {
    CREATED_AT("createdAt", Sort.Direction.DESC),
    REQUESTED_DUE_DATE("requestedDueDate", Sort.Direction.DESC),
    ESTIMATED_DUE_DATE("estimatedDueDate", Sort.Direction.DESC),
    VALID_UNTIL("validUntil", Sort.Direction.DESC);

    private final String property; // 정렬할 엔티티 필드명
    private final Sort.Direction direction; // 정렬 방향

    // 견적 정렬 조건 변환
    public Sort toSort() {
        return Sort.by(new Sort.Order(direction, property), Sort.Order.desc("id"));
    }
}
