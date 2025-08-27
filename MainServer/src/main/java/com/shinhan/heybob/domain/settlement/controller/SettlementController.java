package com.shinhan.heybob.domain.settlement.controller;

import com.shinhan.heybob.common.user.UserPrincipalDetails;
import com.shinhan.heybob.domain.meal.service.ChatIntegrationService;
import com.shinhan.heybob.domain.settlement.dto.*;
import com.shinhan.heybob.domain.settlement.service.SettlementQueryService;
import com.shinhan.heybob.domain.settlement.service.SettlementService;
import com.shinhan.heybob.domain.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settle")
public class SettlementController {

    private final SettlementService settlementService;
    private final SettlementQueryService settlementQueryService;
    private final ChatIntegrationService chatIntegrationService;

    @PostMapping("/{chatRoomId}/create")
    public ResponseEntity<Map<String, Object>> createSettlement(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @RequestBody @Valid SettlementRequestDto requestDto,
            @PathVariable Long chatRoomId
    ) {
        return settlementService.createSettlement(
                userPrincipal.getUserId(), requestDto.participantsUserIds(), requestDto.totalAmount(), chatRoomId);
    }

    @PatchMapping("/{chatRoomId}/update")
    public ResponseEntity<Void> updateSettlement(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @RequestBody @Valid SettlementRequestDto requestDto,
            @PathVariable Long chatRoomId
    ) {
        settlementService.updateSettlement(
                userPrincipal.getUserId(), requestDto.participantsUserIds(), requestDto.totalAmount(), chatRoomId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
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

    @PostMapping("/{chatRoomId}/broadcast/start")
    public ResponseEntity<Map<String, Object>> sendSettlementStart(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @PathVariable Long chatRoomId,
            @RequestBody @Valid SettlementStartBroadcastRequestDto requestDto
    ) {
        try {
            String messageId = chatIntegrationService.sendSettlementBroadcast(
                    requestDto.settlementId(),
                    chatRoomId,
                    userPrincipal.getUserId(),
                    requestDto.perHead()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "messageId", messageId,
                    "chatRoomId", chatRoomId,
                    "settlementId", requestDto.settlementId(),
                    "message", "정산 시작 브로드캐스트가 전송되었습니다."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}
