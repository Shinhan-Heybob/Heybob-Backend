package com.shinhan.heybob.chat.domain.chat.model;

import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "chat_messages")
@Data
@Builder
public class ChatMessage {
    @Id
    private String id;
    private String roomId;
    private String senderId;
    private String studentId;
    private String senderName;
    private String profileImageUrl;
    private String content;
    private MessageType messageType;
    private LocalDateTime timestamp;
    private SettlementData settlementData;  // 정산 관련 데이터
    
    public enum MessageType {
        // 일반 메시지 (바로 MongoDB 저장)
        CHAT, JOIN, LEAVE, SYSTEM,
        
        // 중요한 금융 알림 메시지 (Redis Stream → MongoDB)
        PAYMENT_REQUEST,     // 결제 요청 알림
        PAYMENT_CONFIRM,     // 결제 확인 알림  
        PAYMENT_COMPLETE,    // 모두 결제 확인 완료 알림
        
        // 정산 버튼 상호작용 메시지 (Redis Stream → MongoDB)
        SETTLEMENT_ACCEPT,   // "정산하기" 클릭
        SETTLEMENT_REJECT,   // "거절하기" 클릭
        SETTLEMENT_CANCEL,   // "취소하기" 클릭
        SETTLEMENT_TIMEOUT   // 시간 만료
    }
}