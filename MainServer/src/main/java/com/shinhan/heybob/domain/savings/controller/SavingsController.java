package com.shinhan.heybob.domain.savings.controller;

import com.shinhan.heybob.common.user.UserPrincipalDetails;
import com.shinhan.heybob.domain.savings.dto.SavingsAccountCreateRequestDto;
import com.shinhan.heybob.domain.savings.service.SavingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/savings")
public class SavingsController {

    private final SavingsService savingsService;

    @PostMapping("/{mealId}/create")
    public ResponseEntity<Void> createSavingsAccount(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @PathVariable Long mealId,
            @RequestBody @Valid SavingsAccountCreateRequestDto requestDto
    ) {
        savingsService.createSavingsAccount(
                userPrincipal.getUserId(), mealId, requestDto.perHeadBalance(), requestDto.totalAmount());
        return ResponseEntity.ok().build();
    }
}
