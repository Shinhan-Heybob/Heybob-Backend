package com.shinhan.heybob.domain.financePersonal.dto;

public record InquireDemandDepositAccountBalanceRequest (
        FinanceHeader Header,
        String accountNo
) {}
