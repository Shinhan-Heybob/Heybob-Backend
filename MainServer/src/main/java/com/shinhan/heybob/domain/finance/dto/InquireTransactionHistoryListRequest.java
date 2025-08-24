package com.shinhan.heybob.domain.finance.dto;

import lombok.Builder;

@Builder
public record InquireTransactionHistoryListRequest(
        FinanceHeader header,
        String accountNo,
        String startDate,
        String endDate,
        String transactionType, // M: 입금, D: 출금, A: 전체
        String orderByType // ASC: 오름차순, DESC: 내림차순
) {}
