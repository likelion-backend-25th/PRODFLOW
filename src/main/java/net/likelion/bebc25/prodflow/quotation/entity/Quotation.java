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

    // 견적 품목은 QuotationItem으로 따로 조회한다.
    public Quotation(
            String quotationNo,
            Client client,
            User user,
            LocalDate requestedDueDate,
            LocalDate estimatedDueDate,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate validUntil,
            boolean vatIncluded,
            QuotationStatus status
    ) {
        this.quotationNo = quotationNo;
        this.client = client;
        this.user = user;
        this.requestedDueDate = requestedDueDate;
        this.estimatedDueDate = estimatedDueDate;
        this.plannedStartDate = plannedStartDate;
        this.plannedEndDate = plannedEndDate;
        this.validUntil = validUntil;
        this.vatIncluded = vatIncluded;
        this.status = status;
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
            LocalDate plannedEndDate,
            LocalDate validUntil
    ) {
        this.requestedDueDate = requestedDueDate;
        this.estimatedDueDate = estimatedDueDate;
        this.plannedStartDate = plannedStartDate;
        this.plannedEndDate = plannedEndDate;
        this.validUntil = validUntil;
    }
}
