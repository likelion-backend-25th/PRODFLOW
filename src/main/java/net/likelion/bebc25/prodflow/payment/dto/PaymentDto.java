package net.likelion.bebc25.prodflow.payment.dto;

import net.likelion.bebc25.prodflow.payment.entity.Payment;
import net.likelion.bebc25.prodflow.payment.entity.VirtualAccount;
import net.likelion.bebc25.prodflow.payment.enums.PaymentMethod;
import net.likelion.bebc25.prodflow.payment.enums.PaymentStatus;
import net.likelion.bebc25.prodflow.payment.enums.PaymentType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class PaymentDto {

    private PaymentDto() {
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

            // 입금내역
            PaymentHistoryDto.Response paymentHistory
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
            LocalDateTime createdAt
    ) {
    }

}