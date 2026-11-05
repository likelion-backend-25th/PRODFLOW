package net.likelion.bebc25.prodflow.quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.common.entity.BaseTimeEntity;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.user.entity.User;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "quotation")
public class Quotation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quotation_no", nullable = false, unique = true)
    private String quotationNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "client_email", nullable = false)
    private String clientEmail;

    @Column(name = "client_phone", nullable = false)
    private String clientPhone;

    @Column(name = "client_address", nullable = false)
    private String clientAddress;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "requested_due_date", nullable = false)
    private LocalDate requestedDueDate;

    @Column(name = "estimated_due_date", nullable = false)
    private LocalDate estimatedDueDate;

    @Column(name = "planned_start_date", nullable = false)
    private LocalDate plannedStartDate;

    @Column(name = "planned_end_date", nullable = false)
    private LocalDate plannedEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuotationStatus status;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "vat_included", nullable = false)
    private boolean vatIncluded;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    // 업로드 완료 후에만 저장한다. NULL이면 미리보기를 허용하지 않는다.
    @Column(name = "pdf_object_key")
    private String pdfObjectKey;

    // 견적 정보 초기화
    public Quotation(
            String quotationNo,
            Client client,
            String clientName,
            User user,
            LocalDate requestedDueDate,
            LocalDate estimatedDueDate,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate validUntil,
            boolean vatIncluded,
            QuotationStatus status,
            Long totalAmount
    ) {
        if (totalAmount == null || totalAmount < 0) {
            throw new IllegalArgumentException("견적 총액은 0원 이상이어야 합니다.");
        }
        this.quotationNo = quotationNo;
        this.client = client;
        this.clientName = clientName;
        this.clientEmail = client.getEmail();
        this.clientPhone = client.getPhone();
        this.clientAddress = formatAddress(client.getAddress());
        this.user = user;
        this.requestedDueDate = requestedDueDate;
        this.estimatedDueDate = estimatedDueDate;
        this.plannedStartDate = plannedStartDate;
        this.plannedEndDate = plannedEndDate;
        this.validUntil = validUntil;
        this.vatIncluded = vatIncluded;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    // 견적 초안 변경
    public void changeDraft(Client client, String clientName, LocalDate requestedDueDate,
                            LocalDate estimatedDueDate, LocalDate plannedStartDate, LocalDate plannedEndDate,
                            LocalDate validUntil, boolean vatIncluded, Long totalAmount) {
        if (status != QuotationStatus.DRAFT) {
            throw new IllegalArgumentException("초안 상태의 견적만 수정할 수 있습니다.");
        }
        if (totalAmount == null || totalAmount < 0) {
            throw new IllegalArgumentException("견적 총액은 0원 이상이어야 합니다.");
        }
        this.client = client;
        this.clientName = clientName;
        this.clientEmail = client.getEmail();
        this.clientPhone = client.getPhone();
        this.clientAddress = formatAddress(client.getAddress());
        this.requestedDueDate = requestedDueDate;
        this.estimatedDueDate = estimatedDueDate;
        this.plannedStartDate = plannedStartDate;
        this.plannedEndDate = plannedEndDate;
        this.validUntil = validUntil;
        this.vatIncluded = vatIncluded;
        this.totalAmount = totalAmount;
        this.pdfObjectKey = null;
    }

    // PDF 저장 경로 초기화
    public void clearPdfObjectKey() {
        this.pdfObjectKey = null;
    }

    // PDF 저장 경로 변경
    public void changePdfObjectKey(String pdfObjectKey) {
        if (pdfObjectKey == null || pdfObjectKey.isBlank()) {
            throw new IllegalArgumentException("PDF 객체 키는 비어 있을 수 없습니다.");
        }
        this.pdfObjectKey = pdfObjectKey;
    }

    // 견적번호 변경
    public void changeQuotationNo(String quotationNo) {
        if (quotationNo == null || quotationNo.isBlank()) {
            throw new IllegalArgumentException("견적번호는 비어 있을 수 없습니다.");
        }
        this.quotationNo = quotationNo;
    }

    // 거래처 주소 조합
    private static String formatAddress(Address address) {
        if (address == null) {
            throw new IllegalArgumentException("거래처 주소가 필요합니다.");
        }
        return String.join(" ", address.getZipcode(), address.getRoadAddress(), address.getDetailAddress());
    }

    // 발송 완료 상태 변경
    public void sent() {
        this.status = QuotationStatus.SENT;
    }

    // 승인 상태 변경
    public void approve() {
        this.status = QuotationStatus.APPROVED;
    }

    // 거절 상태 변경
    public void reject() {
        if (status != QuotationStatus.SENT) {
            throw new IllegalArgumentException("발송완료 견적만 거절할 수 있습니다.");
        }
        this.status = QuotationStatus.REJECTED;
    }

    // 발송·승인 견적 취소
    public void cancel() {
        if (status != QuotationStatus.SENT && status != QuotationStatus.APPROVED) {
            throw new IllegalArgumentException("발송완료 또는 승인 견적만 취소할 수 있습니다.");
        }
        this.status = QuotationStatus.CANCELLED;
    }

    // 만료 상태 변경
    public void expired(LocalDate today) {
        if (status != QuotationStatus.SENT || !validUntil.isBefore(today)) {
            throw new IllegalArgumentException("유효기간이 지난 발송완료 견적만 만료할 수 있습니다.");
        }
        this.status = QuotationStatus.EXPIRED;
    }

    // 견적 납기 일정 변경
    public void changeSchedule(
            LocalDate requestedDueDate,
            LocalDate estimatedDueDate,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate
    ) {
        this.requestedDueDate = requestedDueDate;
        this.estimatedDueDate = estimatedDueDate;
        this.plannedStartDate = plannedStartDate;
        this.plannedEndDate = plannedEndDate;
    }
}
