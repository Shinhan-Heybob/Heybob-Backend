package com.shinhan.heybob.domain.notification.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.notification.dto.ChatEventMessageDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisStreamPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.streams.chat-events-key:stream:chat:events}")
    private String streamKey;

    public RecordId publish(ChatEventMessageDto message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            ObjectRecord<String, String> record = StreamRecords
                    .newRecord()
                    .in(streamKey)
                    .ofObject(payload);
            return stringRedisTemplate.opsForStream().add(record);
        } catch (Exception e) {
            throw new HeybobException(ExceptionStatus.REDIS_STREAM_FAIL_TO_PUBLISH);
        }
    }
}
