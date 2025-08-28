# HeyBob Chat Server API 명세서

## 개요
HeyBob 채팅 서버 API 명세서입니다. WebSocket과 HTTP REST API를 통해 실시간 채팅 기능을 제공합니다.

## 기본 정보
- **Base URL**: `http://localhost:8081`
- **WebSocket URL**: `ws://localhost:8081/ws`
- **Protocol**: WebSocket (STOMP), HTTP REST

---

## 1. WebSocket API

### 연결 설정
```
연결 URL: ws://localhost:8081/ws
Protocol: STOMP over WebSocket
```

### 구독 엔드포인트

#### 채팅방 구독
- **URL**: `/topic/room/{roomId}`
- **Method**: SUBSCRIBE
- **Description**: 특정 채팅방의 메시지를 실시간으로 받습니다.

**Example:**
```javascript
stompClient.subscribe('/topic/room/room123', function(message) {
    console.log('received:', JSON.parse(message.body));
});
```

#### 에러 메시지 구독
- **URL**: `/queue/errors`
- **Method**: SUBSCRIBE  
- **Description**: 사용자별 에러 메시지를 받습니다.

### 메시지 전송 엔드포인트

#### 1.1 일반 채팅 메시지 전송
- **URL**: `/app/chat/{roomId}`
- **Method**: MESSAGE
- **Headers**:
  - `X-User-Id`: 사용자 ID
  - `X-Student-Id`: 학번
  - `X-User-Name`: 사용자 이름
  - `X-Profile-Image`: 프로필 이미지 URL

**Request Body:**
```json
{
    "content": "안녕하세요!",
    "messageType": "CHAT"
}
```

**Response (Broadcasting):**
```json
{
    "messageId": "uuid-string",
    "roomId": "room123",
    "senderId": "user123",
    "studentId": "20201234",
    "senderName": "홍길동",
    "profileImageUrl": "https://example.com/profile.jpg",
    "content": "안녕하세요!",
    "messageType": "CHAT",
    "timestamp": "2024-08-26T13:30:45.123Z"
}
```

#### 1.2 결제 요청 메시지 전송
- **URL**: `/app/chat/{roomId}`
- **Method**: MESSAGE

**Request Body:**
```json
{
    "content": "홍길동님이 정산을 요청했습니다.",
    "messageType": "PAYMENT_REQUEST",
    "paymentRequestData": {
        "requesterId": 123,
        "requesterName": "홍길동",
        "requesterStudentId": "20201234",
        "requesterProfileImg": "https://example.com/profile.jpg",
        "requestAmount": 15000
    }
}
```

**Response:**
```json
{
    "messageId": "uuid-string",
    "roomId": "room123",
    "senderId": "user123",
    "studentId": "20201234",
    "senderName": "홍길동",
    "profileImageUrl": "https://example.com/profile.jpg",
    "content": "홍길동님이 정산을 요청했습니다.",
    "messageType": "PAYMENT_REQUEST",
    "timestamp": "2024-08-26T13:30:45.123Z",
    "paymentRequestData": {
        "settlementId": "settle-uuid",
        "roomId": "room123",
        "requesterId": 123,
        "requesterName": "홍길동",
        "requesterStudentId": "20201234",
        "requesterProfileImg": "https://example.com/profile.jpg",
        "requestAmount": 15000,
        "settlementUrl": "/main/settlement/settle-uuid"
    }
}
```

#### 1.3 결제 완료 메시지 전송
- **URL**: `/app/chat/{roomId}`
- **Method**: MESSAGE

**Request Body:**
```json
{
    "content": "정산이 완료되었습니다.",
    "messageType": "PAYMENT_COMPLETE",
    "paymentCompleteData": {
        "settlementId": "settle-123",
        "roomId": "room123",
        "recipientId": "user456",
        "recipientName": "김철수",
        "completedAmount": 15000
    }
}
```

#### 1.4 입장 메시지
- **URL**: `/app/chat/{roomId}`
- **Method**: MESSAGE

**Request Body:**
```json
{
    "content": "",
    "messageType": "JOIN"
}
```

**Response:**
```json
{
    "messageId": "uuid-string",
    "roomId": "room123",
    "senderId": "user123",
    "studentId": "20201234",
    "senderName": "홍길동",
    "profileImageUrl": "https://example.com/profile.jpg",
    "content": "홍길동님이 입장하셨습니다.",
    "messageType": "JOIN",
    "timestamp": "2024-08-26T13:30:45.123Z"
}
```

#### 1.5 학식 정보 요청
- **URL**: `/app/chat/{roomId}/cafeteria`
- **Method**: MESSAGE
- **Headers**: 동일
- **Request Body**: 없음

**Response:**
```json
{
    "messageId": "uuid-string",
    "roomId": "room123",
    "senderId": "system",
    "senderName": "시스템",
    "content": "오늘의 학식 정보입니다...",
    "messageType": "CAFETERIA_INFO",
    "timestamp": "2024-08-26T13:30:45.123Z"
}
```

---

## 2. HTTP REST API

### 2.1 채팅 히스토리 조회
- **URL**: `GET /api/chat/rooms/{roomId}/messages`
- **Headers**:
  - `X-User-Id`: 사용자 ID (선택사항, 테스트용)

**Query Parameters:**
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| before | string | No | - | 특정 메시지 ID 이전의 메시지들을 조회 |
| limit | int | No | 50 | 조회할 메시지 개수 (1-100) |

**Response:**
```json
{
    "messages": [
        {
            "messageId": "uuid-string",
            "roomId": "room123",
            "senderId": "user123",
            "studentId": "20201234",
            "senderName": "홍길동",
            "profileImageUrl": "https://example.com/profile.jpg",
            "content": "안녕하세요!",
            "messageType": "CHAT",
            "timestamp": "2024-08-26T13:30:45.123Z"
        }
    ],
    "lastMessageId": "last-message-uuid",
    "hasMore": true,
    "totalCount": 25
}
```

### 2.2 레거시 채팅 히스토리 조회
- **URL**: `GET /api/chat/rooms/{roomId}/messages/legacy`
- **Description**: 기존 API 호환성을 위한 레거시 엔드포인트

**Response:**
```json
[
    {
        "messageId": "uuid-string",
        "roomId": "room123",
        "senderId": "user123",
        "studentId": "20201234",
        "senderName": "홍길동",
        "profileImageUrl": "https://example.com/profile.jpg",
        "content": "안녕하세요!",
        "messageType": "CHAT",
        "timestamp": "2024-08-26T13:30:45.123Z"
    }
]
```

### 2.3 학식 정보 전송 (HTTP)
- **URL**: `POST /api/chat/{roomId}/cafeteria`
- **Headers**:
  - `X-User-Id`: 사용자 ID (선택사항)
  - `X-User-Name`: 사용자 이름 (선택사항)
- **Request Body**: `{}` (빈 객체 또는 생략 가능)

**Response:**
```json
{
    "success": true,
    "message": "학식 정보가 전송되었습니다",
    "messageId": "uuid-string"
}
```

---

## 3. 메시지 타입

### MessageType Enum
| Type | Description |
|------|-------------|
| `CHAT` | 일반 채팅 메시지 |
| `JOIN` | 사용자 입장 알림 |
| `LEAVE` | 사용자 퇴장 알림 |
| `PAYMENT_REQUEST` | 결제/정산 요청 |
| `PAYMENT_COMPLETE` | 결제/정산 완료 |
| `SAVINGS_REQUEST` | 적금 요청 |
| `SAVINGS_COMPLETE` | 적금 완료 |
| `CAFETERIA_INFO` | 학식 정보 |

---

## 4. 에러 응답

### HTTP 에러 응답
```json
{
    "success": false,
    "error": "에러 메시지"
}
```

### WebSocket 에러 응답 (`/queue/errors`)
```json
{
    "code": "ROOM_NOT_FOUND",
    "message": "채팅방을 찾을 수 없습니다",
    "timestamp": "2024-08-26T13:30:45.123Z"
}
```

### 에러 코드
| Code | Message |
|------|---------|
| `ROOM_NOT_FOUND` | 채팅방을 찾을 수 없습니다 |
| `INVALID_REQUEST` | 잘못된 요청입니다 |
| `INVALID_MESSAGE_TYPE` | 지원하지 않는 메시지 타입입니다 |
| `MESSAGE_SAVE_FAILED` | 메시지 저장에 실패했습니다 |
| `INTERNAL_SERVER_ERROR` | 서버 내부 오류가 발생했습니다 |

---

## 5. 개발/테스트 정보

### 기본값
- 헤더가 없을 경우 테스트용 기본값 사용
- `X-User-Id`: "test-user" 또는 "20000622"
- `X-User-Name`: "테스트사용자" 또는 "개발테스트사용자"

### CORS
- 모든 Origin 허용 (`origins = "*"`)

### 데이터 저장
- **일반 메시지**: MongoDB 직접 저장
- **금융 메시지** (PAYMENT_REQUEST, PAYMENT_COMPLETE): Redis Stream → MongoDB (유실 방지)

---

## 6. 사용 예제

### JavaScript WebSocket 클라이언트 예제
```javascript
// WebSocket 연결
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);

// 연결
stompClient.connect({
    'X-User-Id': 'user123',
    'X-Student-Id': '20201234', 
    'X-User-Name': '홍길동',
    'X-Profile-Image': 'https://example.com/profile.jpg'
}, function(frame) {
    console.log('Connected:', frame);
    
    // 채팅방 구독
    stompClient.subscribe('/topic/room/room123', function(message) {
        const chatMessage = JSON.parse(message.body);
        console.log('Received message:', chatMessage);
    });
    
    // 메시지 전송
    stompClient.send('/app/chat/room123', {}, JSON.stringify({
        content: '안녕하세요!',
        messageType: 'CHAT'
    }));
});
```

### HTTP API 호출 예제
```javascript
// 채팅 히스토리 조회
fetch('/api/chat/rooms/room123/messages?limit=20', {
    headers: {
        'X-User-Id': 'user123'
    }
})
.then(response => response.json())
.then(data => console.log(data));

// 학식 정보 전송
fetch('/api/chat/room123/cafeteria', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'X-User-Id': 'user123',
        'X-User-Name': '홍길동'
    },
    body: JSON.stringify({})
})
.then(response => response.json())
.then(data => console.log(data));
```