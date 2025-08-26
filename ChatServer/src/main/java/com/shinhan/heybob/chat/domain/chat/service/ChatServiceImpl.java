package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.ChatHistoryResponse;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.dto.PaymentRequestData;
import com.shinhan.heybob.chat.domain.chat.dto.PaymentCompleteData;
import com.shinhan.heybob.chat.domain.chat.dto.UiState;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.domain.chat.repository.ChatRepository;
import com.shinhan.heybob.chat.global.error.ChatException;
import com.shinhan.heybob.chat.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    
    @Override
    public ChatMessageResponse processMessage(String roomId, String userId, String studentId, 
                                            String userName, String profileImageUrl, ChatMessageRequest request) {
        
        try {
            // 입력 검증
            if (roomId == null || roomId.trim().isEmpty()) {
                throw new ChatException(ErrorCode.ROOM_NOT_FOUND);
            }
            if (request == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
                throw new ChatException(ErrorCode.INVALID_REQUEST);
            }
            if (request.getMessageType() == null) {
                throw new ChatException(ErrorCode.INVALID_MESSAGE_TYPE);
            }
            
            // 정산 관련 메시지 처리
            ChatMessageResponse response = processSettlementMessage(roomId, userId, studentId, userName, profileImageUrl, request);
            
            // 메시지 중요도에 따른 처리 방식 분리
            if (isFinancialMessage(request.getMessageType())) {
                // 중요한 금융 알림은 Redis Stream → MongoDB (유실 방지)
                chatStreamService.saveToStream(response);
                log.info("금융 메시지 Redis Stream 저장: messageType={}, messageId={}", request.getMessageType(), response.getMessageId());
            } else {
                // 일반 채팅은 바로 MongoDB 저장 (빠른 처리)
                saveDirectlyToMongoDB(response);
                log.info("일반 메시지 MongoDB 직접 저장: messageType={}, messageId={}", request.getMessageType(), response.getMessageId());
            }
            
            return response;
            
        } catch (ChatException e) {
            log.error("채팅 메시지 처리 실패: roomId={}, error={}", roomId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("메시지 저장 중 예상치 못한 오류: roomId={}", roomId, e);
            throw new ChatException(ErrorCode.MESSAGE_SAVE_FAILED, e);
        }
    }
    
    private boolean isFinancialMessage(String messageType) {
        return List.of("PAYMENT_REQUEST", "PAYMENT_COMPLETE").contains(messageType);
    }
    
    private ChatMessageResponse processSettlementMessage(String roomId, String userId, String studentId,
                                                        String userName, String profileImageUrl, ChatMessageRequest request) {
        ChatMessageResponse.ChatMessageResponseBuilder responseBuilder = ChatMessageResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .roomId(roomId)
                .senderId(userId)
                .studentId(studentId)
                .senderName(userName)
                .profileImageUrl(profileImageUrl)
                .content(request.getContent())
                .messageType(request.getMessageType())
                .timestamp(LocalDateTime.now());
        
        // 결제 요청 메시지인 경우
        if ("PAYMENT_REQUEST".equals(request.getMessageType())) {
            PaymentRequestData paymentRequestData = createPaymentRequestData(roomId, userName, request);
            responseBuilder.paymentRequestData(paymentRequestData);
        }
        
        // 결제 완료 메시지인 경우 
        if ("PAYMENT_COMPLETE".equals(request.getMessageType()) && request.getPaymentCompleteData() != null) {
            // Main 서버에서 이미 구성된 데이터를 그대로 사용
            responseBuilder.paymentCompleteData(request.getPaymentCompleteData());
        }
        
        return responseBuilder.build();
    }
    
    private PaymentRequestData createPaymentRequestData(String roomId, String userName, ChatMessageRequest request) {
        String settlementId = UUID.randomUUID().toString();
        
        // 간단한 결제 요청 데이터 생성
        String requesterName = userName;
        Integer requestAmount = 12000;
        
        if (request.getPaymentRequestData() != null) {
            if (request.getPaymentRequestData().getRequesterName() != null) {
                requesterName = request.getPaymentRequestData().getRequesterName();
            }
            if (request.getPaymentRequestData().getRequestAmount() != null) {
                requestAmount = request.getPaymentRequestData().getRequestAmount();
            }
        }
        
        return PaymentRequestData.builder()
                .settlementId(settlementId)
                .roomId(roomId)
                .requesterName(requesterName)
                .requestAmount(requestAmount)
                .settlementUrl("/main/settlement/" + settlementId)
                .build();
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
}