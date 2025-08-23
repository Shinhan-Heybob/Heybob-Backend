# 🧪 HeyBob 채팅 서버 로컬 테스트 가이드

## 📋 사전 준비사항
- Docker Desktop 설치 필요
- Java 17+ 설치 필요

## 🚀 로컬 환경 실행 방법

### 1. **애플리케이션 빌드**
```bash
# ChatServer 디렉토리에서 실행
cd C:\Users\SSAFY\heybob_main\Heybob-Backend\ChatServer

# Gradle 빌드
./gradlew build -x test
```

### 2. **Docker Compose로 전체 서비스 실행**
```bash
# 모든 서비스 한 번에 실행 (Chat Server + Redis + MongoDB)
docker-compose -f docker-compose.local.yml up -d

# 실시간 로그 확인
docker-compose -f docker-compose.local.yml logs -f chat-server

# 컨테이너 상태 확인
docker-compose -f docker-compose.local.yml ps
```

### 3. **서비스 확인**
- **채팅 서버**: http://localhost:8081
- **Redis**: localhost:6379
- **MongoDB**: localhost:27017

---

## 🔧 테스트 데이터 준비

### WebSocket 테스트용 HTML 파일
아래 HTML을 저장하고 브라우저에서 열어서 테스트하세요:

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>HeyBob Chat Test</title>
    <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>
</head>
<body>
    <h2>HeyBob 채팅 테스트</h2>
    
    <div>
        <button onclick="connect()">연결</button>
        <button onclick="disconnect()">해제</button>
        <button onclick="subscribe()">방 구독</button>
    </div>
    
    <div>
        <input type="text" id="messageInput" placeholder="메시지 입력">
        <button onclick="sendMessage()">전송</button>
        <button onclick="sendSettlement()">정산 요청</button>
    </div>
    
    <div>
        <h3>정산 응답</h3>
        <button onclick="acceptSettlement()">수락</button>
        <button onclick="rejectSettlement()">거절</button>
        <button onclick="cancelSettlement()">취소</button>
    </div>
    
    <div id="messages" style="border: 1px solid #ccc; height: 300px; overflow-y: scroll; padding: 10px;"></div>

    <script>
        let stompClient = null;
        const roomId = 'TEST_ROOM_001';
        const userId = '20000622';
        let currentSettlementId = null;

        function connect() {
            const socket = new SockJS('http://localhost:8081/ws');
            stompClient = Stomp.over(socket);
            
            stompClient.connect({
                'X-User-Id': userId,
                'X-Student-Id': userId,
                'X-User-Name': '테스트사용자',
                'X-Profile-Image': 'https://example.com/profile.jpg'
            }, function(frame) {
                console.log('연결됨: ' + frame);
                addMessage('시스템', '서버에 연결되었습니다.');
            });
        }

        function disconnect() {
            if (stompClient !== null) {
                stompClient.disconnect();
                addMessage('시스템', '연결이 해제되었습니다.');
            }
        }

        function subscribe() {
            if (stompClient && stompClient.connected) {
                // 일반 메시지 구독
                stompClient.subscribe('/topic/room/' + roomId, function(message) {
                    const chatMessage = JSON.parse(message.body);
                    addMessage(chatMessage.senderName, chatMessage.content);
                    
                    if (chatMessage.settlementData) {
                        currentSettlementId = chatMessage.settlementData.settlementId;
                        addMessage('정산', '정산 요청: ' + chatMessage.settlementData.note + 
                                          ' (금액: ' + chatMessage.settlementData.totalAmount + '원)');
                    }
                });
                
                // 정산 업데이트 구독
                stompClient.subscribe('/topic/room/' + roomId + '/settlement', function(message) {
                    const settlement = JSON.parse(message.body);
                    addMessage('정산 업데이트', JSON.stringify(settlement, null, 2));
                });
                
                addMessage('시스템', roomId + ' 방을 구독했습니다.');
            }
        }

        function sendMessage() {
            const messageInput = document.getElementById('messageInput');
            const content = messageInput.value;
            
            if (stompClient && content) {
                const chatMessage = {
                    content: content,
                    messageType: 'CHAT'
                };
                
                stompClient.send('/app/chat/' + roomId, {}, JSON.stringify(chatMessage));
                messageInput.value = '';
            }
        }

        function sendSettlement() {
            if (stompClient) {
                currentSettlementId = 'SETTLEMENT_' + Date.now();
                
                const settlementMessage = {
                    content: '점심값 정산해요!',
                    messageType: 'SETTLEMENT_REQUEST',
                    settlementData: {
                        settlementId: currentSettlementId,
                        note: '점심값 정산',
                        totalAmount: 50000,
                        perPersonAmount: 12500,
                        participants: [userId, '20000623', '20000624', '20000625'],
                        expiryTime: new Date(Date.now() + 30*60*1000).toISOString(), // 30분 후
                        participantStatus: {}
                    }
                };
                
                stompClient.send('/app/chat/' + roomId, {}, JSON.stringify(settlementMessage));
            }
        }

        function acceptSettlement() {
            sendSettlementResponse('SETTLEMENT_ACCEPT');
        }

        function rejectSettlement() {
            sendSettlementResponse('SETTLEMENT_REJECT');
        }

        function cancelSettlement() {
            sendSettlementResponse('SETTLEMENT_CANCEL');
        }

        function sendSettlementResponse(messageType) {
            if (stompClient && currentSettlementId) {
                const response = {
                    messageType: messageType,
                    settlementId: currentSettlementId,
                    content: messageType.replace('SETTLEMENT_', '').toLowerCase()
                };
                
                stompClient.send('/app/chat/' + roomId, {}, JSON.stringify(response));
            } else {
                alert('정산 ID가 없습니다. 먼저 정산을 요청하세요.');
            }
        }

        function addMessage(sender, content) {
            const messages = document.getElementById('messages');
            const messageElement = document.createElement('div');
            messageElement.innerHTML = '<strong>' + sender + ':</strong> ' + content;
            messages.appendChild(messageElement);
            messages.scrollTop = messages.scrollHeight;
        }

        // Enter 키로 메시지 전송
        document.getElementById('messageInput').addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                sendMessage();
            }
        });
    </script>
</body>
</html>
```

---

## 🧪 테스트 시나리오

### 1. **연결 테스트**
1. HTML 파일을 브라우저에서 열기
2. "연결" 버튼 클릭
3. "방 구독" 버튼 클릭
4. 연결 성공 메시지 확인

### 2. **채팅 테스트**
1. 메시지 입력 후 전송
2. 브라우저에서 메시지 표시 확인
3. 여러 탭에서 동시 테스트

### 3. **정산 테스트**
1. "정산 요청" 버튼 클릭
2. 정산 메시지 확인
3. "수락/거절/취소" 버튼으로 응답
4. 실시간 업데이트 확인

### 4. **REST API 테스트**
```bash
# 채팅 히스토리 조회
curl -X GET "http://localhost:8081/api/chat/rooms/TEST_ROOM_001/messages?limit=10" \
  -H "X-User-Id: 20000622"
```

---

## 🛠 서비스 중지

```bash
# Spring Boot 애플리케이션 중지 (Ctrl+C)

# Docker 서비스 중지
docker-compose -f docker-compose.local.yml down

# 데이터까지 완전 삭제 (필요시)
docker-compose -f docker-compose.local.yml down -v
```

---

## 🔍 로그 확인

```bash
# Docker 로그 확인
docker-compose -f docker-compose.local.yml logs -f

# 특정 서비스 로그
docker-compose -f docker-compose.local.yml logs -f redis
docker-compose -f docker-compose.local.yml logs -f mongodb
```

이제 로컬에서 편하게 테스트할 수 있습니다! 🚀