package com.shinhan.heybob.domain.settlement.controller;

import com.shinhan.heybob.common.user.UserPrincipalDetails;
import com.shinhan.heybob.domain.settlement.dto.SettlementPageResponseDto;
import com.shinhan.heybob.domain.settlement.dto.SettlementRequestDto;
import com.shinhan.heybob.domain.settlement.dto.SettlementResponseDto;
import com.shinhan.heybob.domain.settlement.service.SettlementQueryService;
import com.shinhan.heybob.domain.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settle")
public class SettlementController {

    private final SettlementService settlementService;
    private final SettlementQueryService settlementQueryService;

    @PostMapping("/create/{chatRoomId}")
    public ResponseEntity<Void> createSettlement(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @RequestBody SettlementRequestDto requestDto,
            @PathVariable Long chatRoomId
    ) {
        settlementService.createSettlement(
                userPrincipal.getUserId(), requestDto.participantsUserIds(), requestDto.totalAmount(), chatRoomId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{chatRoomId}/update")
    public ResponseEntity<Void> updateSettlement(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @RequestBody SettlementRequestDto requestDto,
            @PathVariable Long chatRoomId
    ) {
        settlementService.updateSettlement(
                userPrincipal.getUserId(), requestDto.participantsUserIds(), requestDto.totalAmount(), chatRoomId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/{chatRoomId}/notify")
    public ResponseEntity<Void> notifySettlement(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @PathVariable Long chatRoomId
    ) {
        settlementService.notifySettlement(chatRoomId, userPrincipal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{chatRoomId}/info")
    public ResponseEntity<SettlementResponseDto> getSettlementInfo(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @PathVariable Long chatRoomId
    ) {
        return ResponseEntity.ok(settlementService.getSettlementInfo(userPrincipal.getUserId(), chatRoomId));
    }

    @PostMapping("/{chatRoomId}/pay")
    public ResponseEntity<Void> paySettlement(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @PathVariable Long chatRoomId
    ) {
        settlementService.paySettlement(userPrincipal.getUserId(), chatRoomId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 채팅 ID로 정산, 모임 조회
     * @param chatRoomId
     * @return
     */
    @GetMapping("/{chatRoomId}/page")
    public ResponseEntity<SettlementPageResponseDto> getPage(@PathVariable Long chatRoomId) {
        return ResponseEntity.ok(settlementQueryService.getSettlementPageByChatRoom(chatRoomId));
    }

}
