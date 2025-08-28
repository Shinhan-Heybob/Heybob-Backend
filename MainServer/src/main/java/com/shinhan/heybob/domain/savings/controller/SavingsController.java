package com.shinhan.heybob.domain.savings.controller;

import com.shinhan.heybob.common.user.UserPrincipalDetails;
import com.shinhan.heybob.domain.savings.dto.SavingsAccountCreateRequestDto;
import com.shinhan.heybob.domain.savings.service.SavingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/savings")
public class SavingsController {

    private final SavingsService savingsService;

    @PostMapping("/{chatId}/create")
    public ResponseEntity<Void> createSavingsAccount(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @PathVariable Long chatId,
            @RequestBody @Valid SavingsAccountCreateRequestDto requestDto
    ) {
        savingsService.createSavingsAccount(
                userPrincipal.getUserId(), chatId, requestDto.perHeadBalance(), requestDto.totalAmount());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{chatId}/pay")
    public ResponseEntity<Void> paySavingsAccount(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @PathVariable Long chatId
    ) {
        savingsService.paySavingsAccount(userPrincipal.getUserId(), chatId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
