package com.shinhan.heybob.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateProfileUrlRequestDto {

    private String newProfileUrl;
}
