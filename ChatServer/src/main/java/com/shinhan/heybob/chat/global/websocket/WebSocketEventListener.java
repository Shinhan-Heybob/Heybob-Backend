package com.shinhan.heybob.chat.global.websocket;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    // 세션별 사용자 정보 저장
    private final Map<String, UserSessionInfo> sessionUserMap = new ConcurrentHashMap<>();
    // 방별 사용자 수 관리
    private final Map<String, Integer> roomUserCountMap = new ConcurrentHashMap<>();

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        log.info("WebSocket 연결: sessionId={}", sessionId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        log.info("WebSocket 연결 해제: sessionId={}", sessionId);
        
        // 세션 정보 조회
        UserSessionInfo userInfo = sessionUserMap.get(sessionId);
        if (userInfo != null) {
            // 퇴장 메시지 전송
            sendLeaveMessage(userInfo);
            
            // 세션 정보 정리
            sessionUserMap.remove(sessionId);
            decrementRoomUserCount(userInfo.getRoomId());
        }
    }

    @EventListener
    public void handleSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();
        
        // /topic/room/{roomId} 형태의 구독인지 확인
        if (destination != null && destination.startsWith("/topic/room/")) {
            String roomId = destination.substring("/topic/room/".length());
            
            // 헤더에서 사용자 정보 추출
            String userId = headerAccessor.getFirstNativeHeader("X-User-Id");
            String studentId = headerAccessor.getFirstNativeHeader("X-Student-Id");
            String userName = headerAccessor.getFirstNativeHeader("X-User-Name");
            String profileImageUrl = headerAccessor.getFirstNativeHeader("X-Profile-Image");
            
            // 개발용 기본값 처리
            if (userId == null || userName == null) {
                userId = "20000622";
                studentId = "20000622";
                userName = "개발테스트사용자";
                profileImageUrl = "https://example.com/default-profile.jpg";
            }
            
            // 사용자 세션 정보 저장
            UserSessionInfo userInfo = new UserSessionInfo(sessionId, roomId, userId, studentId, userName, profileImageUrl);
            sessionUserMap.put(sessionId, userInfo);
            
            // 방 사용자 수 증가
            incrementRoomUserCount(roomId);
            
            // 입장 메시지 전송
            sendJoinMessage(userInfo);
            
            log.info("방 구독: sessionId={}, roomId={}, userName={}", sessionId, roomId, userName);
        }
    }

    @EventListener
    public void handleUnsubscribeEvent(SessionUnsubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        // 구독 해제 시에는 퇴장 메시지를 보내지 않음 (연결 해제 시에만)
        log.info("방 구독 해제: sessionId={}", sessionId);
    }

    private void sendJoinMessage(UserSessionInfo userInfo) {
        try {
            ChatMessageRequest joinRequest = new ChatMessageRequest();
            joinRequest.setRoomId(userInfo.getRoomId());
            joinRequest.setContent(userInfo.getUserName() + "님이 입장했습니다.");
            joinRequest.setMessageType("JOIN");

            ChatMessageResponse response = chatService.processMessage(
                    userInfo.getRoomId(),
                    userInfo.getUserId(),
                    userInfo.getStudentId(),
                    userInfo.getUserName(),
                    userInfo.getProfileImageUrl(),
                    joinRequest
            );

            messagingTemplate.convertAndSend("/topic/room/" + userInfo.getRoomId(), response);
            log.info("입장 메시지 전송: roomId={}, userName={}", userInfo.getRoomId(), userInfo.getUserName());
            
        } catch (Exception e) {
            log.error("입장 메시지 전송 실패: roomId={}, userName={}", userInfo.getRoomId(), userInfo.getUserName(), e);
        }
    }

    private void sendLeaveMessage(UserSessionInfo userInfo) {
        try {
            ChatMessageRequest leaveRequest = new ChatMessageRequest();
            leaveRequest.setRoomId(userInfo.getRoomId());
            leaveRequest.setContent(userInfo.getUserName() + "님이 퇴장했습니다.");
            leaveRequest.setMessageType("LEAVE");

            ChatMessageResponse response = chatService.processMessage(
                    userInfo.getRoomId(),
                    userInfo.getUserId(),
                    userInfo.getStudentId(),
                    userInfo.getUserName(),
                    userInfo.getProfileImageUrl(),
                    leaveRequest
            );

            messagingTemplate.convertAndSend("/topic/room/" + userInfo.getRoomId(), response);
            log.info("퇴장 메시지 전송: roomId={}, userName={}", userInfo.getRoomId(), userInfo.getUserName());
            
        } catch (Exception e) {
            log.error("퇴장 메시지 전송 실패: roomId={}, userName={}", userInfo.getRoomId(), userInfo.getUserName(), e);
        }
    }

    private void incrementRoomUserCount(String roomId) {
        roomUserCountMap.merge(roomId, 1, Integer::sum);
        log.info("방 사용자 수 증가: roomId={}, count={}", roomId, roomUserCountMap.get(roomId));
    }

    private void decrementRoomUserCount(String roomId) {
        roomUserCountMap.merge(roomId, -1, Integer::sum);
        int count = roomUserCountMap.get(roomId);
        if (count <= 0) {
            roomUserCountMap.remove(roomId);
        }
        log.info("방 사용자 수 감소: roomId={}, count={}", roomId, Math.max(0, count));
    }

    public int getRoomUserCount(String roomId) {
        return roomUserCountMap.getOrDefault(roomId, 0);
    }

    // 내부 클래스: 사용자 세션 정보
    private static class UserSessionInfo {
        private final String sessionId;
        private final String roomId;
        private final String userId;
        private final String studentId;
        private final String userName;
        private final String profileImageUrl;

        public UserSessionInfo(String sessionId, String roomId, String userId, String studentId, 
                              String userName, String profileImageUrl) {
            this.sessionId = sessionId;
            this.roomId = roomId;
            this.userId = userId;
            this.studentId = studentId;
            this.userName = userName;
            this.profileImageUrl = profileImageUrl;
        }

        public String getSessionId() { return sessionId; }
        public String getRoomId() { return roomId; }
        public String getUserId() { return userId; }
        public String getStudentId() { return studentId; }
        public String getUserName() { return userName; }
        public String getProfileImageUrl() { return profileImageUrl; }
    }
}