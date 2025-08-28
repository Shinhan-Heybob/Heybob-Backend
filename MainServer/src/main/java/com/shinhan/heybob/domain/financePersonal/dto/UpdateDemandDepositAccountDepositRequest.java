package com.shinhan.heybob.domain.financePersonal.dto;

public record UpdateDemandDepositAccountDepositRequest(
        FinanceHeader Header,
        String accountNo,
        String transactionBalance,
        String transactionSummary
) {
}
