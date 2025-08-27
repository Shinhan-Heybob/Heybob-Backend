# 밥약(MealAppointment) API 명세서

## Base URL
```
http://localhost:8080/api
```

## 공통 응답 형식
### 성공 응답
```json
{
  "data": { ... },
  "message": "Success",
  "status": 200
}
```

### 에러 응답
```json
{
  "error": "에러 메시지",
  "status": 400,
  "timestamp": "2024-01-01T12:00:00"
}
```

---

## 1. 밥약 생성
### POST /meal-appointments

밥약 또는 정기모임을 생성합니다.

#### Request
**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
  "name": "점심 밥약",
  "memo": "맛있는 곳에서 먹어요",
  "appointmentDate": "2024-12-25",
  "appointmentTime": "12:30:00",
  "participantIds": [1, 2, 3],
  "creatorId": 1,
  "mealType": "MEAL_APPOINTMENT"  // "MEAL_APPOINTMENT" 또는 "REGULAR_MEETING", 미입력시 기본값: "MEAL_APPOINTMENT"
}
```

#### Request Fields
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| name | String | Yes | 밥약/모임 이름 (최대 100자) |
| memo | String | No | 메모 |
| appointmentDate | String | Yes | 약속 날짜 (YYYY-MM-DD) |
| appointmentTime | String | Yes | 약속 시간 (HH:mm:ss) |
| participantIds | List<Long> | Yes | 참여자 ID 목록 |
| creatorId | Long | No | 생성자 ID (미입력시 기본값 1) |
| mealType | String | No | 타입: "MEAL_APPOINTMENT" (밥약) 또는 "REGULAR_MEETING" (정기모임) |

#### Response
**Status Code:** 201 Created

**Body:**
```json
{
  "id": 1
}
```

#### Error Cases
- 400 Bad Request: 필수 필드 누락, 잘못된 mealType 값
- 400 Bad Request: 과거 시간으로 약속 생성 시도
- 404 Not Found: 존재하지 않는 사용자 ID

---

## 2. 밥약 상세 조회
### GET /meal-appointments/{appointmentId}

특정 밥약의 상세 정보를 조회합니다.

#### Request
**Path Parameters:**
- `appointmentId` (Long): 밥약 ID

#### Response
**Status Code:** 200 OK

**Body:**
```json
{
  "id": 1,
  "name": "점심 밥약",
  "memo": "맛있는 곳에서 먹어요",
  "appointmentDate": "2024-12-25",
  "appointmentTime": "12:30:00",
  "creator": {
    "id": 1,
    "name": "김철수",
    "studentId": "2021123456",
    "department": "컴퓨터공학과"
  },
  "participants": [
    {
      "id": 2,
      "name": "이영희",
      "studentId": "2021123457",
      "department": "경영학과"
    }
  ],
  "status": "ACTIVE",
  "mealType": "MEAL_APPOINTMENT",
  "chatRoomId": 100,
  "createdAt": "2024-01-01T10:00:00"
}
```

#### Error Cases
- 404 Not Found: 존재하지 않는 밥약 ID

---

## 3. 사용자 밥약 목록 조회 (상세)
### GET /meal-appointments

사용자가 참여하는 모든 밥약의 상세 정보를 조회합니다.

#### Request
**Query Parameters:**
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| userId | Long | Yes | - | 사용자 ID |
| type | String | No | "all" | 필터 타입: "all", "MEAL_APPOINTMENT", "REGULAR_MEETING" |

**Example:**
```
GET /meal-appointments?userId=1&type=MEAL_APPOINTMENT
GET /meal-appointments?userId=1&type=all
```

#### Response
**Status Code:** 200 OK

**Body:**
```json
[
  {
    "id": 1,
    "name": "점심 밥약",
    "memo": "맛있는 곳에서 먹어요",
    "appointmentDate": "2024-12-25",
    "appointmentTime": "12:30:00",
    "creator": { ... },
    "participants": [ ... ],
    "status": "ACTIVE",
    "mealType": "MEAL_APPOINTMENT",
    "chatRoomId": 100,
    "createdAt": "2024-01-01T10:00:00"
  },
  {
    "id": 2,
    "name": "스터디 정기모임",
    "memo": "알고리즘 스터디",
    "appointmentDate": "2024-12-26",
    "appointmentTime": "19:00:00",
    "creator": { ... },
    "participants": [ ... ],
    "status": "ACTIVE",
    "mealType": "REGULAR_MEETING",
    "chatRoomId": 101,
    "createdAt": "2024-01-02T10:00:00"
  }
]
```

#### Error Cases
- 404 Not Found: 존재하지 않는 사용자 ID
- 400 Bad Request: 잘못된 type 값

---

## 4. 사용자 밥약 리스트 조회 (간략)
### GET /meal-appointments/list

사용자가 생성한 밥약의 간략한 정보를 조회합니다. 활성 상태 필터링 가능.

#### Request
**Query Parameters:**
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| userId | Long | Yes | - | 사용자 ID |
| status | String | No | "all" | 상태 필터: "all", "active", "inactive" |
| type | String | No | "all" | 타입 필터: "all", "MEAL_APPOINTMENT", "REGULAR_MEETING" |

**Example:**
```
GET /meal-appointments/list?userId=1&status=active&type=MEAL_APPOINTMENT
GET /meal-appointments/list?userId=1&status=all&type=REGULAR_MEETING
```

#### Response
**Status Code:** 200 OK

**Body:**
```json
[
  {
    "id": 1,
    "name": "점심 밥약",
    "creatorName": "김철수",
    "creatorStudentId": "2021123456",
    "creatorDepartment": "컴퓨터공학과",
    "chatRoomId": 100,
    "appointmentDate": "2024-12-25",
    "appointmentTime": "12:30:00",
    "mealType": "MEAL_APPOINTMENT",
    "isActive": true
  },
  {
    "id": 2,
    "name": "스터디 정기모임",
    "creatorName": "김철수",
    "creatorStudentId": "2021123456",
    "creatorDepartment": "컴퓨터공학과",
    "chatRoomId": 101,
    "appointmentDate": "2024-12-26",
    "appointmentTime": "19:00:00",
    "mealType": "REGULAR_MEETING",
    "isActive": true
  }
]
```

**Response Fields:**
- `isActive`: 현재 시간 기준으로 약속 시간이 미래인 경우 true

#### Sorting
- 활성화된 밥약이 먼저 표시
- 같은 활성 상태 내에서는 날짜/시간 순으로 정렬

#### Error Cases
- 404 Not Found: 존재하지 않는 사용자 ID

---

## 5. 정산 브로드캐스트 전송
### POST /meal-appointments/{appointmentId}/settlement-broadcast

특정 밥약의 채팅방에 정산 메시지를 브로드캐스트합니다.

#### Request
**Path Parameters:**
- `appointmentId` (Long): 밥약 ID

**Body:**
```json
{
  "settlementId": "SETTLE_12345",
  "requesterName": "김철수",
  "requestAmount": 15000,
  "message": "오늘 점심값 정산 부탁드립니다!"
}
```

#### Response
**Status Code:** 200 OK

**Body:**
```json
{
  "success": true,
  "messageId": "MSG_67890",
  "appointmentId": 1,
  "chatRoomId": "100",
  "message": "정산 브로드캐스트가 전송되었습니다"
}
```

#### Error Cases
- 404 Not Found: 존재하지 않는 밥약 ID
- 500 Internal Server Error: 채팅 서버 연동 실패

---

## MealType Enum 값

| Value | Description | 용도 |
|-------|-------------|------|
| MEAL_APPOINTMENT | 밥약 | 일반적인 식사 약속 |
| REGULAR_MEETING | 정기모임 | 주기적으로 반복되는 모임 |

---

## 주의사항

1. **날짜/시간 형식**
   - 날짜: ISO 8601 형식 (YYYY-MM-DD)
   - 시간: 24시간 형식 (HH:mm:ss)

2. **필터링 파라미터**
   - type 파라미터에 잘못된 값 입력 시 400 에러 반환
   - "all" 입력 시 모든 타입 조회
   - 대소문자 구분 없음 (all, ALL, All 모두 가능)

3. **참여자 관련**
   - 참여자 ID는 중복 불가
   - 생성자는 자동으로 참여자에 포함됨

4. **정산 기능**
   - 채팅방이 연결된 밥약에서만 사용 가능
   - 채팅 서버와의 통신 실패 시 재시도 필요

---

## 변경 이력
- 2024.12.27: MealType 필드 추가 (밥약/정기모임 구분)
- 2024.12.27: 목록 조회 API에 type 필터 파라미터 추가