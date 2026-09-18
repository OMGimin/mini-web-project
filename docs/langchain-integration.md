# 진행 중 AI 공방 연결

## 목표와 책임

사용자가 양측 최초 변론문을 확인한 뒤 재판을 시작하고, 각 공방을 그 시점에 생성·저장·공개합니다. 공방을 모두 사전 생성해 재생하는 방식이 아닙니다. Python은 원진술·확정 변론·이전 공방을 받아 구조화된 응답을 생성합니다. Spring은 저장과 재판 진행을 담당합니다.

```text
Vue → Spring REST/STOMP → PostgreSQL
                ↓
       FastAPI + LangChain → 설정한 모델
```

최종 의견에는 A/B 책임 비율(각각 0~100, 합계 100), 판단 근거, 양측의 실천 제안이 포함됩니다. 승자 표시는 선택 항목입니다. 책임 비율은 제출된 기록에 대한 AI 의견이며, 객관적 측정값이나 법적 과실 비율이 아닙니다. 관전자 투표는 모델의 판단 입력에 넣지 않습니다.

## 생성과 진행

1. 양측 원진술을 저장하고 최초 변론문을 생성합니다. 사용자가 수정·확정합니다.
2. 재판 소개와 양측 최초 변론을 공개합니다.
3. A의 반박 → B의 답변 → B의 쟁점 제기 → A의 답변 순으로 진행 중 생성합니다.
4. 각 발언을 검증·저장한 뒤 공개하고 읽기 시간을 시작합니다. 생성 중에는 다음 단계로 넘어가지 않습니다.
5. 모든 공방과 마지막 읽기 시간이 끝난 뒤 투표를 엽니다.
6. 투표 시간이 끝나면 원진술·확정 변론·전체 공방으로 최종 의견을 생성합니다.
7. 최종 의견이 검증·저장된 뒤 결과를 공개합니다.

생성 상태는 `IDLE`, `GENERATING`, `FAILED`입니다. 실패를 고정 문장으로 대체하지 않습니다. 실패한 단계는 재판 생성자가 재시도할 수 있습니다. 생성 요청 식별값을 확인해 늦게 도착한 이전 요청의 결과가 덮어쓰지 않도록 합니다. 진행 상태와 공방은 DB에 저장되며 화면은 snapshot과 이벤트로 복구합니다.

## 모델을 호출하지 않는 기본 실행

```bash
docker compose up -d --build
```

기본 `APP_AI_PROVIDER=mock`은 개발 흐름을 확인하는 모의 응답입니다. Mock 결과는 실제 모델 품질 검증으로 보지 않습니다.

## 실제 모델 연결 설정

실제 호출은 과금될 수 있으므로 이번 검증에서는 실행하지 않습니다. 추후 사용 모델과 비용 범위를 정한 후 적용합니다. 키를 소스나 채팅에 붙여넣지 않고 로컬 환경변수로 전달합니다.

필요한 환경변수:

- `APP_AI_PROVIDER=langchain`
- `OPENAI_API_KEY`: 사용할 계정의 키
- `OPENAI_MODEL`: 계정에서 사용 가능한 모델 이름, 명시 설정 필요

위 환경변수를 설정한 터미널에서:

```bash
docker compose --profile ai up -d --build
```

AI 서비스는 내부 네트워크에만 노출됩니다. `/health`는 프로세스가 실행 중인지 확인하며 모델 호출 가능성을 보증하지 않습니다. 모델·키가 없으면 생성 요청은 실패합니다. AI 응답 제한은 45초, Spring의 HTTP 제한은 60초이며 프런트엔드 생성 요청에는 별도 대기 제한이 적용됩니다.

기존 공개 Demo 사용자 식별 방식은 실제 인증이 아닙니다. 이 작업은 로컬 수업 시연 범위이며 인터넷 공개 운영 전환을 포함하지 않습니다.

## 과금 없는 연결 검증

`ai-service/tests/fixture_server.py`는 가짜 모델을 주입한 테스트 전용 FastAPI 앱입니다. 제품 Docker 이미지에는 포함되지 않습니다. 이를 이용하면 Spring의 실제 HTTP 연결, 저장, 상태 전이, 오류 복구를 외부 모델 호출 없이 검사할 수 있습니다.

별도 테스트 DB를 사용하고 아래처럼 각 터미널에서 실행합니다.

```bash
# ai-service/ (의존성을 설치한 가상환경)
python -m uvicorn tests.fixture_server:app --host 127.0.0.1 --port 18001
```

```bash
# backend/: DB_URL/DB_USERNAME/DB_PASSWORD는 별도 테스트 DB 값으로 설정
APP_AI_PROVIDER=langchain APP_AI_BASE_URL=http://127.0.0.1:18001 \
SERVER_PORT=18080 APP_TRIAL_INTRODUCTION_SECONDS=1 \
APP_TRIAL_ARGUMENT_SECONDS=1 APP_TRIAL_DEBATE_INTERVAL_SECONDS=1 \
APP_TRIAL_VOTING_SECONDS=3 ./gradlew bootRun
```

```bash
# 저장소 루트: 가상 사건을 생성하므로 공유·운영 DB에서는 실행하지 않음
python3 scripts/verify-live-ai.py
python3 scripts/verify-live-ai.py --expect-failure
python3 scripts/verify-live-ai.py --delay-seconds 10
python3 scripts/verify-live-ai.py --fail-verdict
```

두 번째 검증은 테스트 모델에 실패를 주입합니다. 실패 후 자동 진행하지 않는지 확인하고 생성자 권한으로 재시도합니다. 실제 모델의 사건 이해·편향·책임 비율 적절성을 평가하는 검사는 아닙니다.

## 검증 구분

- 자동 테스트: 출력 스키마, 입력 분리, 상태 전이, 저장과 복구, 오류 처리.
- 실제 모델 평가: 사건 관련성, 반박의 적절성, 없는 사실의 추가, 비율과 근거의 일치, A/B 교환 시 편향.
- 실제 모델 평가는 과금 호출 승인 전까지 미실행입니다.

준비 단계 중복 호출 억제는 단일 Spring 프로세스 범위입니다. 여러 Backend 인스턴스로 확장하는 운영 설계는 포함하지 않습니다. 생성 중 프로세스가 중단된 경우 120초 이후 실패 상태로 복구해 생성자가 다시 시도하며, 외부 모델 서버가 이미 처리한 호출의 과금까지 취소하는 기능은 아닙니다.
