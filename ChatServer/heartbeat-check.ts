// heartbeat-check.ts
import { Client } from '@stomp/stompjs';
const SockJS = require('sockjs-client'); // CommonJS import로 생성자 사용 [require 방식]

const WEBSOCKET_BASE = process.env.WEBSOCKET_BASE || 'http://localhost:8080';
const WS_ENDPOINT = process.env.WS_ENDPOINT || '/ws';
const USER_ID = process.env.USER_ID || 'health-checker';
const STUDENT_ID = process.env.STUDENT_ID || 'S-HEALTH';
const USER_NAME = process.env.USER_NAME || 'HeartbeatTester';

// 최종 접속 URL
const finalServerUrl = `${WEBSOCKET_BASE}${WS_ENDPOINT}`;

// 호스트 추출(문자열 보장)
const host: string = (() => {
    try {
        const u = new URL(WEBSOCKET_BASE);
        return u.hostname; // 예: 43.205.55.49
    } catch {
        const m = WEBSOCKET_BASE.match(/^(?:ws|wss|http|https):\/\/([^/:]+)/i);
        return m?.[3] ?? '';
    }
})();

console.log('[TEST] Target', { finalServerUrl, host, USER_ID, STUDENT_ID, USER_NAME });

const client = new Client({
    webSocketFactory: () => new SockJS(finalServerUrl),
    connectHeaders: {
        'accept-version': '1.1,1.2',
        'heart-beat': '10000,10000', // 10s 요청
        'host': host,
        'X-User-Id': USER_ID,
        'X-Student-Id': STUDENT_ID,
        'X-User-Name': USER_NAME,
    },
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: (msg) => console.log('[STOMP]', msg), // >>> PING / <<< PONG 로그 확인
    reconnectDelay: 0, // 단발 테스트
    connectionTimeout: 60000,
    splitLargeFrames: false,
    forceBinaryWSFrames: false,
    appendMissingNULLonIncoming: false,
});

client.onConnect = () => {
    console.log('[TEST] CONNECTED: 하트비트 관찰 시작 (20초 후 종료)');
    // 20초 동안 PING/PONG 관찰 후 종료
    setTimeout(async () => {
        await client.deactivate();
        console.log('[TEST] DISCONNECTED');
        // 일부 환경에서 프로세스가 남으면 강제 종료
        process.exit(0);
    }, 20000);
};

client.onStompError = (frame) => {
    console.error('[TEST] STOMP ERROR header:', frame.headers['message']);
    console.error('[TEST] STOMP ERROR body:', frame.body);
};

client.onWebSocketError = (evt) => {
    console.error('[TEST] WS ERROR:', evt);
};

client.onWebSocketClose = (evt) => {
    console.warn('[TEST] WS CLOSE:', (evt as any).code, (evt as any).reason);
};

client.activate();
