package com.shinhan.heybob.domain.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserLoginRequestDto {

    private String university;
    private String studentId;
    private String password;
}
