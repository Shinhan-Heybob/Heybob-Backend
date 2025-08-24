package com.shinhan.heybob.domain.financePersonal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalAccountBalanceResponseDto {

    private String balance;

}
