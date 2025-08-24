package com.shinhan.heybob.domain.financePersonal.entity;

import com.shinhan.heybob.common.entity.BaseTime;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "personal_account",
    uniqueConstraints = @UniqueConstraint(name = "uk_personal_account_no", columnNames = "account_no"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalAccount extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "external_finance_user_id", updatable = false)
    private Long externalFinanceUserId;

    @NotBlank
    @Column(name = "account_no", updatable = false)
    private String accountNo;


}
