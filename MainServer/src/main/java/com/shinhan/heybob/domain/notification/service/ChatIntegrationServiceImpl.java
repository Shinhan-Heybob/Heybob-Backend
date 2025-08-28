package com.shinhan.heybob.domain.notification.service;

import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatIntegrationServiceImpl implements ChatIntegrationService {
    
    private final ChatMessageService chatMessageService;
    
    @Override
    public Long createChatRoom(MealAppointment mealAppointment) {
        try {
            // 참여자 ID 목록 생성
            List<String> participantIds = mealAppointment.getParticipants().stream()
                .map(participant -> participant.getUser().getId().toString())
                .collect(Collectors.toList());
            
            // 메타데이터 준비
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("mealAppointmentId", mealAppointment.getId().toString());
            
            // 공통 서비스를 통해 채팅방 생성
            CompletableFuture<Long> chatRoomFuture = chatMessageService.createChatRoom(
                mealAppointment.getName(),
                mealAppointment.getCreator().getId().toString(),
                participantIds,
                metadata
            );
            
            // 응답 대기 (타임아웃 1초)
            Long chatRoomId = chatRoomFuture.get(1, java.util.concurrent.TimeUnit.SECONDS);
            log.info("✅ 채팅방 생성 완료: 밥약ID={}, 채팅방ID={}", mealAppointment.getId(), chatRoomId);
            return chatRoomId;
            
        } catch (Exception e) {
            log.error("❌ 채팅방 생성 실패: 밥약ID={}", mealAppointment.getId(), e);
            // Fallback: 더미 채팅방 ID 반환
            Long fallbackRoomId = System.currentTimeMillis() % 1000000;
            log.warn("⚠️ Fallback 채팅방 ID 사용: {}", fallbackRoomId);
            return fallbackRoomId;
        }
    }

}