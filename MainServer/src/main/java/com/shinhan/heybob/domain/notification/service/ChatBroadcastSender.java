package com.shinhan.heybob.domain.notification.service;

public interface ChatBroadcastSender {

    // 정산 요청 (PAYMENT_REQUEST)
    String sendPaymentRequest(
            String roomId,
            String senderId, String studentId, String senderName, String profileImageUrl,
            String messageText,
            String settlementId, String requesterId, String requesterName,
            String requesterStudentId, String requesterProfileImg,
            int requestAmount,
            String settlementUrl
    );

    // 정산 이체 1인 완료 (PAYMENT_COMPLETE)
    String sendPaymentComplete(
            String roomId,
            String senderId, String studentId, String senderName, String profileImageUrl,
            String content,
            String settlementId, String recipientId, String recipientName,
            int completedAmount
    );

    // 적금 요청 (SAVINGS_REQUEST)
    String sendSavingsRequest(
            String roomId,
            String senderId, String studentId, String senderName, String profileImageUrl,
            String content,
            String settlementId, String requesterId, String requesterName,
            String requesterStudentId, String requesterProfileImg,
            int requestAmount,
            String settlementUrl
    );

    // 적금 이체 1인 완료 (SAVINGS_COMPLETE)
    String sendSavingsComplete(
            String roomId,
            String senderId, String studentId, String senderName, String profileImageUrl,
            String content,
            String settlementId, String recipientId, String recipientName,
            int completedAmount
    );

}
