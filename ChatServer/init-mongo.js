// MongoDB 초기화 스크립트
db = db.getSiblingDB('heybob');

// 사용자 생성
db.createUser({
  user: 'heybob',
  pwd: 'heybobssafy',
  roles: [
    {
      role: 'readWrite',
      db: 'heybob'
    }
  ]
});

// 채팅 메시지 컬렉션 생성 및 인덱스 설정
db.createCollection('chat_messages');

// 인덱스 생성 (성능 최적화)
db.chat_messages.createIndex({ "roomId": 1, "timestamp": -1 });
db.chat_messages.createIndex({ "messageId": 1 }, { unique: true });
db.chat_messages.createIndex({ "senderId": 1 });

print('MongoDB 초기화 완료!');