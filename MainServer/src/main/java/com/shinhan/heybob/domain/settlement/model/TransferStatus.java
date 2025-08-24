package com.shinhan.heybob.domain.settlement.model;

public enum TransferStatus {
    PENDING, // 아직 결제 안함
    SUCCESS, // 이체 성공
    FAILED, // 이체 실패
    CANCELED; // 사용자/관리자 취소
}
