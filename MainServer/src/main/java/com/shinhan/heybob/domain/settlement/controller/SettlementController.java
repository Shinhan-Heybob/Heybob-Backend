package com.shinhan.heybob.domain.settlement.controller;

import com.shinhan.heybob.common.user.UserPrincipalDetails;
import com.shinhan.heybob.domain.settlement.dto.CreateSettlementRequestDto;
import com.shinhan.heybob.domain.settlement.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settlement")
public class SettlementController {

    private final TransactionService transactionService;

    @PostMapping("/create")
    public ResponseEntity<Void> createSettlement(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @RequestBody CreateSettlementRequestDto requestDto
    ) {
        transactionService.createSettlement(userPrincipal.getUserId(), requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
