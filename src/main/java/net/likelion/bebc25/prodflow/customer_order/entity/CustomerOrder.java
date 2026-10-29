package net.likelion.bebc25.prodflow.customer_order.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.entity.BaseTimeEntity;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.enums.ConfirmationStatus;
import net.likelion.bebc25.prodflow.customer_order.enums.NotifyChannel;
import net.likelion.bebc25.prodflow.customer_order.enums.OrderPaymentStatus;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.user.entity.User;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOrder extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String address;

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

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "deposit_due_date", nullable = false)
    private LocalDate depositDueDate;

    @Column(name = "shipping_address", nullable = false)
    private String shippingAddress;

    @Column(name = "receiver_name", nullable = false)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false)
    private String receiverPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "notify_channel", nullable = false, length = 10)
    private NotifyChannel notifyChannel;

    @Column(length = 255)
    private String memo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_code", nullable = false)
    private CommonCode statusCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_status", nullable = false, length = 30)
    private ConfirmationStatus confirmationStatus;

    @Column(name = "vat_included", nullable = false)
    private boolean vatIncluded;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private OrderPaymentStatus paymentStatus;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    // 수주 품목은 OrderProduct로 따로 조회한다.
    public CustomerOrder(
            String orderNo,
            Quotation quotation,
            Client client,
            String contactName,
            String email,
            String phone,
            String address,
            User user,
            LocalDate requestedDueDate,
            LocalDate estimatedDueDate,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate depositDueDate,
            String shippingAddress,
            String receiverName,
            String receiverPhone,
            NotifyChannel notifyChannel,
            String memo,
            CommonCode statusCode,
            boolean vatIncluded,
            Long totalAmount
    ) {
        this.orderNo = orderNo;
        this.quotation = quotation;
        this.client = client;
        this.contactName = contactName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.user = user;
        this.requestedDueDate = requestedDueDate;
        this.estimatedDueDate = estimatedDueDate;
        this.plannedStartDate = plannedStartDate;
        this.plannedEndDate = plannedEndDate;
        this.depositDueDate = depositDueDate;
        this.shippingAddress = shippingAddress;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.notifyChannel = notifyChannel;
        this.memo = memo;
        this.statusCode = statusCode;
        this.vatIncluded = vatIncluded;
        this.totalAmount = totalAmount;
        this.confirmationStatus = ConfirmationStatus.PENDING;
        this.paymentStatus = OrderPaymentStatus.UNPAID;
        this.endDate = null;
    }

    public void changeSchedule(
            LocalDate estimatedDueDate,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate
    ) {
        this.estimatedDueDate = estimatedDueDate;
        this.plannedStartDate = plannedStartDate;
        this.plannedEndDate = plannedEndDate;
    }

    public void changeProgress(CommonCode statusCode) {
        this.statusCode = statusCode;
        this.confirmationStatus = switch (statusCode.getCode()) {
            case "WAITING" -> ConfirmationStatus.PENDING;
            case "CANCELLED" -> ConfirmationStatus.CANCELLED;
            default -> ConfirmationStatus.CONFIRMED;
        };
    }

    public void markUnpaid() {
        this.paymentStatus = OrderPaymentStatus.UNPAID;
    }

    public void markPartiallyPaid() {
        this.paymentStatus = OrderPaymentStatus.PARTIALLY_PAID;
    }

    public void markPaid() {
        this.paymentStatus = OrderPaymentStatus.PAID;
    }

    public void completeProduction(LocalDate endDate) {
        this.endDate = endDate;
    }

    public void changeShipping(
            String shippingAddress,
            String receiverName,
            String receiverPhone,
            NotifyChannel notifyChannel
    ) {
        this.shippingAddress = shippingAddress;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.notifyChannel = notifyChannel;
    }

    public void changeMemo(String memo) {
        this.memo = memo;
    }
}
