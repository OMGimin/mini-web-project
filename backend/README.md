# Backend

Java 21과 Spring Boot 기반 Backend 프로젝트입니다. REST API, WebSocket/STOMP, PostgreSQL, Flyway Migration, Mock AI Service와 Swagger UI가 구성되어 있습니다.

## 책임별 구조

```text
src/
├── main/
│   ├── java/com/skala/team6/webmini/
│   │   ├── ai/          # Mock AI Client, 변론·판결 Service, AI API
│   │   ├── common/      # 공통 응답, 설정, 예외, Enum
│   │   ├── database/    # JPA Entity와 Repository
│   │   ├── demo/        # Demo 사용자 식별과 저장
│   │   ├── post/        # 게시글 REST API와 Service
│   │   ├── trial/       # 재판 생성, 진행, 채팅, 투표, 결과, STOMP
│   │   └── websocket/   # STOMP 인증·오류·Presence 처리
│   └── resources/
│       ├── db/migration/ # Flyway Database Migration
│       └── static/       # 로컬 STOMP 테스트 페이지
└── test/
    └── java/com/skala/team6/webmini/ # 도메인별 단위·통합·Acceptance Test
```

Controller는 요청·응답 변환을 담당하고, Service는 비즈니스 규칙을 담당합니다. Entity와 Repository는 `database/`에 모아 관리하며, API 응답으로 Entity를 직접 반환하지 않습니다.

## Backend 컨벤션

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| Java Package | lowercase | `com.skala.team6.webmini.trial` |
| Java Class | PascalCase | `TrialService` |
| Java 함수·변수 | camelCase | `createTrial` |
| Java 상수 | UPPER_SNAKE_CASE | `DEFAULT_TIMEOUT_MS` |
| DB 테이블·컬럼 | snake_case | `trial_events`, `created_at` |
| Flyway Migration | `V{번호}__{설명}.sql` | `V1__create_demo_schema.sql` |

- Domain 기준 Package 아래에서 Controller, Service, Repository 책임을 분리합니다.
- Controller는 요청·응답 변환, Service는 비즈니스 규칙, Repository는 영속성을 담당합니다.
- Entity를 API 응답으로 직접 반환하지 않고 DTO 또는 API Model로 변환합니다.
- REST Path, Status Code, STOMP Destination, Message Payload는 승인된 명세와 일치해야 합니다.
- Database 구조는 승인된 ERD와 Flyway Migration을 기준으로 관리합니다.
- Mock AI Provider 변경이나 Prompt/JSON Schema 변경은 승인된 AI 계약을 먼저 확인합니다.
- 코드, 설정, 로그, 테스트 Fixture에 Secret, Prompt 원문 중 민감정보, 개인정보를 남기지 않습니다.

## 실행과 검증

### 전체 서비스 실행

일반 실행과 데모용 실행은 프로젝트 루트의 Docker Compose를 사용합니다.

```bash
docker compose up -d --build
```

일반 접속 주소는 `http://localhost:8081`입니다. 데모에서는 같은 네트워크의 기기에서 `http://<HOST_LAN_IP>:8081`로 접속합니다. Frontend Nginx가 `/api`와 `/ws` 요청을 Backend Container로 프록시합니다.

루트 `compose.yaml`은 로컬 LAN 데모를 위해 `APP_CORS_ALLOWED_ORIGINS=*`를 사용합니다. 운영 또는 외부 배포 환경에서는 모든 Origin 허용을 절대 사용하지 않고 실제 Frontend Origin만 명시합니다.

종료:

```bash
docker compose down
```

### Backend 개발 실행

PostgreSQL만 Docker로 실행하고 Backend는 로컬에서 실행합니다.

```bash
docker compose up -d postgres
cd backend
cp .env.example .env
set -a
source .env
set +a
./gradlew bootRun
```

Swagger UI는 Backend 실행 후 `http://localhost:8080/swagger-ui.html`에서 확인합니다.

Backend 검증:

```bash
./gradlew test
./gradlew check
```

## 주요 설정

| 항목 | 기본값 | 설명 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Spring Boot 실행 Port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/webmini` | PostgreSQL JDBC URL |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | REST와 STOMP 허용 Origin |
| `APP_DEMO_USER_HEADER_NAME` | `X-Demo-User-Id` | Demo 사용자 식별 Header |
| `APP_WEBSOCKET_ENDPOINT` | `/ws` | STOMP Handshake Endpoint |
| `APP_AI_PROVIDER` | `mock` | `mock` 또는 `langchain`; 실제 모드 실패 시 Mock으로 전환하지 않음 |
| `APP_AI_BASE_URL` | `http://ai-service:8000` | LangChain AI 서비스 주소 |
| `APP_AI_TIMEOUT_SECONDS` | `60` | AI HTTP 응답 대기 시간 |
| `APP_TRIAL_DEBATE_TURNS` | `4` | 공방 발언 수: 공정한 순서를 위해 4 또는 8 |
| `APP_TRIAL_DEBATE_INTERVAL_SECONDS` | `8` | 생성된 발언을 공개한 뒤 다음 발언까지 읽는 시간 |

Spring Boot는 `.env` 파일을 자동으로 읽지 않습니다. 로컬 실행 시 Shell, IDE Run Configuration, Docker Compose 중 하나를 통해 환경변수로 전달합니다.

## AI 공방과 복구 계약

실제 AI 모드는 `/lawyer/questions`, `/lawyer/argument`, `/lawyer/debate`, `/judge/verdict`를 `APP_AI_BASE_URL`로 호출합니다. 공방 입력에는 양측 원진술과 확정 변론, 앞서 공개된 발언을 포함합니다. 기본 발언 순서는 A, B, B, A이며, 각 발언은 생성·검증·DB 저장 후에만 공개됩니다. 생성 중이거나 실패한 상태에서는 시간이 지나도 다음 발언, 투표, 판결로 넘어가지 않습니다. `scheduledEndAt`은 생성 시간을 제외한 예상 시각이며, 실제 진행 여부는 `status`와 `phaseEndsAt`을 따릅니다.

`GET /api/v1/trials/{trialId}/snapshot`은 `aiProvider`, `generationStatus` (`IDLE`, `GENERATING`, `FAILED`), `generationStage`, `generationTurn`, `totalDebateTurns`, `nextSpeaker`, `generationError`, `retryable`을 제공합니다. `GENERATION_STARTED`와 `GENERATION_FAILED` 이벤트에는 단계·발언 번호·생성 상태를 담습니다. `A_DEBATE`/`B_DEBATE` 이벤트는 저장한 발언과 다음 공개 시각을 담습니다. 연결이 끊겼다면 스냅샷과 이벤트 순번으로 복구합니다.

실패한 작업은 재판 생성자가 `X-Demo-User-Id` 헤더로 `POST /api/v1/trials/{trialId}/ai/retry`를 호출해 재시도합니다. 진행 중 요청 식별자를 DB에 기록하고, 재시작 후 오래된 요청은 실패로 전환합니다. 오래된 요청의 뒤늦은 응답은 저장하지 않습니다. 판결의 `aFaultRatio`와 `bFaultRatio`는 각각 0~100이고 합계는 100이며, `winnerSide`는 결론이 동률이거나 판정이 유보되면 `null`일 수 있습니다.

## STOMP 경로

| 구분 | 경로 |
| --- | --- |
| Handshake Endpoint | `/ws` |
| Application Prefix | `/app` |
| Broker Prefix | `/topic` |
| User Destination Prefix | `/user` |
| 개인 오류 Queue | `/user/queue/errors` |

Demo 사용자 식별값은 REST 요청 Header와 STOMP `CONNECT` Header의 `X-Demo-User-Id`로 전달합니다.
