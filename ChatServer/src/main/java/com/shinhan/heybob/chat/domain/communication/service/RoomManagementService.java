package com.shinhan.heybob.chat.domain.communication.service;

import com.shinhan.heybob.chat.domain.communication.dto.ServerMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomManagementService {
    
    private final MainServerCommunicationService mainServerCommunicationService;
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * 채팅방 생성 (밥약 생성시 호출)
     */
    public void createChatRoom(String mealAppointmentId, String creatorUserId, String roomName, 
                              List<String> initialMembers) {
        try {
            log.info("📢 채팅방 생성 요청: mealAppointmentId={}, creator={}, members={}", 
                mealAppointmentId, creatorUserId, initialMembers.size());
            
            // Main 서버에 채팅방 생성 요청
            mainServerCommunicationService.createRoom(mealAppointmentId, creatorUserId, roomName, initialMembers);
            
        } catch (Exception e) {
            log.error("❌ 채팅방 생성 요청 실패: mealAppointmentId={}", mealAppointmentId, e);
            throw e;
        }
    }
    
    /**
     * 채팅방 입장 (사용자가 밥약 참여시 호출)
     */
    public CompletableFuture<Boolean> joinChatRoom(String roomId, String userId, 
                                                   String userName, String studentId) {
        try {
            log.info("🚪 채팅방 입장 요청: roomId={}, userId={}", roomId, userId);
            
            return mainServerCommunicationService.joinRoom(roomId, userId, userName, studentId)
                .thenApply(response -> {
                    Map<String, Object> payload = response.getPayload();
                    boolean success = Boolean.TRUE.equals(payload.get("success"));
                    
                    if (success) {
                        log.info("✅ 채팅방 입장 성공: roomId={}, userId={}", roomId, userId);
                        
                        // 클라이언트에게 입장 성공 알림
                        broadcastRoomJoined(roomId, userId, userName);
                    } else {
                        String errorMessage = (String) payload.get("errorMessage");
                        log.warn("⚠️ 채팅방 입장 실패: roomId={}, userId={}, reason={}", 
                            roomId, userId, errorMessage);
                    }
                    
                    return success;
                })
                .exceptionally(throwable -> {
                    log.error("❌ 채팅방 입장 요청 실패: roomId={}, userId={}", roomId, userId, throwable);
                    return false;
                });
                
        } catch (Exception e) {
            log.error("❌ 채팅방 입장 처리 중 오류: roomId={}, userId={}", roomId, userId, e);
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * 사용자 채팅방 접근 권한 확인
     */
    public CompletableFuture<Boolean> validateRoomAccess(String userId, String roomId) {
        try {
            log.debug("🔒 방 접근 권한 확인: userId={}, roomId={}", userId, roomId);
            
            return mainServerCommunicationService.validateUserAccess(userId, roomId)
                .exceptionally(throwable -> {
                    log.error("❌ 방 접근 권한 확인 실패, 기본값 false 반환: userId={}, roomId={}", 
                        userId, roomId, throwable);
                    return false;
                });
                
        } catch (Exception e) {
            log.error("❌ 방 접근 권한 확인 중 오류: userId={}, roomId={}", userId, roomId, e);
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * 채팅방 멤버 목록 조회 (캐시된 정보)
     */
    public CompletableFuture<List<Map<String, Object>>> getRoomMembers(String roomId) {
        try {
            log.debug("👥 채팅방 멤버 조회: roomId={}", roomId);
            
            return mainServerCommunicationService.getRoomMembers(roomId, "system")
                .exceptionally(throwable -> {
                    log.error("❌ 채팅방 멤버 조회 실패: roomId={}", roomId, throwable);
                    return List.of(); // 빈 목록 반환
                });
                
        } catch (Exception e) {
            log.error("❌ 채팅방 멤버 조회 중 오류: roomId={}", roomId, e);
            return CompletableFuture.completedFuture(List.of());
        }
    }
    
    private void broadcastRoomJoined(String roomId, String userId, String userName) {
        Map<String, Object> joinNotification = Map.of(
            "type", "USER_JOINED",
            "roomId", roomId,
            "userId", userId,
            "userName", userName,
            "timestamp", System.currentTimeMillis()
        );
        
        // 해당 방의 다른 사용자들에게 브로드캐스트
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/events", joinNotification);
    }
}