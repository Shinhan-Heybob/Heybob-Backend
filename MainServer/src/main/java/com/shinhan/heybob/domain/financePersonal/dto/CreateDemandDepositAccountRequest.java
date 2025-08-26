package com.shinhan.heybob.domain.financePersonal.dto;

public record CreateDemandDepositAccountRequest(
        FinanceHeader Header,
        String accountTypeUniqueNo
) {
}
