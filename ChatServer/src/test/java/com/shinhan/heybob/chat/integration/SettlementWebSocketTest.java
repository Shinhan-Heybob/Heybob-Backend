package com.shinhan.heybob.chat.integration;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379", 
        "spring.data.mongodb.host=localhost",
        "spring.data.mongodb.port=27017",
        "spring.data.mongodb.database=heybob_chat_test"
    }
)
@ActiveProfiles("test")
class SettlementWebSocketTest {

    @LocalServerPort
    private int port;

    @Test
    void 정산_요청_WebSocket_테스트() throws Exception {
        // Given
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        
        String url = "ws://localhost:" + port + "/ws";
        String roomId = "test-room-settlement";
        
        BlockingQueue<Object> receivedMessages = new LinkedBlockingDeque<>();
        
        // When
        StompSession session = stompClient.connectAsync(url, new TestStompSessionHandler())
                .get(5, TimeUnit.SECONDS);
        
        // 방 구독
        session.subscribe("/topic/room/" + roomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Object.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                System.out.println("일반 메시지 수신: " + payload);
                receivedMessages.offer(payload);
            }
        });
        
        // 정산 상태 업데이트 구독
        session.subscribe("/topic/room/" + roomId + "/settlement", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Object.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                System.out.println("정산 상태 업데이트 수신: " + payload);
                receivedMessages.offer(payload);
            }
        });

        Thread.sleep(500); // 구독 안정화 대기

        // 정산 요청 메시지 전송
        ChatMessageRequest settlementRequest = new ChatMessageRequest();
        settlementRequest.setRoomId(roomId);
        settlementRequest.setContent("치킨값 정산해요!");
        settlementRequest.setMessageType("PAYMENT_REQUEST");
        
        SettlementData settlementData = SettlementData.builder()
                .note("치킨 24,000원")
                .totalAmount(24000)
                .build();
        settlementRequest.setSettlementData(settlementData);

        session.send("/app/chat/" + roomId, settlementRequest);

        // Then - 메시지 수신 대기
        Object receivedMessage = receivedMessages.poll(5, TimeUnit.SECONDS);
        assertNotNull(receivedMessage, "정산 요청 메시지를 수신해야 합니다");
        System.out.println("수신된 메시지: " + receivedMessage);

        // Cleanup
        session.disconnect();
    }

    @Test
    void 정산_상호작용_WebSocket_테스트() throws Exception {
        // Given
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        
        String url = "ws://localhost:" + port + "/ws";
        String roomId = "test-room-interaction";
        
        BlockingQueue<Object> receivedMessages = new LinkedBlockingDeque<>();
        
        // When
        StompSession session = stompClient.connectAsync(url, new TestStompSessionHandler())
                .get(5, TimeUnit.SECONDS);
        
        // 정산 상태 업데이트 구독
        session.subscribe("/topic/room/" + roomId + "/settlement", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Object.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                System.out.println("정산 상태 업데이트 수신: " + payload);
                receivedMessages.offer(payload);
            }
        });

        Thread.sleep(500); // 구독 안정화 대기

        // 정산 승낙 메시지 전송
        ChatMessageRequest acceptRequest = new ChatMessageRequest();
        acceptRequest.setRoomId(roomId);
        acceptRequest.setContent("정산 승낙합니다");
        acceptRequest.setMessageType("SETTLEMENT_ACCEPT");
        acceptRequest.setSettlementId("dummy-settlement-id"); // 실제로는 존재하는 ID여야 함

        session.send("/app/chat/" + roomId, acceptRequest);

        // Then - 메시지 수신 대기 (실제 Redis가 없으면 예외가 발생할 수 있음)
        Thread.sleep(1000); // 처리 시간 대기

        // Cleanup
        session.disconnect();
    }

    private static class TestStompSessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            System.out.println("정산 WebSocket 연결 성공: " + session.getSessionId());
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
            System.err.println("정산 WebSocket 테스트 예외: " + exception.getMessage());
        }
    }
}