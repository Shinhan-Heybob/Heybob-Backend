package com.shinhan.heybob.chat.domain.chat.model;

import com.shinhan.heybob.chat.domain.chat.dto.PaymentRequestData;
import com.shinhan.heybob.chat.domain.chat.dto.PaymentCompleteData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.LocalDateTime;

@Document(collection = "chat_messages")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {
    @Id
    private String id;  // MongoDB _id (messageId와 동일한 값 사용)
    private String roomId;
    private String senderId;
    private String studentId;
    private String senderName;
    private String profileImageUrl;
    private String content;
    private MessageType messageType;
    private LocalDateTime timestamp;
    private PaymentRequestData paymentRequestData;  // 결제 요청 데이터 (PAYMENT_REQUEST 타입에서 사용)
    private PaymentCompleteData paymentCompleteData; // 결제 완료 데이터 (PAYMENT_COMPLETE 타입에서 사용)
    private Boolean emergencyFallback;  // Redis Stream 실패로 MongoDB에 직접 저장된 경우
    
    public enum MessageType {
        CHAT,               // 일반 채팅 메시지
        JOIN,               // 사용자 입장
        LEAVE,              // 사용자 퇴장
        PAYMENT_REQUEST,    // 송금 요청 "이지민님이 요청했습니다. (12000원 송금하기)"
        PAYMENT_COMPLETE    // 정산 완료 "정산이 완료되었습니다"
    }
}