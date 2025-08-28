package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.dto.PaymentCompleteData;
import com.shinhan.heybob.chat.domain.chat.dto.PaymentRequestData;
import com.shinhan.heybob.chat.domain.chat.handler.MessageTypeHandler;
import com.shinhan.heybob.chat.domain.chat.handler.impl.*;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.global.error.ChatException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class MessageDispatcherTest {
    
    private MessageDispatcher messageDispatcher;
    
    @BeforeEach
    void setUp() {
        List<MessageTypeHandler> handlers = Arrays.asList(
            new ChatMessageHandler(),
            new PaymentRequestHandler(),
            new PaymentCompleteHandler(),
            new JoinMessageHandler(),
            new CafeteriaInfoHandler()
        );
        
        messageDispatcher = new MessageDispatcher(handlers);
        messageDispatcher.init();
    }
    
    @Test
    @DisplayName("일반 채팅 메시지 처리")
    void testChatMessage() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent("안녕하세요");
        request.setMessageType("CHAT");
        request.setRoomId("room1");
        
        ChatMessageResponse response = messageDispatcher.dispatch(
            "room1", "user1", "20201234", "홍길동", "profile.jpg", request
        );
        
        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("안녕하세요");
        assertThat(response.getMessageType()).isEqualTo("CHAT");
        assertThat(response.getSenderName()).isEqualTo("홍길동");
    }
    
    @Test
    @DisplayName("결제 요청 메시지 처리")
    void testPaymentRequestMessage() {
        PaymentRequestData paymentData = PaymentRequestData.builder()
            .requesterName("홍길동")
            .requestAmount(15000)
            .build();
            
        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent("홍길동님이 정산을 요청했습니다");
        request.setMessageType("PAYMENT_REQUEST");
        request.setRoomId("room1");
        request.setPaymentRequestData(paymentData);
        
        ChatMessageResponse response = messageDispatcher.dispatch(
            "room1", "user1", "20201234", "홍길동", "profile.jpg", request
        );
        
        assertThat(response).isNotNull();
        assertThat(response.getMessageType()).isEqualTo("PAYMENT_REQUEST");
        assertThat(response.getPaymentRequestData()).isNotNull();
        assertThat(response.getPaymentRequestData().getRequesterName()).isEqualTo("홍길동");
        assertThat(response.getPaymentRequestData().getRequestAmount()).isEqualTo(15000);
    }
    
    @Test
    @DisplayName("결제 완료 메시지 처리")
    void testPaymentCompleteMessage() {
        PaymentCompleteData completeData = PaymentCompleteData.builder()
            .settlementId("settle123")
            .roomId("room1")
            .recipientId("user1")
            .recipientName("김철수")
            .completedAmount(15000)
            .build();
            
        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent("정산이 완료되었습니다");
        request.setMessageType("PAYMENT_COMPLETE");
        request.setRoomId("room1");
        request.setPaymentCompleteData(completeData);
        
        ChatMessageResponse response = messageDispatcher.dispatch(
            "room1", "user2", "20201235", "김철수", "profile2.jpg", request
        );
        
        assertThat(response).isNotNull();
        assertThat(response.getMessageType()).isEqualTo("PAYMENT_COMPLETE");
        assertThat(response.getPaymentCompleteData()).isNotNull();
        assertThat(response.getPaymentCompleteData().getRecipientName()).isEqualTo("김철수");
    }
    
    @Test
    @DisplayName("입장 메시지 처리")
    void testJoinMessage() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent("");
        request.setMessageType("JOIN");
        request.setRoomId("room1");
        
        ChatMessageResponse response = messageDispatcher.dispatch(
            "room1", "user1", "20201234", "홍길동", "profile.jpg", request
        );
        
        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("홍길동님이 입장하셨습니다.");
        assertThat(response.getMessageType()).isEqualTo("JOIN");
    }
    
    @Test
    @DisplayName("지원하지 않는 메시지 타입")
    void testUnsupportedMessageType() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent("테스트");
        request.setMessageType("UNKNOWN_TYPE");
        request.setRoomId("room1");
        
        assertThatThrownBy(() -> 
            messageDispatcher.dispatch(
                "room1", "user1", "20201234", "홍길동", "profile.jpg", request
            )
        ).isInstanceOf(ChatException.class);
    }
    
    @Test
    @DisplayName("금융 메시지 타입 확인")
    void testIsFinancialMessage() {
        assertThat(messageDispatcher.isFinancialMessage("PAYMENT_REQUEST")).isTrue();
        assertThat(messageDispatcher.isFinancialMessage("PAYMENT_COMPLETE")).isTrue();
        assertThat(messageDispatcher.isFinancialMessage("CHAT")).isFalse();
        assertThat(messageDispatcher.isFinancialMessage("JOIN")).isFalse();
        assertThat(messageDispatcher.isFinancialMessage("INVALID")).isFalse();
    }
    
    @Test
    @DisplayName("빈 내용 검증")
    void testEmptyContentValidation() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent("");
        request.setMessageType("CHAT");
        request.setRoomId("room1");
        
        assertThatThrownBy(() -> 
            messageDispatcher.dispatch(
                "room1", "user1", "20201234", "홍길동", "profile.jpg", request
            )
        ).isInstanceOf(ChatException.class);
    }
    
    @Test
    @DisplayName("결제 완료 메시지에 데이터 없을 때 검증")
    void testPaymentCompleteWithoutData() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent("정산 완료");
        request.setMessageType("PAYMENT_COMPLETE");
        request.setRoomId("room1");
        // paymentCompleteData를 설정하지 않음
        
        assertThatThrownBy(() -> 
            messageDispatcher.dispatch(
                "room1", "user1", "20201234", "홍길동", "profile.jpg", request
            )
        ).isInstanceOf(ChatException.class);
    }
}