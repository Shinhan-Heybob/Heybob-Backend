package com.shinhan.heybob.chat.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UiState {
    private Boolean isRequester;
    private String userResponseStatus;  // "pending", "accepted", "rejected"
    private List<String> availableActions;  // ["cancel", "accept", "reject", "view_details"]
    private Boolean isExpired;
}