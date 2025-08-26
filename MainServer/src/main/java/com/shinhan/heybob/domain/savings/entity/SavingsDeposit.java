package com.shinhan.heybob.domain.savings.entity;

import com.shinhan.heybob.common.entity.BaseTime;
import com.shinhan.heybob.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "savings_deposit",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_deposit_unique",
                columnNames = {"savings_account_id","participant_user_id","cycle_no"}),
        indexes = {
                @Index(name = "idx_deposit_account_cycle", columnList = "savings_account_id,cycle_no"),
                @Index(name = "idx_deposit_status", columnList = "status")
        })
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsDeposit extends BaseTime {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 어떤 적금계좌에 대한 입금인지 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "savings_account_id", nullable = false)
    private SavingsAccount savingsAccount;

    /** 입금자(참가자) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_user_id", nullable = false)
    private User participantUser;

    /** 회차(1부터 시작 권장) */
    @Column(name = "cycle_no", nullable = false)
    private int cycleNo;

    /** 입금 금액(스냅샷) */
    @Column(name = "amount", nullable = false)
    private int amount;

    /** 멱등키(중복 클릭/재시도 방지) */
    @Column(name = "idempotency_key", length = 64, nullable = false)
    private String idempotencyKey;

    /** 외부 이체 식별자(있으면) */
    @Column(name = "external_tx_id", length = 100)
    private String externalTxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TransferStatus status = TransferStatus.PENDING; // PENDING/SUCCESS/FAILED/CANCELED

    public enum TransferStatus { PENDING, SUCCESS, FAILED, CANCELED }

    public void markSuccess(String txId) { this.status = TransferStatus.SUCCESS; this.externalTxId = txId; }
    public void markFailed() { this.status = TransferStatus.FAILED; }
    public void cancel() { this.status = TransferStatus.CANCELED; }
}
