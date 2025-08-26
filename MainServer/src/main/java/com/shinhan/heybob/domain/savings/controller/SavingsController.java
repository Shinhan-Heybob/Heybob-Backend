package com.shinhan.heybob.domain.savings.controller;

import com.shinhan.heybob.common.user.UserPrincipalDetails;
import com.shinhan.heybob.domain.savings.service.SavingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/savings")
public class SavingsController {

    private final SavingsService savingsService;

    @PostMapping("/{mealId}/create")
    public ResponseEntity<Void> createSavingsAccount(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @PathVariable Long mealId
    ) {
        savingsService.createSavingsAccount(userPrincipal.getUserId(), mealId);
        return ResponseEntity.ok().build();
    }
}
