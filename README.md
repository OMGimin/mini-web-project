# 사랑과 전쟁터

> LangChain 확장 작업: 진행 중 공방 생성과 사건별 책임 비율을 위한 구성·검증 방법은
> [LangChain 연결 가이드](docs/langchain-integration.md)를 참고하세요.
> 아래 원래 팀 프로젝트 설명에는 확장 이전의 Mock AI·고정 시간 설계 기록이 남아 있습니다. 현재 진행 규칙은 연결 가이드를 기준으로 확인하세요.
> 기본 실행은 `mock`이며 실제 모델 연결은 명시적으로 선택합니다.

SKALA Full-Stack Engineering 과정의 6조 AI-Ready 웹 서비스 설계 프로젝트입니다.

연인 간 갈등 당사자가 각자의 입장을 정리하고, Mock AI 변호사와 Mock AI 판사의 의견 및 관전자 투표를 참고해 해결 방향을 탐색하는 서비스입니다. 실제 법률 판결이나 법률 상담을 제공하지 않습니다.

현재 저장소에는 팀 개발을 위한 문서, 협업 규칙, 편집기 설정, Justice & Empathy 디자인 시스템, Frontend·Backend 프로젝트, PostgreSQL Schema, Docker Compose와 Local Live Demo 기능 코드가 구성되어 있습니다.

## 범위와 구현 상태

실제 구현 완료 여부는 Repository 코드와 실행된 검증 결과를 기준으로 판단합니다. 제품 범위와 정책은 Notion, 담당자와 작업 상태는 GitHub Project를 따릅니다.

### Demo 개발 대상

이번 Local Live Demo는 공개 재판의 다음 핵심 흐름을 개발 대상으로 합니다.

- 갈등 게시글과 공개 재판 생성
- 한 화면 흐름에서 A측 작성 후 B측 작성
- Mock AI 안내 질문, 답변, 사실관계 요약과 변론문 생성
- Backend 시간 기준 재판 단계 전이와 STOMP Event 전달
- 이전 채팅 조회, 원본 채팅 저장과 실시간 채팅
- 관전자당 한 번의 승소 투표
- 양측 변론을 사용한 Mock AI 판결 생성과 결과 저장
- AI 판결과 대중 투표 결과의 분리 표시

Demo에서는 실제 로그인 대신 Browser별 Demo 사용자 식별값을 사용하고 공개 재판만 동작시킵니다.

### Frontend만 개발

다음 일반 커뮤니티 기능은 Frontend 화면과 로컬 Mock 데이터로만 구현합니다. Backend API, Database 저장, 실제 인증과 권한 처리는 이번 Demo 범위가 아닙니다.

- 홈·인기 게시글·Live 재판·커뮤니티 가이드라인 탭
- 게시글 목록·상세·작성·수정·삭제 화면
- 관계 유형·갈등 사유 필터와 페이지 선택
- 댓글·답글·좋아요·신고 화면 및 로컬 상호작용

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| Design | Google Stitch, Figma, Justice & Empathy Design System |
| Frontend | Vue 3, Vite, JavaScript, Vue Router, Tailwind CSS 4, shadcn-vue, lucide-vue |
| Frontend API | Axios, REST API Client, STOMP Client |
| Backend | Java 21, Spring Boot 4, Gradle, Spring Web, Bean Validation |
| Backend Realtime | Spring WebSocket/STOMP, Topic Broker, User Queue |
| Database | PostgreSQL 17, Spring Data JPA, Flyway |
| AI | Mock AI Client, Mock AI Lawyer, Mock AI Judge |
| API Docs | springdoc-openapi, Swagger UI |
| Infra | Docker Compose, Nginx Reverse Proxy |
| Future Ops/AI (추후 개발) | AWS, Amazon API Gateway, Amazon EC2, Amazon RDS, GitHub Actions, Amazon Bedrock Agents, Bedrock Knowledge Base, Amazon S3, OpenSearch Serverless |
| Collaboration | Notion, GitHub Project, GitHub Issues, Pull Requests |

`Future Ops/AI`는 현재 구현 범위가 아니라 추후 개발 계획입니다. 세부 내용은 [프로젝트 한계점 및 추후 AI 실제 결합 로드맵](docs/ai-roadmap.md)을 기준으로 확인합니다.

## 디렉토리 구조

빌드 산출물인 `frontend/dist/`, `backend/build/`, `backend/.gradle/`, 의존성 디렉토리인 `node_modules/`는 구조 설명에서 제외합니다.

```text
.
├── frontend/                         # Vue 3 + Vite Frontend 애플리케이션
│   ├── public/                       # 정적 공개 파일
│   │   └── images/                   # 화면에서 직접 사용하는 이미지 자산
│   └── src/
│       ├── apis/                     # REST API 요청 함수
│       ├── app/                      # 앱 진입점, 최상위 App, Router
│       │   └── router/               # Vue Router 설정
│       ├── assets/                   # Frontend 전용 스타일 자산
│       │   └── styles/               # Justice & Empathy 토큰, 폰트, Global CSS
│       ├── components/               # 재사용 UI 컴포넌트
│       │   ├── chat/                 # 재판 채팅 UI
│       │   ├── common/               # Header, Footer 등 공통 레이아웃
│       │   ├── community/            # 커뮤니티 목록·게시글 UI
│       │   ├── trial/                # 재판 준비·진행 UI
│       │   ├── ui/                   # Tailwind CSS + shadcn-vue UI Primitive
│       │   ├── verdict/              # AI 판결 결과 UI
│       │   └── vote/                 # 관전자 투표 UI
│       ├── composables/              # Vue Composition API 기반 상태·흐름 로직
│       ├── consts/                   # API, 상태, 메시지, STOMP 상수
│       ├── lib/                      # HTTP Client, Realtime Client, 공통 유틸
│       ├── mock/                     # Frontend-only 화면 검증용 Mock 데이터
│       │   ├── community/
│       │   ├── trial/
│       │   ├── verdict/
│       │   └── vote/
│       ├── pages/                    # Router 단위 화면
│       │   ├── community/
│       │   ├── integration/
│       │   ├── live-trial/
│       │   ├── trial-preparation/
│       │   └── trial-result/
│       ├── stores/                   # 화면 상태 Store
│       └── utils/                    # 순수 유틸 함수
├── backend/                          # Java 21 + Spring Boot Backend 애플리케이션
│   ├── config/
│   │   └── checkstyle/               # Java Code Style 검증 규칙
│   ├── gradle/
│   │   └── wrapper/                  # Gradle Wrapper
│   └── src/
│       ├── main/
│       │   ├── java/com/skala/team6/webmini/
│       │   │   ├── ai/               # Mock AI Client, 변론·판결 Service, AI API
│       │   │   ├── common/           # 공통 응답, 설정, 예외, Enum
│       │   │   │   ├── api/
│       │   │   │   ├── config/
│       │   │   │   ├── exception/
│       │   │   │   └── model/
│       │   │   ├── database/         # JPA Entity와 Repository
│       │   │   │   ├── entity/
│       │   │   │   └── repository/
│       │   │   ├── demo/             # Demo 사용자 식별과 저장
│       │   │   ├── post/             # 게시글 REST API와 Service
│       │   │   ├── trial/            # 재판 생성, 진행, 채팅, 투표, 결과, STOMP
│       │   │   └── websocket/        # STOMP 인증·오류·Presence 처리
│       │   └── resources/
│       │       ├── db/
│       │       │   └── migration/    # Flyway Database Migration
│       │       └── static/           # 로컬 STOMP 테스트 페이지
│       └── test/
│           └── java/com/skala/team6/webmini/
│               ├── ai/               # AI Service·Controller Test
│               ├── database/         # Migration·Persistence Test
│               ├── post/             # 게시글 Acceptance Test
│               ├── trial/            # 재판 흐름·채팅·투표 Test
│               └── websocket/        # STOMP Interceptor·Error Test
├── docs/                             # 승인된 설계 문서와 정적 가이드 자산
│   ├── ai-roadmap.md                 # 운영 한계와 Bedrock 기반 AI 결합 로드맵
│   ├── ai/
│   │   └── prompts/                  # Mock AI Prompt 계약
│   ├── assets/
│   │   └── github-guide/             # Notion에서 참조하는 GitHub 가이드 이미지
│   └── user-flow.md                  # Actor, Use Case, 유저플로우 노드·엣지
└── .vscode/                          # 팀 공통 VS Code 설정
```

### 디자인 시스템

Justice & Empathy 디자인 시스템은 Frontend 시각 기준입니다.

- Tailwind CSS를 중심으로 색상, Typography, Radius, Spacing Token을 관리합니다.
- Token과 전역 스타일은 `frontend/src/assets/styles/`에 둡니다.
- UI Primitive는 Tailwind CSS와 shadcn-vue 구조를 사용하며 `frontend/src/components/ui/`에 둡니다.
- Variant 관리는 `class-variance-authority`, Class 병합은 `clsx`, `tailwind-merge`를 사용합니다.

### 역할 경계

| 영역         | 책임                                                  | 변경 기준 Branch |
| ------------ | ----------------------------------------------------- | ---------------- |
| `frontend/`  | 화면, Router, REST 호출, STOMP Client, 화면 상태 관리 | `frontend`       |
| `frontend/src/assets/styles/` | Justice & Empathy 디자인 토큰과 전역 스타일           | `frontend`       |
| `frontend/src/components/ui/` | Tailwind CSS와 shadcn-vue 기반 UI Primitive           | `frontend`       |
| `backend/`   | REST API, STOMP, Domain Service, DB, Mock AI Adapter   | `backend`        |
| `docs/`      | 승인된 API, ERD, Database, STOMP, AI 계약 문서         | 작업 성격에 따름 |
| `.vscode/`   | 팀 공통 편집기 설정                                   | `dev` 기준 협의  |
| 루트 설정 파일 | Git, Docker Compose, Repository 협업 규칙             | `dev` 기준 협의  |

## 실행 방법

실제 AI를 사용하는 **Docker Compose + GPT-5.6 Luna** 실행을 기준으로 합니다. Docker Desktop을 실행한 뒤, 터미널에서 이 저장소의 `compose.yaml`이 있는 폴더를 엽니다. Java·Node.js·Python은 호스트에 별도로 설치하지 않아도 되며, 최초 빌드에는 이미지와 의존성을 내려받기 위한 인터넷 연결이 필요합니다.

### 1. API 키와 모델 설정

아래 명령은 **macOS 기본 셸인 zsh 기준**입니다. 첫 줄을 실행하면 키 입력을 기다립니다. 발급받은 OpenAI API 키를 붙여넣고 Enter를 누르세요. 입력한 키는 화면에 표시되지 않으며, 키 값이 들어간 명령을 셸 기록에 남기지 않습니다.

```zsh
read -rs "OPENAI_API_KEY?OpenAI API 키 입력: "
export OPENAI_API_KEY
export OPENAI_MODEL=gpt-5.6-luna
export APP_AI_PROVIDER=langchain
```

키 입력 후 나머지 명령을 실행합니다. 키에 연결된 프로젝트의 사용 가능한 API 잔액과 모델 권한이 필요합니다. 실제 모델 호출은 과금됩니다. 키를 코드·문서·채팅에 붙여넣거나 커밋하지 않습니다.

`OPENAI_MODEL`을 명시해 이전에 설정한 모델이 그대로 사용되는 것을 방지합니다. `APP_AI_PROVIDER=langchain`도 필요하며, 이 설정이 없으면 Backend는 기본 모의 응답을 사용합니다.

### 2. 빌드하고 실행

**환경변수를 설정한 같은 터미널**에서 실행합니다.

```bash
docker compose --profile ai up -d --build
docker compose --profile ai ps
```

`postgres`, `backend`, `frontend`, `ai-service` 네 서비스가 실행 중인지 확인한 뒤 **http://localhost:8081**에 접속합니다. 시작 직후에는 Backend 준비에 시간이 걸릴 수 있습니다.

다음에 다시 실행할 때도 **1~2단계를 그대로 진행**하면 됩니다. 같은 터미널에 환경변수가 남아 있다면 키 재입력은 생략할 수 있지만, 새 터미널에서는 다시 설정해야 합니다. 코드나 모델 설정이 변경됐을 때도 위 실행 명령으로 반영합니다. 기존 화면을 열어둔 경우 브라우저를 새로고침하세요.

### 3. 새 사건으로 테스트

글쓰기에서 제목·내용·관계 유형을 입력하고 재판 신청을 선택합니다. 재판 제목·사건 요약과 양측 이름을 확인한 뒤, 각 측의 **기본 질문 6개 → 변론문 생성 → 확인·확정**을 진행합니다. 현재 테스트 흐름에서는 AI 추가 질문을 생략합니다.

마지막 확인 화면에서 재판을 시작하면 공방 4회와 투표를 거쳐 최종 결론·책임 비율이 생성됩니다. 기존 재판의 저장된 결과는 모델을 바꿔도 자동으로 다시 생성되지 않습니다.

### 4. 사용 후 종료

```bash
docker compose --profile ai stop
```

네 서비스를 중지하며 게시글·재판 등 DB 데이터는 보존합니다. 데이터 보존을 위해 볼륨 삭제 옵션인 `down -v`는 사용하지 마세요.

### 실행이 안 될 때

상태와 최근 로그를 확인합니다.

```bash
docker compose --profile ai ps --all
docker compose --profile ai logs --tail=80 ai-service backend
```

- Docker 연결 오류: Docker Desktop이 실행 중인지 확인합니다.
- 인증·모델 권한·잔액 오류: 키가 연결된 OpenAI 프로젝트의 설정을 확인합니다.
- 모의 응답 표시: `APP_AI_PROVIDER=langchain`을 설정한 같은 터미널에서 2단계 명령을 다시 실행합니다.
- AI 응답 실패: 오류를 확인한 뒤 화면의 재시도 기능을 사용합니다. 재시도도 비용이 발생할 수 있습니다.

현재 구성은 로컬 테스트용입니다. 외부 배포 시에는 별도의 인증·네트워크 설정 검토가 필요합니다. 내부 연결 구조는 [LangChain 연결 가이드](docs/langchain-integration.md), 이전 검증 범위는 [검증 기록](docs/langchain-verification.md)을 참고하세요. 2026년 9월 21일 GPT-5.6 Luna의 실제 API 연결과 가상 사건 2건의 전체 흐름을 확인했습니다. FastAPI 생성 요청 14회(변론 4회·공방 8회·최종 의견 2회)가 모두 성공했고, 사건별 책임 비율은 45:55와 10:90으로 달라졌으며 각 사건에 따른 판단 근거와 개선 의견이 출력됐습니다. 각 사례를 1회씩 실행한 결과이므로, 반복 실행 시 일관성과 편향은 추가 검증이 필요합니다.

## 협업 흐름

1. 최신 `dev`에서 영역 통합 Branch인 `frontend`와 `backend`를 분기하고 최신 상태를 유지합니다.
2. Issue 하나에 Branch 하나와 PR 하나를 연결합니다.
3. Frontend와 Backend 작업은 Task, Branch, Commit, PR을 분리합니다.
4. Frontend 작업 Branch는 최신 `frontend`에서, Backend 작업 Branch는 최신 `backend`에서 생성합니다.
5. Frontend 작업 PR은 `frontend`로, Backend 작업 PR은 `backend`로 생성합니다.
6. `frontend`와 `backend`에서 검증된 변경을 `dev`로 반영합니다.
7. 최종 완료 후 `dev`에서 `main`으로 반영합니다.
8. 보호된 Branch에는 Review와 검증 후 Squash and merge합니다.

초기 Repository 부트스트랩 Issue는 위 협업 규칙 확정 전에 직접 반영되었습니다. 이후 기능 작업부터 Issue·Branch·PR 연결과 Review 규칙을 적용합니다.

## 협업 컨벤션

공용 협업 규칙은 이 문서를 기준으로 합니다. Frontend와 Backend의 세부 코드 작성 규칙, 디렉토리 역할, 실행·검증 방법은 각 파트 README를 따릅니다.

- Frontend 세부 규칙: [frontend/README.md](frontend/README.md)
- Backend 세부 규칙: [backend/README.md](backend/README.md)
- 문서 관리 규칙: [docs/README.md](docs/README.md)

### Issue

- 제목: `[영역] type: 한국어 작업명`
- 예시: `[FE] feat: 작업 등록 화면 구현`, `[BE] feat: 작업 등록 API 구현`
- Feature 아래의 Task는 한 사람이 1~3시간 안에 완료할 크기로 나눕니다.
- 한 Task에 Frontend와 Backend 작업을 함께 넣지 않습니다.
- 작업 내용, 완료 조건, 선행 작업, 담당자, Iteration, Priority와 Label을 기록합니다.

영역은 `FE`, `BE`, `DB`, `AI`, `INTEGRATION`, `DESIGN`, `DOCS`, `QA`, `COMMON`을 사용합니다.

### Branch

- 형식: `type/작업영역-이슈번호-영어-작업명`
- 기준 Branch:
  Frontend 작업은 `frontend`, Backend 작업은 `backend`, Integration 작업은 `dev`에서 분기합니다.
- Frontend: `feat/frontend-12-task-form`
- Backend: `feat/backend-13-task-api`
- 연동: `fix/integration-27-task-flow`
- 문서: `docs/api-8-spec`

### Commit

- 형식: `type(scope): 한국어 제목`
- `type`과 `scope`는 영어, 제목과 본문은 한국어로 작성합니다.
- scope는 생략하지 않습니다.

```text
feat(frontend): 작업 등록 폼 추가
feat(backend): 작업 등록 API 추가
test(integration): 작업 등록 흐름 검증
docs(docs): API 명세 갱신
chore(common): 공통 개발환경 구성
```

| type       | 용도                       |
| ---------- | -------------------------- |
| `feat`     | 기능 추가                  |
| `fix`      | 버그 수정                  |
| `refactor` | 기능 변경 없는 코드 개선   |
| `docs`     | 문서 작성 및 수정          |
| `test`     | 테스트 작성 및 수정        |
| `style`    | UI 스타일 또는 코드 Format |
| `chore`    | 설정과 개발환경 작업       |

scope는 `frontend`, `backend`, `database`, `ai`, `design`, `docs`, `qa`, `integration`, `common`을 사용합니다.

### Pull Request

- Frontend 작업은 작업 Branch에서 `frontend`로 PR을 생성합니다.
- Backend 작업은 작업 Branch에서 `backend`로 PR을 생성합니다.
- `frontend`와 `backend`는 영역 통합 Branch이며, 각 영역의 검증된 작업을 먼저 모읍니다.
- `frontend`와 `backend`에서 검증된 변경만 `dev`로 반영합니다.
- `main`, `dev`, `frontend`, `backend`는 GitHub 보호 규칙으로 최소 1명의 승인 Review를 요구합니다.
- 보호된 Branch는 강제 Push와 Branch 삭제를 허용하지 않습니다.
- 작업 Branch에서 `main`으로 직접 병합하지 않고 최종 완료 시점에 `dev`의 검증된 내용을 `main`에 반영합니다.
- 기본적으로 같은 R&R 영역의 동료에게 Review를 요청하고, 승인 후 작성자와 Reviewer가 함께 변경 범위와 검증 결과를 확인한 뒤 병합합니다.
- `Closes #이슈번호`를 작성합니다.
- Frontend와 Backend PR을 각각 영역 Branch에 병합한 뒤 별도 Integration Task와 PR로 `dev` 반영을 진행합니다.
- Review와 필요한 검증을 통과한 뒤 Squash and merge합니다.

### 공용 네이밍

| 대상                 | 규칙                         | 예시                         |
| -------------------- | ---------------------------- | ---------------------------- |
| 디렉터리             | kebab-case                   | `task-result`                |
| API Path             | 소문자 kebab-case, 복수 명사 | `/api/task-results`          |
| JSON 필드            | camelCase                    | `createdAt`                  |
| 환경변수             | UPPER_SNAKE_CASE             | `DB_PASSWORD`                |

파트별 네이밍과 코드 작성 규칙은 각 파트 README를 따릅니다.

## 현재 완료 범위

- Repository 협업 문서, Issue/PR 템플릿, VS Code 공통 설정
- Vue 3 + Vite Frontend, Java 21 + Spring Boot Backend
- Tailwind CSS, shadcn-vue, Justice & Empathy 디자인 시스템
- PostgreSQL Schema, Flyway, Docker Compose
- REST API, STOMP, Demo 사용자 식별, Mock AI 변론·판결 흐름
