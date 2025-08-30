    package com.shinhan.heybob.domain.notification.service;

    import lombok.RequiredArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.data.redis.core.RedisTemplate;
    import org.springframework.stereotype.Service;

    import java.util.LinkedHashMap;
    import java.util.Map;

    @Service
    @RequiredArgsConstructor
    @Slf4j
    public class ChatBroadcastSenderImpl implements ChatBroadcastSender {

        private final RedisTemplate<String, String> streamRedisTemplate; // String 직렬화 템플릿
        private static final String STREAM = "main-to-chat-stream";
        private static final String SRC = "MAIN";
        private static final String TGT = "CHAT";

        private static String now() { return java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).toString(); }
        private static String s(Object v) { return v == null ? "" : String.valueOf(v); }

        /** 공통 헤더 */
        private Map<String,String> header(String messageType) {
            Map<String,String> m = new LinkedHashMap<>();
            m.put("messageId", java.util.UUID.randomUUID().toString());
            m.put("messageType", messageType);
            m.put("sourceServer", SRC);
            m.put("targetServer", TGT);
            m.put("timestamp", now());
            m.put("retryCount", "0");
            return m;
        }

        /** ✅ 정산 요청(PAYMENT_REQUEST) */
        @Override
        public String sendPaymentRequest(
                String roomId,
                String senderId, String studentId, String senderName, String profileImageUrl,
                String messageText,          // 예: "점심 정산입니다"
                String settlementId,
                String requesterId, String requesterName, String requesterStudentId,
                String requesterProfileImg,
                int requestAmount,
                String settlementUrl        // 정산 상세 진입 URL(옵션이면 "" 넘기기)
        ) {
            Map<String,String> m = header("PAYMENT_REQUEST");

            // 채팅 표시용 상단 공통(컨슈머가 그대로 씀)
            m.put("payload_roomId", s(roomId));
            m.put("payload_senderId", s(senderId));
            m.put("payload_studentId", s(studentId));
            m.put("payload_senderName", s(senderName));
            m.put("payload_profileImageUrl", s(profileImageUrl));

            // 메시지 본문(컨슈머 요구 스펙: payload_message 필수)
            m.put("payload_message", s(messageText));
            // (원하면 동일 문자열을 content로도 넣어 두기)
            m.put("payload_content", s(messageText));

            // 요청 데이터
            m.put("payload_settlementId", s(settlementId));
            m.put("payload_requesterId", s(requesterId));
            m.put("payload_requesterName", s(requesterName));
            m.put("payload_requesterStudentId", s(requesterStudentId));
            m.put("payload_requesterProfileImg", s(requesterProfileImg));
            m.put("payload_requestAmount", s(requestAmount));
            if (!s(settlementUrl).isEmpty()) {
                m.put("payload_settlementUrl", s(settlementUrl));
            }

            // 완료 데이터는 없음 표시(컨슈머가 null로 인식)
            m.put("payload_paymentCompleteData", "");

            streamRedisTemplate.opsForStream().add(STREAM, m);
            log.info("PAYMENT_REQUEST -> {}", m);
            return m.get("messageId");
        }

        /** 1) 정산 완료(PAYMENT_COMPLETE) */
        @Override
        public String sendPaymentComplete(
                String roomId,
                String senderId, String studentId, String senderName, String profileImageUrl,
                String content,        // "이영희님이 15,000원을 송금했습니다."
                String settlementId,   // "settlement-001"
                String recipientId, String recipientName,
                int completedAmount
        ) {
            Map<String,String> m = header("PAYMENT_COMPLETE");
            // 상단 공통 필드도 payload_* 로 전달(컨슈머가 그대로 쓰도록)
            m.put("payload_roomId", s(roomId));
            m.put("payload_senderId", s(senderId));
            m.put("payload_studentId", s(studentId));
            m.put("payload_senderName", s(senderName));
            m.put("payload_profileImageUrl", s(profileImageUrl));
            m.put("payload_content", s(content));

            // paymentCompleteData (중첩을 평탄화)
            m.put("payload_paymentCompleteData_settlementId", s(settlementId));
            m.put("payload_paymentCompleteData_roomId", s(roomId));
            m.put("payload_paymentCompleteData_recipientId", s(recipientId));
            m.put("payload_paymentCompleteData_recipientName", s(recipientName));
            m.put("payload_paymentCompleteData_completedAmount", s(completedAmount));
            // null 전달 명시
            m.put("payload_paymentRequestData", "");

            streamRedisTemplate.opsForStream().add(STREAM, m);
            log.info("PAYMENT_COMPLETE -> {}", m);
            return m.get("messageId");
        }

        /** 2) 적금 요청(SAVINGS_REQUEST) */
        @Override
        public String sendSavingsRequest(
                String roomId,
                String senderId, String studentId, String senderName, String profileImageUrl,
                String content,          // "김철수님이 적금을 요청했습니다."
                String settlementId,     // "savings-001"
                String requesterId, String requesterName, String requesterStudentId,
                String requesterProfileImg,
                int requestAmount,
                String settlementUrl     // "https://heybob.com/savings/001"
        ) {
            Map<String,String> m = header("SAVINGS_REQUEST");

            m.put("payload_roomId", s(roomId));
            m.put("payload_senderId", s(senderId));
            m.put("payload_studentId", s(studentId));
            m.put("payload_senderName", s(senderName));
            m.put("payload_profileImageUrl", s(profileImageUrl));
            m.put("payload_content", s(content));

            // paymentRequestData 평탄화
            m.put("payload_paymentRequestData_settlementId", s(settlementId));
            m.put("payload_paymentRequestData_roomId", s(roomId));
            m.put("payload_paymentRequestData_requesterId", s(requesterId));
            m.put("payload_paymentRequestData_requesterName", s(requesterName));
            m.put("payload_paymentRequestData_requesterStudentId", s(requesterStudentId));
            m.put("payload_paymentRequestData_requesterProfileImg", s(requesterProfileImg));
            m.put("payload_paymentRequestData_requestAmount", s(requestAmount));
            m.put("payload_paymentRequestData_settlementUrl", s(settlementUrl));

            // null 전달 명시
            m.put("payload_paymentCompleteData", "");

            streamRedisTemplate.opsForStream().add(STREAM, m);
            log.info("SAVINGS_REQUEST -> {}", m);
            return m.get("messageId");
        }

        /** 3) 적금 이체 1인 완료(SAVINGS_COMPLETE) */
        @Override
        public String sendSavingsComplete(
                String roomId,
                String senderId, String studentId, String senderName, String profileImageUrl,
                String content,          // "이영희님이 50,000원을 적금했습니다."
                String settlementId,     // "savings-001"
                String recipientId, String recipientName, // "savings-account", "모임적금"
                int completedAmount
        ) {
            Map<String,String> m = header("SAVINGS_COMPLETE");

            m.put("payload_roomId", s(roomId));
            m.put("payload_senderId", s(senderId));
            m.put("payload_studentId", s(studentId));
            m.put("payload_senderName", s(senderName));
            m.put("payload_profileImageUrl", s(profileImageUrl));
            m.put("payload_content", s(content));

            // paymentCompleteData 평탄화
            m.put("payload_paymentCompleteData_settlementId", s(settlementId));
            m.put("payload_paymentCompleteData_roomId", s(roomId));
            m.put("payload_paymentCompleteData_recipientId", s(recipientId));
            m.put("payload_paymentCompleteData_recipientName", s(recipientName));
            m.put("payload_paymentCompleteData_completedAmount", s(completedAmount));

            // null 전달 명시
            m.put("payload_paymentRequestData", "");
            m.put("payload_uiState", ""); // 예시의 uiState: null

            streamRedisTemplate.opsForStream().add(STREAM, m);
            log.info("SAVINGS_COMPLETE -> {}", m);
            return m.get("messageId");
        }
    }

