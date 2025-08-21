package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.ChatHistoryResponse;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import com.shinhan.heybob.chat.domain.chat.dto.UiState;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.domain.chat.repository.ChatRepository;
import com.shinhan.heybob.chat.global.error.ChatException;
import com.shinhan.heybob.chat.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

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
        return List.of("PAYMENT_REQUEST", "PAYMENT_CONFIRM", "PAYMENT_COMPLETE",
                      "SETTLEMENT_ACCEPT", "SETTLEMENT_REJECT", "SETTLEMENT_CANCEL", "SETTLEMENT_TIMEOUT")
                   .contains(messageType);
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
        
        // 정산 요청 메시지인 경우
        if ("PAYMENT_REQUEST".equals(request.getMessageType())) {
            SettlementData settlementData = createSettlementData(roomId, request);
            responseBuilder.settlementData(settlementData);
        } else if (request.getSettlementData() != null) {
            // 정산 응답 등 기존 정산 데이터가 있는 경우 복사
            responseBuilder.settlementData(request.getSettlementData());
        }
        
        ChatMessageResponse response = responseBuilder.build();
        
        // UI 상태 생성
        UiState uiState = createUiState(userId, request.getMessageType(), response.getSettlementData());
        response.setUiState(uiState);
        
        return response;
    }
    
    private SettlementData createSettlementData(String roomId, ChatMessageRequest request) {
        // Mock 데이터로 방 멤버 조회 (나중에 메인 서버 API 연동)
        List<String> mockRoomMembers = Arrays.asList("20000622", "20000623", "20000624");
        
        String settlementId = UUID.randomUUID().toString();
        Integer totalAmount = request.getSettlementData() != null ? request.getSettlementData().getTotalAmount() : 24000;
        Integer perPersonAmount = totalAmount / mockRoomMembers.size();
        
        // 초기 참가자 상태 설정
        Map<String, SettlementData.SettlementStatus> participantStatus = new HashMap<>();
        for (String memberId : mockRoomMembers) {
            participantStatus.put(memberId, SettlementData.SettlementStatus.builder()
                    .status("pending")
                    .build());
        }
        
        return SettlementData.builder()
                .settlementId(settlementId)
                .roomId(roomId)
                .note(request.getSettlementData() != null ? request.getSettlementData().getNote() : "정산 요청")
                .totalAmount(totalAmount)
                .perPersonAmount(perPersonAmount)
                .participants(mockRoomMembers)
                .expiryTime(LocalDateTime.now().plusMinutes(30))  // 30분 만료
                .participantStatus(participantStatus)
                .build();
    }
    
    private UiState createUiState(String userId, String messageType, SettlementData settlementData) {
        if (settlementData == null) {
            return null;
        }
        
        boolean isExpired = settlementData.getExpiryTime() != null && LocalDateTime.now().isAfter(settlementData.getExpiryTime());
        boolean isRequester = "PAYMENT_REQUEST".equals(messageType);
        
        SettlementData.SettlementStatus userStatus = null;
        String userResponseStatus = "pending";
        if (settlementData.getParticipantStatus() != null) {
            userStatus = settlementData.getParticipantStatus().get(userId);
            userResponseStatus = userStatus != null ? userStatus.getStatus() : "pending";
        }
        
        List<String> availableActions = new ArrayList<>();
        if (isExpired) {
            availableActions.add("view_details");
        } else if (isRequester) {
            availableActions.addAll(Arrays.asList("cancel", "view_details"));
        } else {
            if ("pending".equals(userResponseStatus)) {
                availableActions.addAll(Arrays.asList("accept", "reject", "view_details"));
            } else {
                availableActions.add("view_details");
            }
        }
        
        return UiState.builder()
                .isRequester(isRequester)
                .userResponseStatus(userResponseStatus)
                .availableActions(availableActions)
                .isExpired(isExpired)
                .build();
    }
    
    private void saveDirectlyToMongoDB(ChatMessageResponse response) {
        try {
            ChatMessage message = ChatMessage.builder()
                    .id(response.getMessageId())
                    .roomId(response.getRoomId())
                    .senderId(response.getSenderId())
                    .studentId(response.getStudentId())
                    .senderName(response.getSenderName())
                    .profileImageUrl(response.getProfileImageUrl())
                    .content(response.getContent())
                    .messageType(ChatMessage.MessageType.valueOf(response.getMessageType()))
                    .timestamp(response.getTimestamp())
                    .settlementData(response.getSettlementData())
                    .build();
            
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
                .messageId(message.getId())
                .roomId(message.getRoomId())
                .senderId(message.getSenderId())
                .studentId(message.getStudentId())
                .senderName(message.getSenderName())
                .profileImageUrl(message.getProfileImageUrl())
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .timestamp(message.getTimestamp())
                .settlementData(message.getSettlementData())
                .build();
        
        // UI 상태 재생성 (조회 시점 기준)
        if (message.getSettlementData() != null) {
            UiState uiState = createUiState(message.getSenderId(), message.getMessageType().name(), message.getSettlementData());
            response.setUiState(uiState);
        }
        
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
}