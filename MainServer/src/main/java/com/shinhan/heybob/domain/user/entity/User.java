package com.shinhan.heybob.domain.user.entity;

import com.shinhan.heybob.common.entity.BaseTime;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_name", nullable = false)
    private String name;

    @Column(name = "user_student_id", nullable = false, unique = true, updatable = false)
    private String studentId;

    @Column(name = "user_password", nullable = false)
    private String password;

    @Column(name = "user_profile_url", nullable = false)
    private String profileUrl;

    @Column(name = "user_university", nullable = false, updatable = false)
    private String university;

    @Column(name = "user_department", nullable = false, updatable = false)
    private String department;

    public void updateProfileUrl(String newProfileUrl) {
        this.profileUrl = newProfileUrl;
    }

}
