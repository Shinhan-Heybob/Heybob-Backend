package com.shinhan.heybob.chat.util;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ChatStompTestClient {
    
    private final WebSocketStompClient stompClient;
    private StompSession stompSession;
    private final String serverUrl;
    
    // 사용자 정보 저장
    private String userId;
    private String studentId;
    private String userName;
    private String profileImageUrl;
    
    public ChatStompTestClient(int port) {
        this.serverUrl = "ws://localhost:" + port + "/ws";
        this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }
    
    public void connect() throws Exception {
        connect(null, null, null, null);
    }
    
    public void connect(String userId, String studentId, String userName, String profileImage) throws Exception {
        // 사용자 정보 저장
        this.userId = userId;
        this.studentId = studentId;
        this.userName = userName;
        this.profileImageUrl = profileImage;
        
        this.stompSession = stompClient.connectAsync(serverUrl, new TestStompSessionHandler())
                .get(10, TimeUnit.SECONDS);
    }
    
    public ChatMessageResponse sendMessageAndWaitForResponse(String roomId, String content) throws Exception {
        return sendMessageAndWaitForResponse(roomId, content, "TEXT");
    }
    
    public ChatMessageResponse sendMessageAndWaitForResponse(String roomId, String content, String messageType) throws Exception {
        CountDownLatch responseLatch = new CountDownLatch(1);
        AtomicReference<ChatMessageResponse> responseRef = new AtomicReference<>();
        
        // 구독
        StompSession.Subscription subscription = stompSession.subscribe(
            "/topic/room/" + roomId,
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return ChatMessageResponse.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    responseRef.set((ChatMessageResponse) payload);
                    responseLatch.countDown();
                }
            }
        );
        
        // 메시지 전송
        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent(content);
        request.setMessageType(messageType);
        
        // 헤더 설정
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app/chat/" + roomId);
        if (userId != null) headers.add("X-User-Id", userId);
        if (studentId != null) headers.add("X-Student-Id", studentId);
        if (userName != null) headers.add("X-User-Name", userName);
        if (profileImageUrl != null) headers.add("X-Profile-Image", profileImageUrl);
        
        stompSession.send(headers, request);
        
        // 응답 대기
        if (!responseLatch.await(10, TimeUnit.SECONDS)) {
            subscription.unsubscribe();
            throw new RuntimeException("메시지 응답을 10초 내에 받지 못했습니다");
        }
        
        subscription.unsubscribe();
        return responseRef.get();
    }
    
    public CompletableFuture<ChatMessageResponse> subscribeToRoom(String roomId) {
        CompletableFuture<ChatMessageResponse> future = new CompletableFuture<>();
        
        stompSession.subscribe(
            "/topic/room/" + roomId,
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return ChatMessageResponse.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    future.complete((ChatMessageResponse) payload);
                }
            }
        );
        
        return future;
    }

    // 구독 후 메시지 전송하는 통합 메서드
    public ChatMessageResponse subscribeAndSendMessage(String roomId, String content, String messageType) throws Exception {
        CompletableFuture<ChatMessageResponse> future = subscribeToRoom(roomId);
        
        // 구독이 완료될 시간을 충분히 대기
        Thread.sleep(2000);
        
        // 메시지 전송
        sendMessage(roomId, content, messageType);
        
        // 응답 대기
        return future.get(10, TimeUnit.SECONDS);
    }
    
    public void sendMessage(String roomId, String content) {
        sendMessage(roomId, content, "TEXT");
    }
    
    public void sendMessage(String roomId, String content, String messageType) {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent(content);
        request.setMessageType(messageType);
        
        // 헤더 설정
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app/chat/" + roomId);
        if (userId != null) headers.add("X-User-Id", userId);
        if (studentId != null) headers.add("X-Student-Id", studentId);
        if (userName != null) headers.add("X-User-Name", userName);
        if (profileImageUrl != null) headers.add("X-Profile-Image", profileImageUrl);
        
        stompSession.send(headers, request);
    }
    
    public void disconnect() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
        }
    }
    
    public boolean isConnected() {
        return stompSession != null && stompSession.isConnected();
    }
    
    private static class TestStompSessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            System.out.println("테스트 클라이언트 연결 성공: " + session.getSessionId());
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
            System.err.println("테스트 클라이언트 예외: " + exception.getMessage());
        }
    }
}