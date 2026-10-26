package net.likelion.bebc25.prodflow.workorder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.likelion.bebc25.prodflow.productionline.entity.Line;
import net.likelion.bebc25.prodflow.user.entity.User;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class WorkOrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto_increment
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "previous_line_id", nullable = false)
    private Line previousLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_line_id", nullable = false)
    private Line newLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "previous_user_id", nullable = false)
    private User previousUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_user_id", nullable = false)
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
        this.previousLine = previousLine;
        this.newLine = newLine;
        this.previousUser = previousUser;
        this.newUser = newUser;
        this.changedBy = changedBy;
        this.changeReason = changeReason;
    }
}
