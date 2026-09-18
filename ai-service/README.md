# AI 서비스

Spring Backend가 전달한 재판 기록으로 안내 질문, 최초 변론, 순차 공방, 최종 의견을 생성하는 내부 FastAPI 서비스입니다. 실제 법률 판결이나 법률 상담을 제공하지 않습니다. 상태와 데이터 저장은 Spring Backend가 담당합니다.

## 실행

Python 3.11 환경에서 `pip install -r requirements.lock`을 실행한 다음 `uvicorn app.main:app --host 0.0.0.0 --port 8000`으로 시작합니다. 실제 모델 호출에는 `OPENAI_API_KEY`와 `OPENAI_MODEL`을 명시해야 합니다. 호출 제한 시간은 `AI_REQUEST_TIMEOUT_SECONDS`(기본값 `45`)로 설정합니다. 두 필수 설정 중 하나라도 없으면 생성 API는 HTTP 503을 반환합니다. 외부 모델 오류나 응답 검증 실패는 HTTP 502를 반환하며 임의 결과로 대체하지 않습니다. `/health`는 설정과 무관하게 프로세스 상태만 확인합니다.

`requirements.txt`는 직접 의존성, `requirements.lock`은 Python 3.11에서 검증한 전체 의존성 버전입니다. Docker 이미지도 lock 파일을 사용합니다.

모델 연동은 LangChain의 프롬프트 체인과 [ChatOpenAI 구조화 출력 API](https://docs.langchain.com/oss/python/integrations/chat/openai#structured-output)를 사용합니다. 실제 호출은 사용량에 따른 비용이 발생할 수 있습니다.

## 내부 계약

모든 생성 API는 JSON 본문을 받고 응답은 Spring의 `ApiResponse` 래퍼 없이 반환합니다. 정상 응답의 `schemaVersion`은 `1.0`입니다.

| 경로 | 핵심 입력 | 핵심 출력 |
| --- | --- | --- |
| `POST /lawyer/questions` | `trialId`, `side`, `relationshipType`, `statement` | 순서 1~3의 `questions` |
| `POST /lawyer/argument` | `trialId`, `side`, `statement`, `guideAnswers`(빈 배열 가능) | `factSummary`, `argumentText` |
| `POST /lawyer/debate` | `trialId`, `turn`, `totalTurns`, `side`, `postContent`, 양측 `statements`·`arguments`, `previousTurns`, `promptVersion` | `content`, `schemaVersion`, `promptVersion` |
| `POST /judge/verdict` | `trialId`, `postSummary`, 양측 `statements`·`arguments`, `debateTurns`, `promptVersion` | `winnerSide`(A/B/null), 합계 100인 `aFaultRatio`·`bFaultRatio`, `summary`, `grounds`, `recommendations`, 버전 |

`statement`의 필드는 `incidentTime`, `situation`, `counterpartAction`, `ownAction`, `afterConversation`, `desiredResolution`입니다. `previousTurns`와 `debateTurns`의 각 항목은 `{ "side": "A", "content": "..." }` 형식입니다. 공방의 `turn`은 1부터 시작하며 `previousTurns`의 길이보다 하나 커야 합니다. 양측 `statements`와 `arguments`는 `{ "A": ..., "B": ... }` 형식입니다.

## 무료 계약 검증

`tests.fixture_server:app`은 주입형 가짜 모델을 사용하는 **테스트 전용 서버**입니다. `uvicorn tests.fixture_server:app --host 127.0.0.1 --port 8001`로 실행하면 동일 API 계약을 비용 없이 검증할 수 있습니다. `POST /__test__/fail-next`에 `{ "count": 1 }`을 보내 다음 생성 호출에 502를 주입하고, `POST /__test__/delay-next`에 `{ "seconds": 3 }`을 보내 다음 한 호출만 최대 10초 지연시킬 수 있습니다. `GET /__test__/calls`로 전달된 문맥을 검사할 수 있습니다. 이 경로들은 제품용 Docker 이미지에 포함되지 않습니다.
