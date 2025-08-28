package com.shinhan.heybob.common.chat.service;

import com.shinhan.heybob.common.chat.dto.ChatBroadcastRequest;
import com.shinhan.heybob.common.chat.dto.ServerMessage;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ChatMessageService {
    
    /**
     * Chat 서버로 메시지 전송
     * @param message 전송할 서버 메시지
     * @return 메시지 ID
     */
    String sendMessage(ServerMessage message);
    
    /**
     * Chat 서버로 메시지 전송 후 응답 대기
     * @param message 전송할 서버 메시지
     * @param timeoutMs 타임아웃 시간 (밀리초)
     * @return 응답 메시지
     */
    CompletableFuture<ServerMessage> sendMessageWithResponse(ServerMessage message, long timeoutMs);
    
    /**
     * 정산 브로드캐스트 전송
     * @param request 브로드캐스트 요청
     * @return 메시지 ID
     */
    String sendSettlementBroadcast(ChatBroadcastRequest request);
    
    /**
     * 적금 브로드캐스트 전송
     * @param request 브로드캐스트 요청
     * @return 메시지 ID
     */
    String sendSavingsBroadcast(ChatBroadcastRequest request);
    
    /**
     * 정산 완료 브로드캐스트 전송
     * @param request 브로드캐스트 요청
     * @return 메시지 ID
     */
    String sendPaymentCompleteBroadcast(ChatBroadcastRequest request);
    
    /**
     * 적금 완료 브로드캐스트 전송
     * @param request 브로드캐스트 요청
     * @return 메시지 ID
     */
    String sendSavingsCompleteBroadcast(ChatBroadcastRequest request);
    
    /**
     * 채팅방 생성 요청
     * @param roomName 채팅방 이름
     * @param creatorUserId 생성자 사용자 ID
     * @param initialMembers 초기 멤버 목록
     * @param metadata 추가 메타데이터
     * @return 생성된 채팅방 ID
     */
    CompletableFuture<Long> createChatRoom(String roomName, String creatorUserId, 
                                          java.util.List<String> initialMembers, 
                                          Map<String, Object> metadata);
}