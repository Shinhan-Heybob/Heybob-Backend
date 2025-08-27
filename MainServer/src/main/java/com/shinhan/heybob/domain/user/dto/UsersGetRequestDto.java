package com.shinhan.heybob.domain.user.dto;

import java.util.List;

public record UsersGetRequestDto(
        List<Long> userIds
) {}
