package com.shinhan.heybob.domain.meal.service;

import com.shinhan.heybob.common.chat.dto.ChatBroadcastRequest;
import com.shinhan.heybob.common.chat.service.ChatMessageService;
import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.user.entity.User;
import com.shinhan.heybob.domain.user.repository.UserRepository;
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
    private final UserRepository userRepository;
    
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
            
            // 응답 대기 (타임아웃 5초)
            Long chatRoomId = chatRoomFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
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
    
    
    @Override
    public String sendSettlementBroadcast(Long settlementId, Long chatRoomId, Long requesterId, Integer requestAmount
    ) {
        try {
            User initiator = userRepository.findById(requesterId)
                    .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));

            String settlementIdStr = String.valueOf(settlementId);
            String chatRoomIdStr = String.valueOf(chatRoomId);
            String requesterName = initiator.getName();
            String requesterStudentId = initiator.getStudentId();
            String requesterProfileImg = initiator.getProfileUrl();

            ChatBroadcastRequest request = ChatBroadcastRequest.builder()
                .settlementId(settlementIdStr)
                .roomId(chatRoomIdStr)
                .requesterId(requesterId)  // 더미 ID
                .requesterName(requesterName)
                .requesterStudentId(requesterStudentId)  // 더미 학번
                .requesterProfileImg(requesterProfileImg) // 더미 프로필
                .requestAmount(requestAmount)
                .message("")
                .type(ChatBroadcastRequest.BroadcastType.PAYMENT)
                .build();
            
            // 공통 서비스를 통해 정산 브로드캐스트 전송
            String messageId = chatMessageService.sendSettlementBroadcast(request);
            
            log.info("✅ 정산 브로드캐스트 전송 완료: messageId={}, settlementId={}, roomId={}", 
                messageId, settlementId, chatRoomId);
            
            return messageId;
            
        } catch (Exception e) {
            log.error("❌ 정산 브로드캐스트 전송 실패: settlementId={}, roomId={}", settlementId, chatRoomId, e);
            throw new RuntimeException("정산 브로드캐스트 전송 실패: " + e.getMessage());
        }
    }

    @Override
    public String sendSettleRequestBroadcast(Long settlementId, Long chatRoomId, Long requesterId, String requesterName, String requesterStudentId, String requesterProfileImg, Integer requestAmount) {
        return "";
    }
}