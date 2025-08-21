package com.shinhan.heybob.domain.auth.entity;

import com.shinhan.heybob.common.entity.BaseTime;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "refresh_tokens")
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class RefreshToken extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "user_email", nullable = false, unique = true, updatable = false)
    private String email;

    @Column(name = "user_refresh_token", length = 500, nullable = false, unique = true)
    private String token;

    @Builder
    public RefreshToken(String email, String token) {
        this.email = email;
        this.token = token;
    }

    public void updateToken(String token) {
        this.token = token;
    }


}
