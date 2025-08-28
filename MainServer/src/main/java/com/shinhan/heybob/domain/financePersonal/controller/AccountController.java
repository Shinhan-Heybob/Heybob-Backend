package com.shinhan.heybob.domain.financePersonal.controller;

import com.shinhan.heybob.common.user.UserPrincipalDetails;
import com.shinhan.heybob.domain.financePersonal.dto.*;
import com.shinhan.heybob.domain.financePersonal.service.FinanceAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/personal/history")
    public ResponseEntity<TransactionHistoryListResponseDto> getTransactionHistory(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @RequestBody TransactionHistoryDateRequestDto dateRequestDto
            ) {
        TransactionHistoryListResponseDto dto = financeAccountService.getTransactionHistoryList(
                userPrincipal.getUserId(), dateRequestDto.getStartDate(), dateRequestDto.getEndDate()
        );
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/deposit")
    public ResponseEntity<Void> deposit(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @RequestBody DepositAmountRequestDto requestDto
    ) {
       financeAccountService.deposit(userPrincipal.getUserId(), requestDto.amount());
       return ResponseEntity.ok().build();
    }
}
