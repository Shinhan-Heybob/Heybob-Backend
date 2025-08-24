package com.shinhan.heybob.domain.financePersonal.dto;

import lombok.Getter;

@Getter
public class TransactionHistoryDateRequestDto {
    private String startDate; // 20250824 형태
    private String endDate;
}
