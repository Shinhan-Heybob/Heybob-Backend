package com.shinhan.heybob.chat.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

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
class BasicWebSocketTest {

    @LocalServerPort
    private int port;

    @Test
    void WebSocket_연결_테스트() throws Exception {
        // Given
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        
        String url = "ws://localhost:" + port + "/ws";
        
        // When
        StompSession session = stompClient.connectAsync(url, new TestStompSessionHandler())
                .get(10, TimeUnit.SECONDS);
        
        // Then
        assertNotNull(session);
        assertTrue(session.isConnected());
        
        // Cleanup
        session.disconnect();
    }

    @Test
    void 서버_포트_확인() {
        assertTrue(port > 0);
        System.out.println("테스트 서버 포트: " + port);
    }

    private static class TestStompSessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            System.out.println("기본 WebSocket 연결 성공: " + session.getSessionId());
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
            System.err.println("기본 WebSocket 테스트 예외: " + exception.getMessage());
        }
    }
}