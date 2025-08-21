package com.shinhan.heybob.domain.user.dto;

import com.shinhan.heybob.domain.user.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequestDto {

    @NotBlank
    @Size(min = 2, max = 20)
    private String name;

    @NotBlank
    private String profileUrl;

    @NotBlank
    private String password;

    @NotBlank
    private String studentId;

    @NotBlank
    private String university;

    @NotBlank
    private String department;

    public User toEntity(UserCreateRequestDto userCreateRequestDto, String encryptedPassword) {
        return User.builder()
                .name(userCreateRequestDto.getName())
                .profileUrl(userCreateRequestDto.getProfileUrl())
                .password(encryptedPassword)
                .studentId(userCreateRequestDto.getStudentId())
                .university(userCreateRequestDto.getUniversity())
                .department(userCreateRequestDto.getDepartment())
                .build();
    }

}
