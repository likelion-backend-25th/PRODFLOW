package net.likelion.bebc25.prodflow.payment.service;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.payment.dto.PaymentDto;
import net.likelion.bebc25.prodflow.payment.dto.PaymentHistoryDto;
import net.likelion.bebc25.prodflow.payment.dto.VirtualAccountDto;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.Bank;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.repository.PaymentHistoryRepository;
import net.likelion.bebc25.prodflow.payment.repository.PaymentRepository;
import net.likelion.bebc25.prodflow.payment.repository.VirtualAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {

    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");

    private final PaymentRepository paymentRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;

    // 입금관리 통계 조회
    public PaymentDto.SummaryResponse getSummary() {

        // 취소되지 않은 수주의 총액 (미발급 잔금 포함)
        long orderTotal =
                paymentRepository.sumValidOrderAmount();

        // 실제 입금 완료 금액
        long completedAmount =
                paymentRepository.sumAmountByStatus(PaymentStatus.PAID);

        // 가상계좌 발급 후 입금 대기 중인 금액
        long waitingAmount =
                paymentRepository.sumAmountByStatus(PaymentStatus.WAITING);

        // 전체 미입금 예정 금액
        long totalScheduledAmount =
                orderTotal - completedAmount;

        return new PaymentDto.SummaryResponse(
                totalScheduledAmount,
                waitingAmount,
                completedAmount,
                null // 미수 금액은 정책 확정 후 구현
        );
    }

    // 입금 목록 검색 및 페이징
    public Page<PaymentDto.ListResponse> getPayments(
            PaymentDto.SearchRequest condition,
            Pageable pageable
    ) {

        if (condition == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "검색 조건을 입력해 주세요."
            );
        }

        // 조회 기간 유효성 검사
        if (condition.startDate() != null
                && condition.endDate() != null
                && condition.startDate().isAfter(condition.endDate())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "조회 시작일은 종료일보다 늦을 수 없습니다."
            );
        }

        // 검색, 페이징 및 COUNT 처리
        Page<Payment> paymentPage = paymentRepository.findAll(
                searchSpec(condition),
                pageable
        );

        List<Long> paymentIds = paymentPage.getContent()
                .stream()
                .map(Payment::getId)
                .toList();

        if (paymentIds.isEmpty()) {
            return paymentPage.map(
                    payment -> toListResponse(payment, null, null)
            );
        }

        // 가상계좌 일괄 조회
        List<VirtualAccount> accounts =
                virtualAccountRepository
                        .findByPayment_IdInOrderByIdDesc(paymentIds);

        // 각 결제에서 가장 최근 가상계좌만 사용
        Map<Long, VirtualAccount> accountMap = new HashMap<>();

        for (VirtualAccount account : accounts) {
            accountMap.putIfAbsent(
                    account.getPayment().getId(),
                    account
            );
        }

        // 입금내역 일괄 조회
        List<PaymentHistory> histories =
                paymentHistoryRepository.findByPayment_IdIn(paymentIds);

        Map<Long, PaymentHistory> historyMap = new HashMap<>();

        for (PaymentHistory history : histories) {
            historyMap.put(
                    history.getPayment().getId(),
                    history
            );
        }

        // 페이지 정보는 그대로 유지하고 DTO만 변환
        return paymentPage.map(payment ->
                toListResponse(
                        payment,
                        accountMap.get(payment.getId()),
                        historyMap.get(payment.getId())
                )
        );
    }

    // 입금 상세 조회
    public PaymentDto.DetailResponse getPaymentDetail(Long paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "결제건을 찾을 수 없습니다."
                        )
                );

        CustomerOrder order = payment.getOrder();

        // 가장 최근 발급된 가상계좌 조회
        VirtualAccount account = virtualAccountRepository
                .findFirstByPayment_IdOrderByIdDesc(paymentId)
                .orElse(null);

        // 실제 입금 내역 조회
        PaymentHistory history = paymentHistoryRepository
                .findByPayment_Id(paymentId)
                .orElse(null);

        // 수주 정보
        PaymentDto.OrderInfo orderInfo = new PaymentDto.OrderInfo(
                order.getId(),
                order.getOrderNo(),
                order.getClient().getCompany(),
                order.getTotalAmount(),
                order.getStatusCode().getCode()
        );

        // 결제 정보
        PaymentDto.PaymentInfo paymentInfo = new PaymentDto.PaymentInfo(
                payment.getId(),
                payment.getPaymentType(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaymentMethod(),
                payment.getCreatedAt()
        );

        // 가상계좌 정보
        VirtualAccountDto.Response accountInfo =
                account == null
                        ? null
                        : VirtualAccountDto.Response.from(account);

        // 입금내역 정보
        PaymentHistoryDto.Response historyInfo =
                history == null
                        ? null
                        : PaymentHistoryDto.Response.from(history);

        return new PaymentDto.DetailResponse(
                orderInfo,
                paymentInfo,
                accountInfo,
                historyInfo
        );
    }

    // 목록 검색 조건
    private Specification<Payment> searchSpec(
            PaymentDto.SearchRequest condition
    ) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            // 수주번호 검색
            if (hasText(condition.orderNumber())) {
                predicates.add(
                        cb.like(
                                cb.lower(
                                        root.join("order", JoinType.INNER)
                                                .get("orderNo")
                                ),
                                keyword(condition.orderNumber())
                        )
                );
            }

            // 거래처명 검색
            if (hasText(condition.clientName())) {
                predicates.add(
                        cb.like(
                                cb.lower(
                                        root.join("order", JoinType.INNER)
                                                .join("client", JoinType.INNER)
                                                .get("company")
                                ),
                                keyword(condition.clientName())
                        )
                );
            }

            // 계약금 / 잔금 필터
            if (condition.paymentType() != null) {
                predicates.add(
                        cb.equal(
                                root.get("paymentType"),
                                condition.paymentType()
                        )
                );
            }

            // 결제 상태 필터
            if (condition.paymentStatus() != null) {
                predicates.add(
                        cb.equal(
                                root.get("status"),
                                condition.paymentStatus()
                        )
                );
            }

            // 조회 시작일 포함
            if (condition.startDate() != null) {

                LocalDateTime start =
                        condition.startDate().atStartOfDay();

                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("createdAt"),
                                start
                        )
                );
            }

            // 조회 종료일 당일까지 포함
            if (condition.endDate() != null) {

                LocalDateTime end =
                        condition.endDate()
                                .plusDays(1)
                                .atStartOfDay();

                predicates.add(
                        cb.lessThan(
                                root.get("createdAt"),
                                end
                        )
                );
            }

            return cb.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }

    // 목록 응답 DTO 변환
    private PaymentDto.ListResponse toListResponse(
            Payment payment,
            VirtualAccount account,
            PaymentHistory history
    ) {

        return new PaymentDto.ListResponse(
                payment.getId(),
                payment.getOrder().getOrderNo(),
                payment.getOrder().getClient().getCompany(),
                payment.getPaymentType(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaymentMethod(),

                // Bank enum을 화면 표시용 문자열로 변환
                account != null
                        ? resolveBankName(account.getBank())
                        : null,

                // 가상계좌 번호
                account != null
                        ? account.getAccountNumber()
                        : null,

                // 가상계좌 입금기한
                account != null
                        ? toKoreanLocalDateTime(account.getExpiresAt())
                        : null,

                // 실제 입금완료일
                history != null
                        ? toKoreanLocalDateTime(history.getPaidAt())
                        : null
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String keyword(String value) {
        return "%" +
                value.trim().toLowerCase(Locale.ROOT) +
                "%";
    }

    // Bank enum의 표시 문자열
    private String resolveBankName(Bank bank) {

        if (bank == null) {
            return null;
        }

        return bank.toString();
    }

    // OffsetDateTime -> 한국 시간 LocalDateTime
    // 기존 목록 DTO의 LocalDateTime 필드에 맞추기 위한 변환
    private LocalDateTime toKoreanLocalDateTime(
            OffsetDateTime dateTime
    ) {

        if (dateTime == null) {
            return null;
        }

        return dateTime
                .atZoneSameInstant(KOREA_ZONE)
                .toLocalDateTime();
    }
}
