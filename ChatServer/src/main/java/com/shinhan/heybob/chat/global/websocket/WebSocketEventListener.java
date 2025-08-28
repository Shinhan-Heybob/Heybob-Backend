package com.shinhan.heybob.chat.global.websocket;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;
import org.springframework.web.socket.messaging.SessionConnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;
    private final RedisTemplate<String, Object> redisTemplate;

    // 세션별 사용자 정보 저장
    private final Map<String, UserSessionInfo> sessionUserMap = new ConcurrentHashMap<>();
    // 방별 사용자 수 관리
    private final Map<String, Integer> roomUserCountMap = new ConcurrentHashMap<>();
    // Redis를 통한 입장 메시지 추적을 위한 키 접두사
    private static final String ROOM_JOINED_USERS_KEY = "room:joined_users:";

    @EventListener
    public void handleStompConnectEvent(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        log.info("🚀 STOMP CONNECT 프레임 수신: sessionId={}", sessionId);
        log.debug("🔍 CONNECT 헤더들: {}", headerAccessor.toMap());
        
        // 커스텀 헤더 확인
        String userId = headerAccessor.getFirstNativeHeader("X-User-Id");
        String userName = headerAccessor.getFirstNativeHeader("X-User-Name");
        
        if (userId != null || userName != null) {
            log.info("✅ CONNECT에서 커스텀 헤더 발견: userId={}, userName={}", userId, userName);
        } else {
            log.info("ℹ️ CONNECT에 커스텀 헤더 없음 (정상)");
        }
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        log.info("🔌 WebSocket CONNECTED 이벤트 수신: sessionId={}", sessionId);
        log.debug("🔍 전체 헤더 정보: {}", headerAccessor.toMap());
        
        // 연결 시 헤더에서 사용자 정보 추출하여 세션 속성에 저장
        String userId = headerAccessor.getFirstNativeHeader("X-User-Id");
        String studentId = headerAccessor.getFirstNativeHeader("X-Student-Id");
        String userName = headerAccessor.getFirstNativeHeader("X-User-Name");
        String profileImageUrl = headerAccessor.getFirstNativeHeader("X-Profile-Image");
        
        log.debug("🔍 추출된 헤더: userId={}, studentId={}, userName={}, profileImage={}", 
            userId, studentId, userName, profileImageUrl);
        
        if (userId != null && userName != null) {
            // 세션 속성에 사용자 정보 저장
            headerAccessor.getSessionAttributes().put("userId", userId);
            headerAccessor.getSessionAttributes().put("studentId", studentId);
            headerAccessor.getSessionAttributes().put("userName", userName);
            headerAccessor.getSessionAttributes().put("profileImageUrl", profileImageUrl);
            
            // 백업용 임시 저장도 유지
            UserSessionInfo tempInfo = new UserSessionInfo(sessionId, null, userId, studentId, userName, profileImageUrl);
            sessionUserMap.put(sessionId + "_TEMP", tempInfo);
            
            log.info("✅ WebSocket 연결 성공 (사용자 정보 저장): sessionId={}, userName={}", sessionId, userName);
        } else {
            log.warn("⚠️ WebSocket 연결 (헤더 없음): sessionId={}", sessionId);
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
            
            // Redis에서 입장 기록 제거 (완전 퇴장 시)
            removeUserFromRoom(userInfo.getRoomId(), userInfo.getUserId());
            log.info("🚪 완전 퇴장: Redis 입장 기록 제거 - userId={}, roomId={}", userInfo.getUserId(), userInfo.getRoomId());
            
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
            
            // Redis 기반 중복 입장 메시지 방지
            if (!hasUserJoinedRoom(roomId, userId)) {
                // 처음 입장하는 경우에만 입장 메시지 전송
                sendJoinMessage(userInfo);
                addUserToRoom(roomId, userId);
                log.info("✅ 첫 입장: 입장 메시지 전송 - sessionId={}, userId={}, roomId={}", sessionId, userId, roomId);
            } else {
                log.info("🔄 재구독: 입장 메시지 생략 - sessionId={}, userId={}, roomId={}", sessionId, userId, roomId);
            }
            
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
            String messageId = java.util.UUID.randomUUID().toString();
            String content = userInfo.getUserName() + "님이 입장했습니다.";
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            
            log.info("입장 메시지 처리: roomId={}, userName={}", userInfo.getRoomId(), userInfo.getUserName());
            
            // ChatService를 통해 입장 메시지를 채팅 메시지로 처리
            com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest joinRequest = 
                new com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest();
            joinRequest.setContent(content);
            joinRequest.setMessageType("JOIN");
            
            // ChatService를 통해 처리하여 MongoDB에 저장
            com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse response = chatService.processMessage(
                userInfo.getRoomId(),
                "SYSTEM",
                "SYSTEM", 
                "시스템",
                null,
                joinRequest
            );
            
            // 브로드캐스트
            messagingTemplate.convertAndSend("/topic/room/" + userInfo.getRoomId(), response);
            log.info("✅ 입장 메시지 저장 및 브로드캐스트: roomId={}, userName={}, messageId={}", 
                userInfo.getRoomId(), userInfo.getUserName(), response.getMessageId());
            
        } catch (Exception e) {
            log.error("❌ 입장 메시지 처리 실패: roomId={}, userName={}", userInfo.getRoomId(), userInfo.getUserName(), e);
        }
    }

    private void sendLeaveMessage(UserSessionInfo userInfo) {
        try {
            String messageId = java.util.UUID.randomUUID().toString();
            String content = userInfo.getUserName() + "님이 퇴장했습니다.";
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            
            log.info("퇴장 메시지 처리: roomId={}, userName={}", userInfo.getRoomId(), userInfo.getUserName());
            
            // ChatService를 통해 퇴장 메시지를 채팅 메시지로 처리
            com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest leaveRequest = 
                new com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest();
            leaveRequest.setContent(content);
            leaveRequest.setMessageType("LEAVE");
            
            // ChatService를 통해 처리하여 MongoDB에 저장
            com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse response = chatService.processMessage(
                userInfo.getRoomId(),
                "SYSTEM",
                "SYSTEM", 
                "시스템",
                null,
                leaveRequest
            );
            
            // 브로드캐스트
            messagingTemplate.convertAndSend("/topic/room/" + userInfo.getRoomId(), response);
            log.info("✅ 퇴장 메시지 저장 및 브로드캐스트: roomId={}, userName={}, messageId={}", 
                userInfo.getRoomId(), userInfo.getUserName(), response.getMessageId());
            
        } catch (Exception e) {
            log.error("❌ 퇴장 메시지 처리 실패: roomId={}, userName={}", userInfo.getRoomId(), userInfo.getUserName(), e);
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

    /**
     * 사용자가 해당 방에 입장 메시지를 이미 보냈는지 Redis에서 확인
     */
    private boolean hasUserJoinedRoom(String roomId, String userId) {
        try {
            String key = ROOM_JOINED_USERS_KEY + roomId;
            return redisTemplate.opsForSet().isMember(key, userId);
        } catch (Exception e) {
            log.error("❌ Redis에서 입장 기록 확인 실패: roomId={}, userId={}", roomId, userId, e);
            return false; // 에러 시 중복으로 간주하지 않음 (안전한 방향)
        }
    }

    /**
     * 사용자를 방의 입장 목록에 추가
     */
    private void addUserToRoom(String roomId, String userId) {
        try {
            String key = ROOM_JOINED_USERS_KEY + roomId;
            redisTemplate.opsForSet().add(key, userId);
            // 24시간 TTL 설정 (방이 오래 유지되면 자동 정리)
            redisTemplate.expire(key, 24, java.util.concurrent.TimeUnit.HOURS);
            log.debug("✅ Redis에 입장 기록 추가: roomId={}, userId={}", roomId, userId);
        } catch (Exception e) {
            log.error("❌ Redis에 입장 기록 추가 실패: roomId={}, userId={}", roomId, userId, e);
        }
    }

    /**
     * 사용자를 방의 입장 목록에서 제거
     */
    private void removeUserFromRoom(String roomId, String userId) {
        try {
            String key = ROOM_JOINED_USERS_KEY + roomId;
            redisTemplate.opsForSet().remove(key, userId);
            log.debug("✅ Redis에서 입장 기록 제거: roomId={}, userId={}", roomId, userId);
        } catch (Exception e) {
            log.error("❌ Redis에서 입장 기록 제거 실패: roomId={}, userId={}", roomId, userId, e);
        }
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