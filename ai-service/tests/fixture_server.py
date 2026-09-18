"""Test-only FastAPI server. Run with: uvicorn tests.fixture_server:app --port 8001."""

import time

from fastapi import FastAPI
from pydantic import BaseModel, Field

from app.main import create_app
from app.models import (
    ArgumentGeneration,
    DebateGeneration,
    QuestionsGeneration,
    VerdictGeneration,
)


class FakeGateway:
    def __init__(self):
        self.fail_next = 0
        self.delay_next_seconds = 0.0
        self.calls: list[dict] = []

    def generate(self, schema, system: str, facts: dict):
        if self.delay_next_seconds:
            delay = self.delay_next_seconds
            self.delay_next_seconds = 0.0
            time.sleep(delay)
        if self.fail_next:
            self.fail_next -= 1
            raise RuntimeError("injected test failure")
        self.calls.append({"schema": schema.__name__, "facts": facts})
        if schema is QuestionsGeneration:
            return QuestionsGeneration(questions=[
                "당시 합의한 기준이 있었나요?",
                "그 행동을 선택한 이유는 무엇인가요?",
                "상대에게 바라는 구체적 변화는 무엇인가요?",
            ])
        if schema is ArgumentGeneration:
            return ArgumentGeneration(
                factSummary=f"{facts['side']}측 진술: {facts['statement']['situation']}",
                argumentText=f"{facts['side']}측은 {facts['statement']['desiredResolution']}을 요청합니다.",
            )
        if schema is DebateGeneration:
            previous = facts["previousTurns"]
            reference = previous[-1]["content"] if previous else facts["arguments"]["B" if facts["side"] == "A" else "A"]
            return DebateGeneration(content=f"{facts['side']}측 {facts['turn']}차 발언: {reference}에 관해 답변합니다.")
        if schema is VerdictGeneration:
            return VerdictGeneration(
                winnerSide=None,
                aFaultRatio=50,
                bFaultRatio=50,
                summary="양측의 원진술과 확정 변론, 이어진 공방을 함께 검토했으며 일방의 승소를 정하기 어렵습니다.",
                grounds=[
                    "A측의 원진술과 확정 변론에 포함된 사정을 검토했습니다.",
                    "B측의 원진술과 확정 변론에 포함된 사정을 검토했습니다.",
                    "순차 공방에서 제기된 설명과 반론을 함께 검토했습니다.",
                ],
                recommendations={"a": "상대와 대화 기준을 정하세요.", "b": "상대의 설명을 확인하세요."},
            )
        raise AssertionError(f"unexpected schema: {schema.__name__}")


gateway = FakeGateway()
app: FastAPI = create_app(gateway)


class FailNext(BaseModel):
    count: int = Field(default=1, ge=0, le=10)


class DelayNext(BaseModel):
    seconds: float = Field(ge=0, le=10)


@app.post("/__test__/fail-next")
def fail_next(request: FailNext):
    gateway.fail_next = request.count
    return {"remaining": gateway.fail_next}


@app.post("/__test__/delay-next")
def delay_next(request: DelayNext):
    gateway.delay_next_seconds = request.seconds
    return {"seconds": gateway.delay_next_seconds}


@app.get("/__test__/calls")
def calls():
    return gateway.calls


@app.post("/__test__/reset")
def reset():
    gateway.calls.clear()
    gateway.fail_next = 0
    gateway.delay_next_seconds = 0.0
    return {"status": "ok"}
