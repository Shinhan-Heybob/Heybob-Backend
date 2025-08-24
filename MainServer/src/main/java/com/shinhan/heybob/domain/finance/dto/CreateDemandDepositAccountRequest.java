package com.shinhan.heybob.domain.finance.dto;

public record CreateDemandDepositAccountRequest(
        FinanceHeader Header,
        String accountTypeUniqueNo
) {
}
