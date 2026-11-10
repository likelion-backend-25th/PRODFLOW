package net.likelion.bebc25.prodflow.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.PaymentHistory;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.PaymentMethod;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public final class PaymentDto {

    private PaymentDto() {
    }

    // 현금으로 받은 계약금 확인. 입금일시 생략 시 한국 시간의 처리 시각을 사용한다.
    public record ManualDepositRequest(
            @NotNull @Positive Long orderId,
            @NotNull @Positive Long amount,
            OffsetDateTime paidAt
    ) {
    }

    public record ManualDepositResponse(
            Long paymentId,
            Long orderId,
            long amount,
            PaymentMethod paymentMethod,
            OffsetDateTime paidAt,
            String orderStatus,
            ConfirmationStatus confirmationStatus,
            OrderPaymentStatus paymentStatus,
            List<Long> workOrderIds
    ) {
        public static ManualDepositResponse from(Payment payment, PaymentHistory history,
                                                 CustomerOrder order, List<Long> workOrderIds) {
            return new ManualDepositResponse(payment.getId(), order.getId(), payment.getAmount(),
                    payment.getPaymentMethod(), history.getPaidAt(), order.getStatusCode().getCode(),
                    order.getConfirmationStatus(), order.getPaymentStatus(), workOrderIds);
        }
    }

    // 수주 상세의 입금 요약과 결제 목록
    public record OrderResponse(
            Long orderId,
            Long totalAmount,
            long depositAmount,
            long paidAmount,
            long balanceAmount,
            List<OrderPaymentResponse> payments
    ) {
        public static OrderResponse from(
                CustomerOrder order,
                long depositAmount,
                long paidAmount,

                List<OrderPaymentResponse> payments
        ) {
            return new OrderResponse(
                    order.getId(),
                    order.getTotalAmount(),
                    depositAmount,
                    paidAmount,
                    Math.max(0, order.getTotalAmount() - paidAmount),
                    payments);
        }
    }

    // 결제별 입금 내역과 최신 발급 계좌. 아직 생성되지 않은 정보는 null이다.
    public record OrderPaymentResponse(
            Long paymentId,
            PaymentType paymentType,
            Long amount,
            PaymentStatus status,
            PaymentMethod paymentMethod,
            LocalDateTime createdAt,
            VirtualAccountDto.Response virtualAccount,
            PaymentHistoryDto.Response paymentHistory
    ) {
        public static OrderPaymentResponse from(Payment payment, VirtualAccount account, PaymentHistory history) {
            return new OrderPaymentResponse(
                    payment.getId(),
                    payment.getPaymentType(),
                    payment.getAmount(),
                    payment.getStatus(),
                    payment.getPaymentMethod(),
                    payment.getCreatedAt(),
                    account == null ? null : VirtualAccountDto.Response.from(account),
                    history == null ? null : PaymentHistoryDto.Response.from(history));
        }
    }

    // 수주 등록/출고 처리 시 내부적으로 발급한 결제건 + 가상계좌 결과
    // 별도의 프론트엔드 결제 생성 요청 Body는 필요하지 않음
    public record IssueResponse(
            Long paymentId,
            Long orderId,
            String orderNo,
            PaymentType paymentType,
            Long amount,
            PaymentStatus status,
            PaymentMethod paymentMethod,
            VirtualAccountDto.Response virtualAccount
    ) {
        // Service의 트랜잭션 안에서 변환한다 (LAZY 연관관계 접근)
        public static IssueResponse from(Payment payment, VirtualAccount account) {
            return new IssueResponse(
                    payment.getId(),
                    payment.getOrder().getId(),
                    payment.getOrder().getOrderNo(),
                    payment.getPaymentType(),
                    payment.getAmount(),
                    payment.getStatus(),
                    payment.getPaymentMethod(),
                    VirtualAccountDto.Response.from(account)
            );
        }
    }

    // 입금관리 상단 금액 통계
    public record SummaryResponse(

            // 전체 결제 예정 금액
            Long totalScheduledAmount,

            // 입금 대기 금액
            Long waitingAmount,

            // 입금 완료 금액
            Long completedAmount,

            // 미수 금액
            Long unpaidAmount
    ) {
    }

    // 입금관리 검색 조건
    public record SearchRequest(

            // 수주번호
            String orderNumber,

            // 거래처명
            String clientName,

            // 결제구분 (계약금 / 잔여대금)
            PaymentType paymentType,

            // 결제상태
            PaymentStatus paymentStatus,

            // 조회 시작일
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            // 조회 종료일
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate
    ) {
    }

    // 입금관리 목록의 한 행
    public record ListResponse(

            // 상세조회에 사용할 결제 ID
            Long paymentId,

            // 수주번호
            String orderNumber,

            // 거래처명
            String clientName,

            // 결제구분
            PaymentType paymentType,

            // 결제 예정 금액
            Long scheduledAmount,

            // 결제상태
            PaymentStatus paymentStatus,

            // 입금방식 (VA / MD)
            PaymentMethod paymentMethod,

            // 가상계좌 은행명
            String bankName,

            // 가상계좌번호
            String accountNumber,

            // 입금기한
            LocalDateTime expiresAt,

            // 입금완료일
            LocalDateTime receivedAt
    ) {
    }

    // 입금 상세내역 전체 응답
    public record DetailResponse(

            // 수주 정보
            OrderInfo order,

            // 결제건 정보
            PaymentInfo payment,

            // 가상계좌 정보
            VirtualAccountDto.Response virtualAccount,

            // 기존 입금내역 정보
            PaymentHistoryDto.Response paymentHistory,

            // 재발급 요청 버튼 활성화 여부 (실제 POST에서는 PortOne 상태 재검증)
            boolean reissueAvailable
    ) {
    }

    // 수주 정보
    public record OrderInfo(

            Long orderId,

            // 수주번호
            String orderNumber,

            // 거래처
            String clientName,

            // 수주 총액
            Long totalAmount,

            // 수주 상태
            String orderStatus
    ) {
    }

    //결제건 정보
    public record PaymentInfo(

            Long paymentId,

            // 결제구분 (계약금 / 잔여대금)
            PaymentType paymentType,

            // 결제 예정 금액
            Long scheduledAmount,

            // 결제상태
            PaymentStatus status,

            // 입금방식
            PaymentMethod paymentMethod,

            // 결제건 생성일
            LocalDateTime createdAt,

            // 현재 정책: 전액 입금되었다면 예정 금액, 그렇지 않으면 0원
            long actualPaidAmount,

            // 현재 가상계좌의 PortOne 결제 ID (수동 입금이면 null)
            String portonePaymentId
    ) {
    }

}
