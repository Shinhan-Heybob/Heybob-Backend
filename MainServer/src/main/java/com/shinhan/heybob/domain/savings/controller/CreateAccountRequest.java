package com.shinhan.heybob.domain.savings.controller;

import com.shinhan.heybob.domain.financePersonal.dto.FinanceHeader;

public record CreateAccountRequest(
        FinanceHeader Header,
        String withdrawalAccountNo,
        String accountTypeUniqueNo,
        String depositBalance
) {
}
