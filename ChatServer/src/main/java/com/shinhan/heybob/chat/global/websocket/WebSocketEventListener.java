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
        
        // 연결 시 헤더에서 사용자 정보 추출하여 세션 속성에 저장
        String userId = headerAccessor.getFirstNativeHeader("X-User-Id");
        String studentId = headerAccessor.getFirstNativeHeader("X-Student-Id");
        String userName = headerAccessor.getFirstNativeHeader("X-User-Name");
        String profileImageUrl = headerAccessor.getFirstNativeHeader("X-Profile-Image");
        
        if (userId != null && userName != null) {
            // 세션 속성에 사용자 정보 저장
            headerAccessor.getSessionAttributes().put("userId", userId);
            headerAccessor.getSessionAttributes().put("studentId", studentId);
            headerAccessor.getSessionAttributes().put("userName", userName);
            headerAccessor.getSessionAttributes().put("profileImageUrl", profileImageUrl);
            
            // 백업용 임시 저장도 유지
            UserSessionInfo tempInfo = new UserSessionInfo(sessionId, null, userId, studentId, userName, profileImageUrl);
            sessionUserMap.put(sessionId + "_TEMP", tempInfo);
            
            log.info("WebSocket 연결 (사용자 정보 저장): sessionId={}, userName={}", sessionId, userName);
        } else {
            log.info("WebSocket 연결 (헤더 없음): sessionId={}", sessionId);
        }
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
        
        // 임시 정보도 정리
        sessionUserMap.remove(sessionId + "_TEMP");
    }

    @EventListener
    public void handleSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();
        
        // /topic/room/{roomId} 형태의 구독인지 확인 (정확히 roomId만, settlement 등 하위 경로 제외)
        if (destination != null && destination.startsWith("/topic/room/") && !destination.substring("/topic/room/".length()).contains("/")) {
            String roomId = destination.substring("/topic/room/".length());
            
            log.debug("방 구독 시도: sessionId={}, roomId={}, destination={}", sessionId, roomId, destination);
            
            String userId = null, studentId = null, userName = null, profileImageUrl = null;
            
            // 1순위: 세션 속성에서 사용자 정보 가져오기
            if (headerAccessor.getSessionAttributes() != null) {
                userId = (String) headerAccessor.getSessionAttributes().get("userId");
                studentId = (String) headerAccessor.getSessionAttributes().get("studentId");
                userName = (String) headerAccessor.getSessionAttributes().get("userName");
                profileImageUrl = (String) headerAccessor.getSessionAttributes().get("profileImageUrl");
                
                if (userId != null && userName != null) {
                    log.info("세션 속성에서 사용자 정보 사용: sessionId={}, userName={}", sessionId, userName);
                } else {
                    log.warn("세션 속성에 사용자 정보 없음: sessionId={}", sessionId);
                    userId = null; // 다음 단계로 넘어가기 위해 null 설정
                }
            } else {
                userId = null;
            }
            
            // 2순위: 임시 저장소에서 사용자 정보 가져오기
            if (userId == null) {
                UserSessionInfo tempInfo = sessionUserMap.get(sessionId + "_TEMP");
                if (tempInfo != null) {
                    userId = tempInfo.getUserId();
                    studentId = tempInfo.getStudentId();
                    userName = tempInfo.getUserName();
                    profileImageUrl = tempInfo.getProfileImageUrl();
                    log.info("임시 저장소에서 사용자 정보 사용: sessionId={}, userName={}", sessionId, userName);
                } else {
                    log.warn("임시 저장소에도 사용자 정보 없음: sessionId={}", sessionId);
                }
            }
            
            // 3순위: 구독 헤더에서 직접 추출 시도 (fallback)
            if (userId == null) {
                userId = headerAccessor.getFirstNativeHeader("X-User-Id");
                studentId = headerAccessor.getFirstNativeHeader("X-Student-Id");
                userName = headerAccessor.getFirstNativeHeader("X-User-Name");
                profileImageUrl = headerAccessor.getFirstNativeHeader("X-Profile-Image");
                log.debug("구독 헤더에서 사용자 정보 추출 시도: userId={}, userName={}", userId, userName);
            }
            
            // 마지막: 개발용 기본값 처리
            if (userId == null || userName == null) {
                log.warn("모든 방법으로 사용자 정보를 찾을 수 없음, 개발용 기본값 사용: sessionId={}", sessionId);
                userId = "20000622";
                studentId = "20000622";
                userName = "개발테스트사용자";
                profileImageUrl = "https://example.com/default-profile.jpg";
            }
            
            // 임시 정보 정리 (사용 여부와 관계없이)
            sessionUserMap.remove(sessionId + "_TEMP");
            
            // 사용자 세션 정보 저장
            UserSessionInfo userInfo = new UserSessionInfo(sessionId, roomId, userId, studentId, userName, profileImageUrl);
            sessionUserMap.put(sessionId, userInfo);
            
            // 방 사용자 수 증가
            incrementRoomUserCount(roomId);
            
            // 입장 메시지 전송
            sendJoinMessage(userInfo);
            
            log.info("방 구독 완료: sessionId={}, roomId={}, userName={}", sessionId, roomId, userName);
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
            // 테스트용: JOIN 메시지 저장 비활성화, 브로드캐스트만 수행
            log.info("테스트 모드: 입장 알림 (저장 생략): roomId={}, userName={}", userInfo.getRoomId(), userInfo.getUserName());
            
            // 간단한 알림 메시지만 브로드캐스트
            ChatMessageResponse response = ChatMessageResponse.builder()
                    .messageId(java.util.UUID.randomUUID().toString())
                    .roomId(userInfo.getRoomId())
                    .senderId("SYSTEM")
                    .senderName("시스템")
                    .content(userInfo.getUserName() + "님이 입장했습니다.")
                    .messageType("JOIN")
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            messagingTemplate.convertAndSend("/topic/room/" + userInfo.getRoomId(), response);
            log.info("입장 메시지 브로드캐스트: roomId={}, userName={}", userInfo.getRoomId(), userInfo.getUserName());
            
        } catch (Exception e) {
            log.error("입장 메시지 전송 실패: roomId={}, userName={}", userInfo.getRoomId(), userInfo.getUserName(), e);
        }
    }

    private void sendLeaveMessage(UserSessionInfo userInfo) {
        try {
            // 테스트용: LEAVE 메시지 저장 비활성화, 브로드캐스트만 수행
            log.info("테스트 모드: 퇴장 알림 (저장 생략): roomId={}, userName={}", userInfo.getRoomId(), userInfo.getUserName());
            
            // 간단한 알림 메시지만 브로드캐스트
            ChatMessageResponse response = ChatMessageResponse.builder()
                    .messageId(java.util.UUID.randomUUID().toString())
                    .roomId(userInfo.getRoomId())
                    .senderId("SYSTEM")
                    .senderName("시스템")
                    .content(userInfo.getUserName() + "님이 퇴장했습니다.")
                    .messageType("LEAVE")
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            messagingTemplate.convertAndSend("/topic/room/" + userInfo.getRoomId(), response);
            log.info("퇴장 메시지 브로드캐스트: roomId={}, userName={}", userInfo.getRoomId(), userInfo.getUserName());
            
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