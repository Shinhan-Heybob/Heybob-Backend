package com.shinhan.heybob.domain.finance.dto;

public record InquireDemandDepositAccountBalanceRequest (
        FinanceHeader header,
        String accountNo
) {}
