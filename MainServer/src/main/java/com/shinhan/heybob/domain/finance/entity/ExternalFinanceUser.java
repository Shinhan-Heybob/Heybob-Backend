package com.shinhan.heybob.domain.finance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "external_finance_user",
    uniqueConstraints = {
            @UniqueConstraint(name = "uk_ext_user_id", columnNames = "user_id")
    })
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalFinanceUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private Long userRealId; // 실제 userId

    @Column(length = 40, nullable = false, unique = true)
    private String userId;  // 외부 시스템에 넘길 이메일 형식 ex) abcd1234@ssafy.com

    @Column(length = 128, nullable = false)
    private String userKey; // 금융API로부터 받은 userKey

    public void updateKey(String newKey) {
        this.userKey = newKey;
    }
}
