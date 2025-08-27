package com.shinhan.heybob.domain.savings.entity;

import com.shinhan.heybob.common.entity.BaseTime;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "savings_plan",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_plan_account", columnNames = "savings_account_id"),
        indexes = @Index(name = "idx_plan_next", columnList = "status,next_notify_at"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsPlan extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 플랜이 적용되는 적금 계좌 (1:1 가정) */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "savings_account_id", nullable = false)
    private SavingsAccount savingsAccount;

    /** 1회 알림 당 금액(원) */
    @Column(name = "per_head_balance", nullable = false)
    private int perHeadBalance;

    /** 총 회수(예: 12주) */
    @Column(name = "total_cycles", nullable = false)
    private int totalCycles;

    /** 지금까지 보낸 알림 회수 */
    @Column(name = "sent_cycles", nullable = false)
    @Builder.Default
    private int sentCycles = 0;

    /** 다음 알림 시각(KST) */
    @Column(name = "next_notify_at", nullable = false)
    private LocalDateTime nextNotifyAt;

    /** 요일/시간(옵션) — 다음 알림 계산용 */
    @Column(name = "notify_day_of_week", nullable = false)
    private int notifyDayOfWeek; // 1(Mon)~7(Sun), java.time.DayOfWeek#getValue

    @Column(name = "notify_time", nullable = false)
    private LocalTime notifyTime;

    /** 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PlanStatus status = PlanStatus.ACTIVE; // ACTIVE/PAUSED/COMPLETED/CANCELED

    /** 동시성 제어(낙관적 락) */
    @Version
    private Long version;

    // 진행 업데이트
    public void markSentAndScheduleNext() {
        this.sentCycles += 1;
        if (this.sentCycles >= this.totalCycles) {
            this.status = PlanStatus.COMPLETED;
        } else {
            this.nextNotifyAt = this.nextNotifyAt.plusWeeks(1);
        }
    }

    public enum PlanStatus { ACTIVE, PAUSED, COMPLETED, CANCELED }
}
