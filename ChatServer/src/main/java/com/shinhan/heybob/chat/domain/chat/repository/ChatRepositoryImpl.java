package com.shinhan.heybob.chat.domain.chat.repository;

import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ChatRepositoryImpl implements ChatRepository {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public ChatMessage save(ChatMessage message) {
        ChatMessage saved = mongoTemplate.save(message);
        log.info("MongoDB 저장 완료: messageId={}, roomId={}", saved.getId(), saved.getRoomId());
        return saved;
    }
    
    @Override
    public List<ChatMessage> findRecentMessagesByRoomId(String roomId, int limit) {
        Query query = new Query(Criteria.where("roomId").is(roomId))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(limit);
        
        List<ChatMessage> messages = mongoTemplate.find(query, ChatMessage.class);
        log.info("최근 메시지 조회: roomId={}, count={}", roomId, messages.size());
        return messages;
    }
    
    @Override
    public List<ChatMessage> findMessagesBeforeId(String roomId, String beforeMessageId, int limit) {
        // 먼저 기준 메시지 찾기
        ChatMessage beforeMessage = mongoTemplate.findById(beforeMessageId, ChatMessage.class);
        if (beforeMessage == null) {
            log.warn("기준 메시지를 찾을 수 없음: messageId={}", beforeMessageId);
            return List.of();
        }
        
        // 기준 메시지보다 이전 메시지들 조회
        Query query = new Query(Criteria.where("roomId").is(roomId)
                .and("timestamp").lt(beforeMessage.getTimestamp()))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(limit);
        
        List<ChatMessage> messages = mongoTemplate.find(query, ChatMessage.class);
        log.info("이전 메시지 조회: roomId={}, beforeId={}, count={}", roomId, beforeMessageId, messages.size());
        return messages;
    }
    
    @Override
    public List<ChatMessage> saveAll(List<ChatMessage> messages) {
        Collection<ChatMessage> savedMessages = mongoTemplate.insertAll(messages);
        List<ChatMessage> result = new ArrayList<>(savedMessages);
        log.info("MongoDB 일괄 저장 완료: count={}", result.size());
        return result;
    }
}