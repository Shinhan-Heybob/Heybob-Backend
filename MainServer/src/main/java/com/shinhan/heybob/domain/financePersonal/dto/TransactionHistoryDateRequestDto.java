package com.shinhan.heybob.domain.financePersonal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionHistoryDateRequestDto {
    private String startDate; // 20250824 형태
    private String endDate;
}
