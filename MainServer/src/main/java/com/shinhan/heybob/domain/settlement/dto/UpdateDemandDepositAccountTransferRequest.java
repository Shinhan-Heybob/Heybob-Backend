package com.shinhan.heybob.domain.settlement.dto;

import com.shinhan.heybob.domain.financePersonal.dto.FinanceHeader;

public record UpdateDemandDepositAccountTransferRequest(
        FinanceHeader Header,
        String depositAccountNo,
        String depositTransactionSummary,
        String transactionBalance,
        String withdrawalAccountNo,
        String withdrawalTransactionSummary
) {}
