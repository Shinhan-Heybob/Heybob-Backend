package com.shinhan.heybob.domain.settlement.entity;

import com.shinhan.heybob.common.entity.BaseTime;
import com.shinhan.heybob.domain.settlement.model.TransferStatus;
import com.shinhan.heybob.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "settlement_participant",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_settlement_user", columnNames = {"settlement_id", "participant_user_id"})
        },
        indexes = {
                @Index(name = "idx_participant_user", columnList = "participant_user_id"),
                @Index(name = "idx_participant_status", columnList = "transfer_status")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementParticipant extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Settlement settlement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_user_id", nullable = false)
    private User participantUser;

    // 참가자별 청구 금액(그때의 스냅샷, 보통 per_head와 동일)
    @Column(name = "amount", nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 20)
    @Builder.Default
    private TransferStatus transferStatus = TransferStatus.PENDING;

    public void markSuccess() { this.transferStatus = TransferStatus.SUCCESS; }
    public void markFailed() { this.transferStatus = TransferStatus.FAILED; }
    public void cancel() { this.transferStatus = TransferStatus.CANCELED; }

    public boolean isSuccess() { return this.transferStatus == TransferStatus.SUCCESS; }
    public boolean isPending() { return this.transferStatus == TransferStatus.PENDING; }
}
