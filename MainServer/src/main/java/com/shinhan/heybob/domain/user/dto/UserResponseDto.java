package com.shinhan.heybob.domain.user.dto;

import com.shinhan.heybob.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
public class UserResponseDto {

    private Long id;
    private String name;
    private String studentId;
    private String profileUrl;
    private String university;
    private String department;

    @Builder
    public UserResponseDto(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.studentId = user.getStudentId();
        this.profileUrl = user.getProfileUrl();
        this.university = user.getUniversity();
        this.department = user.getDepartment();
    }

    public User toEntity() {
        return User.builder()
                .id(this.id)
                .name(this.name)
                .studentId(this.studentId)
                .profileUrl(this.profileUrl)
                .university(this.university)
                .department(this.department)
                .build();
    }
}
