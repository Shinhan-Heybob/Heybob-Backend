package com.shinhan.heybob.domain.financePersonal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionHistoryDto {
    private String transactionUniqueNo;
    private String transactionDate;
    private String transactionTime;
    private String transactionTypeName; // 입금, 출금, 입금(이체), 출금(이체)
    private String transactionBalance; // 거래 금액
    private String transactionAfterBalance; // 거래 후 잔고
    private String transactorName; // 거래자
    private String eventTitle; // 거래 내용
}
