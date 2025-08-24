package com.shinhan.heybob.domain.financePersonal.dto;

import lombok.Builder;

@Builder
public record InquireTransactionHistoryListRequest(
        FinanceHeader Header,
        String accountNo,
        String startDate,
        String endDate,
        String transactionType, // M: 입금, D: 출금, A: 전체
        String orderByType // ASC: 오름차순, DESC: 내림차순
) {}
