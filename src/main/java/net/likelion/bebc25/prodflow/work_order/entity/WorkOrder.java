package net.likelion.bebc25.prodflow.work_order.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.likelion.bebc25.prodflow.common.entity.BaseTimeEntity;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.user.entity.User;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkOrder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto_increment
    private Long id;

    @Column(nullable = false, unique = true)
    private String workNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_product_id", nullable = false)
    private OrderProduct orderProduct;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id", nullable = false)
    private Line line;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 작업지시서 하나에 담당 품목은 1개
    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    @ColumnDefault("(CURRENT_DATE)")
    private LocalDate workOrderDate;

    @Column(nullable = false)
    private LocalDate plannedStartDate;

    @Column(nullable = false)
    private LocalDate plannedEndDate;

    private LocalDate completedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "common_code_id", nullable = false)
    private CommonCode commonCode;

    @Column(columnDefinition = "TEXT")
    private String note;

    public WorkOrder(String workNo,
                     OrderProduct orderProduct,
                     Line line,
                     User user,
                     int quantity,
                     LocalDate plannedStartDate,
                     LocalDate plannedEndDate,
                     String note) {
        this.workNo = workNo;
        this.orderProduct = orderProduct;
        this.line = line;
        this.user = user;
        this.quantity = quantity;
        this.workOrderDate = LocalDate.now();
        this.plannedStartDate = plannedStartDate;
        this.plannedEndDate = plannedEndDate;
        this.completedAt = null;
        this.note = note;
    }

    // 상태 변경
    public void changeStatus(CommonCode commonCode) {
        this.commonCode = commonCode;
        if ("PACK".equals(commonCode.getCode())) {
            this.completedAt = LocalDate.now();
        }
    }

    // 라인 변경
    public void changeLine(Line line) {
        this.line = line;
    }

    // 담당자 변경
    public void changeUser(User user) {
        this.user = user;
    }

    // 작업지시 수량 변경
    public void changeQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("작업지시 수량은 1 이상이어야 합니다.");
        }
        this.quantity = quantity;
    }

    // 작업지시 생산 예정 기간 변경
    public void changePlannedDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("생산 예정 기간이 올바르지 않습니다.");
        }
        this.plannedStartDate = startDate;
        this.plannedEndDate = endDate;
    }
}
