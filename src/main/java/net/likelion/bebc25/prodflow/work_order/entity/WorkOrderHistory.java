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
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.user.entity.User;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkOrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto_increment
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id")
    private WorkOrder workOrder;

    // 준비 작업 통합 후에도 어느 작업지시의 배정 이력인지 보존한다.
    private String workNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "previous_line_id", nullable = false)
    private Line previousLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_line_id", nullable = false)
    private Line newLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_user_id")
    private User previousUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_user_id")
    private User newUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    @Column(columnDefinition = "TEXT")
    private String changeReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime changedAt;

    public WorkOrderHistory(WorkOrder workOrder,
                            Line previousLine,
                            Line newLine,
                            User previousUser,
                            User newUser,
                            User changedBy,
                            String changeReason) {
        this.workOrder = workOrder;
        this.workNo = workOrder.getWorkNo();
        this.previousLine = previousLine;
        this.newLine = newLine;
        this.previousUser = previousUser;
        this.newUser = newUser;
        this.changedBy = changedBy;
        this.changeReason = changeReason;
    }

    // 통합되는 작업지시의 FK만 해제하고 변경 당시 라인·담당자·작업번호는 유지한다.
    public void archiveWorkOrder() {
        if (workOrder != null) {
            workNo = workOrder.getWorkNo();
            workOrder = null;
        }
    }
}
