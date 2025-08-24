package com.shinhan.heybob.domain.finance.controller;

import com.shinhan.heybob.common.user.UserPrincipalDetails;
import com.shinhan.heybob.domain.finance.dto.PersonalAccountBalanceResponseDto;
import com.shinhan.heybob.domain.finance.dto.PersonalAccountNoResponseDto;
import com.shinhan.heybob.domain.finance.service.FinanceAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/account")
public class AccountController {

    private final FinanceAccountService financeAccountService;

    @GetMapping("/personal/no")
    public ResponseEntity<PersonalAccountNoResponseDto> getPersonalAccountNo(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal
            ) {
        PersonalAccountNoResponseDto dto = financeAccountService.getPersonalAccountNo(userPrincipal.getUserId());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/personal/balance")
    public ResponseEntity<PersonalAccountBalanceResponseDto> getPersonalAccountBalance(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal
    ) {
        PersonalAccountBalanceResponseDto dto =
                financeAccountService.getPersonalAccountBalance(userPrincipal.getUserId());
        return ResponseEntity.ok(dto);
    }
}
