package net.likelion.bebc25.prodflow.quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.common.entity.BaseTimeEntity;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import net.likelion.bebc25.prodflow.user.entity.User;

import java.time.LocalDate;

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

    // 견적 품목은 QuotationItem으로 따로 조회한다.
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

    // 초안만 수정하며 견적 ID,번호,최초 작성자는 유지
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
    }

    public void changeQuotationNo(String quotationNo) {
        if (quotationNo == null || quotationNo.isBlank()) {
            throw new IllegalArgumentException("견적번호는 비어 있을 수 없습니다.");
        }
        this.quotationNo = quotationNo;
    }

    private static String formatAddress(Address address) {
        if (address == null) {
            throw new IllegalArgumentException("거래처 주소가 필요합니다.");
        }
        return String.join(" ", address.getZipcode(), address.getRoadAddress(), address.getDetailAddress());
    }

    public void sent() {
        this.status = QuotationStatus.SENT;
    }

    public void approve() {
        this.status = QuotationStatus.APPROVED;
    }

    public void reject() {
        this.status = QuotationStatus.REJECTED;
    }

    public void cancel() {
        this.status = QuotationStatus.CANCELLED;
    }

    public void expired() {
        this.status = QuotationStatus.EXPIRED;
    }

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
