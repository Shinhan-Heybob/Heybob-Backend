package com.shinhan.heybob.domain.financePersonal.entity;

import com.shinhan.heybob.common.entity.BaseTime;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "external_finance_user")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalFinanceUser extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_real_id", nullable = false, unique = true, updatable = false)
    private Long userRealId; // 실제 userId

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;  // 외부 시스템에 넘길 이메일 형식 ex) abcd1234@ssafy.com

    @Column(name = "user_key", nullable = false)
    private String userKey; // 금융API로부터 받은 userKey

}
