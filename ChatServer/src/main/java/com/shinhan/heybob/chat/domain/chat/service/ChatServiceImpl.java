package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.ChatHistoryResponse;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.domain.chat.repository.ChatRepository;
import com.shinhan.heybob.chat.global.error.ChatException;
import com.shinhan.heybob.chat.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {
    
    private final ChatStreamService chatStreamService;
    private final ChatRepository chatRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MessageDispatcher messageDispatcher;
    
    @Override
    public ChatMessageResponse processMessage(String roomId, String userId, String studentId, 
                                            String userName, String profileImageUrl, ChatMessageRequest request) {
        
        try {
            // 금융 메시지는 WebSocket으로 생성 불가 (메인 서버에서만 가능)
            if (messageDispatcher.isFinancialMessage(request.getMessageType())) {
                throw new ChatException(ErrorCode.FORBIDDEN, 
                        "금융 메시지는 WebSocket으로 생성할 수 없습니다. 메인 서버에서만 처리됩니다.");
            }
            
            // MessageDispatcher를 통한 일반 메시지 처리
            ChatMessageResponse response = messageDispatcher.dispatch(
                    roomId, userId, studentId, userName, profileImageUrl, request);
            
            // 일반 메시지는 MongoDB 직접 저장
            // (금융 메시지는 MessageHandler에서 처리하므로 여기서는 일반 메시지만)
            saveDirectlyToMongoDB(response);
            log.info("일반 메시지 MongoDB 직접 저장: messageType={}, messageId={}", 
                    request.getMessageType(), response.getMessageId());
            
            return response;
            
        } catch (ChatException e) {
            log.error("채팅 메시지 처리 실패: roomId={}, error={}", roomId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("메시지 저장 중 예상치 못한 오류: roomId={}", roomId, e);
            throw new ChatException(ErrorCode.MESSAGE_SAVE_FAILED, e);
        }
    }
    
    private void saveDirectlyToMongoDB(ChatMessageResponse response) {
        try {
            String messageId = response.getMessageId();
            if (messageId == null || messageId.trim().isEmpty()) {
                messageId = UUID.randomUUID().toString();
                log.warn("MessageId가 null이었습니다. 새로 생성: {}", messageId);
            }
            
            ChatMessage message = ChatMessage.builder()
                    .id(messageId)  // MongoDB _id (messageId 역할)
                    .roomId(response.getRoomId())
                    .senderId(response.getSenderId())
                    .studentId(response.getStudentId())
                    .senderName(response.getSenderName())
                    .profileImageUrl(response.getProfileImageUrl())
                    .content(response.getContent())
                    .messageType(ChatMessage.MessageType.valueOf(response.getMessageType()))
                    .timestamp(response.getTimestamp())
                    .paymentRequestData(response.getPaymentRequestData())
                    .paymentCompleteData(response.getPaymentCompleteData())
                    .emergencyFallback(false)
                    .build();
            
            log.info("MongoDB 저장 시도: messageId={}, content={}", messageId, response.getContent());
            chatRepository.save(message);
        } catch (Exception e) {
            log.error("MongoDB 저장 실패: messageId={}", response.getMessageId(), e);
            throw new ChatException(ErrorCode.MONGODB_CONNECTION_ERROR, e);
        }
    }
    
    @Override
    public List<ChatMessageResponse> getRecentMessages(String roomId, int limit) {
        try {
            if (roomId == null || roomId.trim().isEmpty()) {
                throw new ChatException(ErrorCode.ROOM_NOT_FOUND);
            }
            if (limit <= 0 || limit > 100) {
                throw new ChatException(ErrorCode.INVALID_REQUEST);
            }
            
            List<ChatMessage> messages = chatRepository.findRecentMessagesByRoomId(roomId, limit);
            return messages.stream()
                    .map(this::convertToResponse)
                    .toList();
        } catch (ChatException e) {
            throw e;
        } catch (Exception e) {
            log.error("최근 메시지 조회 실패: roomId={}", roomId, e);
            throw new ChatException(ErrorCode.MONGODB_QUERY_ERROR, e);
        }
    }
    
    @Override
    public List<ChatMessageResponse> getMessagesBefore(String roomId, String beforeMessageId, int limit) {
        try {
            if (roomId == null || roomId.trim().isEmpty()) {
                throw new ChatException(ErrorCode.ROOM_NOT_FOUND);
            }
            if (beforeMessageId == null || beforeMessageId.trim().isEmpty()) {
                throw new ChatException(ErrorCode.MESSAGE_NOT_FOUND);
            }
            if (limit <= 0 || limit > 100) {
                throw new ChatException(ErrorCode.INVALID_REQUEST);
            }
            
            List<ChatMessage> messages = chatRepository.findMessagesBeforeId(roomId, beforeMessageId, limit);
            return messages.stream()
                    .map(this::convertToResponse)
                    .toList();
        } catch (ChatException e) {
            throw e;
        } catch (Exception e) {
            log.error("이전 메시지 조회 실패: roomId={}, beforeMessageId={}", roomId, beforeMessageId, e);
            throw new ChatException(ErrorCode.MONGODB_QUERY_ERROR, e);
        }
    }
    
    private ChatMessageResponse convertToResponse(ChatMessage message) {
        ChatMessageResponse response = ChatMessageResponse.builder()
                .messageId(message.getId())  // _id를 messageId로 사용
                .roomId(message.getRoomId())
                .senderId(message.getSenderId())
                .studentId(message.getStudentId())
                .senderName(message.getSenderName())
                .profileImageUrl(message.getProfileImageUrl())
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .timestamp(message.getTimestamp())
                .paymentRequestData(message.getPaymentRequestData())
                .paymentCompleteData(message.getPaymentCompleteData())
                .build();
        
        return response;
    }
    
    @Override
    public ChatHistoryResponse getChatHistory(String roomId, String beforeMessageId, int limit) {
        try {
            if (roomId == null || roomId.trim().isEmpty()) {
                throw new ChatException(ErrorCode.ROOM_NOT_FOUND);
            }
            if (limit <= 0 || limit > 100) {
                throw new ChatException(ErrorCode.INVALID_REQUEST);
            }
            
            List<ChatMessage> messages;
            
            if (beforeMessageId != null) {
                // 특정 메시지 이전의 메시지들 조회 (스크롤 업)
                messages = chatRepository.findMessagesBeforeId(roomId, beforeMessageId, limit + 1); // +1로 hasMore 확인
            } else {
                // 최근 메시지들 조회 (초기 로딩)
                messages = chatRepository.findRecentMessagesByRoomId(roomId, limit + 1); // +1로 hasMore 확인
            }
            
            // hasMore 체크: 요청한 limit보다 많이 조회되었으면 더 있음
            boolean hasMore = messages.size() > limit;
            
            // limit 수만큼만 실제 반환
            if (hasMore) {
                messages = messages.subList(0, limit);
            }
            
            // 메시지 변환
            List<ChatMessageResponse> responseMessages = messages.stream()
                    .map(this::convertToResponse)
                    .toList();
            
            // 마지막 메시지 ID 추출
            String lastMessageId = null;
            if (!responseMessages.isEmpty()) {
                lastMessageId = responseMessages.get(responseMessages.size() - 1).getMessageId();
            }
            
            return ChatHistoryResponse.builder()
                    .messages(responseMessages)
                    .lastMessageId(lastMessageId)
                    .hasMore(hasMore)
                    .totalCount(responseMessages.size())
                    .build();
                    
        } catch (ChatException e) {
            throw e;
        } catch (Exception e) {
            log.error("채팅 히스토리 조회 실패: roomId={}, beforeMessageId={}", roomId, beforeMessageId, e);
            throw new ChatException(ErrorCode.MONGODB_QUERY_ERROR, e);
        }
    }
    
    @Override
    public void saveMessage(ChatMessage chatMessage) {
        try {
            log.info("💾 MongoDB 직접 저장 시도: messageId={}, content={}", 
                chatMessage.getId(), chatMessage.getContent());
            
            chatRepository.save(chatMessage);
            
            log.info("✅ MongoDB 직접 저장 완료: messageId={}", chatMessage.getId());
            
        } catch (Exception e) {
            log.error("❌ MongoDB 직접 저장 실패: messageId={}", chatMessage.getId(), e);
            throw new ChatException(ErrorCode.MONGODB_CONNECTION_ERROR, e);
        }
    }
    
    @Override
    public ChatMessageResponse processCafeteriaInfo(String roomId, String userId, String studentId, 
                                                   String userName, String profileImageUrl, String cafeteriaInfo) {
        try {
            log.info("🍚 학식 정보 처리 시작: roomId={}, userId={}", roomId, userId);
            
            // 파라미터로 받은 학식 정보 사용
            String cafeteriaData = cafeteriaInfo;
            
            String messageId = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();
            
            // 학식 정보 메시지 생성
            String content = formatCafeteriaMessage(cafeteriaData);
            
            ChatMessage chatMessage = ChatMessage.builder()
                .id(messageId)
                .roomId(roomId)
                .senderId(userId)
                .studentId(studentId)
                .senderName(userName)
                .profileImageUrl(profileImageUrl)
                .content(content)
                .messageType(ChatMessage.MessageType.CAFETERIA_INFO)
                .timestamp(now)
                .paymentRequestData(null)
                .paymentCompleteData(null)
                .emergencyFallback(false)
                .build();
            
            // MongoDB에 저장
            saveMessage(chatMessage);
            
            // 응답 생성
            ChatMessageResponse response = ChatMessageResponse.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(userId)
                .studentId(studentId)
                .senderName(userName)
                .profileImageUrl(profileImageUrl)
                .content(content)
                .messageType("CAFETERIA_INFO")
                .timestamp(now)
                .paymentRequestData(null)
                .paymentCompleteData(null)
                .build();
            
            log.info("✅ 학식 정보 처리 완료: messageId={}", messageId);
            return response;
            
        } catch (Exception e) {
            log.error("❌ 학식 정보 처리 실패: roomId={}, userId={}", roomId, userId, e);
            throw new ChatException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }
    
    
    private String formatCafeteriaMessage(String cafeteriaData) {
        if (cafeteriaData == null || cafeteriaData.contains("불러올 수 없습니다") || cafeteriaData.contains("오류가 발생")) {
            return "📋 " + cafeteriaData;
        }
        
        // CafeteriaService에서 이미 포맷된 데이터가 오므로 그대로 사용
        return cafeteriaData;
    }
}