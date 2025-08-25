package com.shinhan.heybob.domain.settlement.controller;

import com.shinhan.heybob.common.user.UserPrincipalDetails;
import com.shinhan.heybob.domain.settlement.dto.SettlementRequestDto;
import com.shinhan.heybob.domain.settlement.dto.SettlementResponseDto;
import com.shinhan.heybob.domain.settlement.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settlement")
public class SettlementController {

    private final TransactionService transactionService;

    @PostMapping("/create/{chatRoomId}")
    public ResponseEntity<Void> createSettlement(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @RequestBody SettlementRequestDto requestDto,
            @PathVariable Long chatRoomId
    ) {
        transactionService.createSettlement(
                userPrincipal.getUserId(), requestDto.participantsUserIds(), requestDto.totalAmount(), chatRoomId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/update/{chatRoomId}")
    public ResponseEntity<Void> updateSettlement(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @RequestBody SettlementRequestDto requestDto,
            @PathVariable Long chatRoomId
    ) {
        transactionService.updateSettlement(
                userPrincipal.getUserId(), requestDto.participantsUserIds(), requestDto.totalAmount(), chatRoomId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/notify/{chatRoomId}")
    public ResponseEntity<Void> notifySettlement(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @PathVariable Long chatRoomId
    ) {
        transactionService.notifySettlement(chatRoomId, userPrincipal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{chatRoomId}")
    public ResponseEntity<SettlementResponseDto> getSettlementInfo(
            @PathVariable Long chatRoomId
    ) {
        return ResponseEntity.ok(transactionService.getSettlementInfo(chatRoomId));
    }
}
