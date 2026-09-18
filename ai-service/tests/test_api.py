from fastapi.testclient import TestClient
import time

from app.main import create_app
from app.models import VerdictGeneration
from app.service import LangChainGateway
from tests.fixture_server import FakeGateway, app as fixture_app


STATEMENT_A = {
    "incidentTime": "어제 저녁",
    "situation": "연락 문제로 다툼",
    "counterpartAction": "답장이 늦었음",
    "ownAction": "반복해서 연락함",
    "afterConversation": "대화가 중단됨",
    "desiredResolution": "연락 기준 합의",
}
STATEMENT_B = {
    "incidentTime": "어제 저녁",
    "situation": "연락 문제로 다툼",
    "counterpartAction": "반복 연락을 받음",
    "ownAction": "늦게 답함",
    "afterConversation": "대화가 중단됨",
    "desiredResolution": "서로의 시간 존중",
}
CONTEXT = {
    "statements": {"A": STATEMENT_A, "B": STATEMENT_B},
    "arguments": {"A": "A측의 확정 변론입니다.", "B": "B측의 확정 변론입니다."},
}


def test_complete_api_contract_with_injected_fake_model():
    gateway = FakeGateway()
    client = TestClient(create_app(gateway))
    assert client.get("/health").json() == {"status": "ok"}

    questions = client.post("/lawyer/questions", json={
        "trialId": 1, "side": "A", "relationshipType": "LOVER", "statement": STATEMENT_A,
    })
    assert questions.status_code == 200
    assert [item["sequence"] for item in questions.json()["questions"]] == [1, 2, 3]

    argument = client.post("/lawyer/argument", json={
        "trialId": 1, "side": "A", "statement": STATEMENT_A, "guideAnswers": [],
    })
    assert argument.status_code == 200
    assert argument.json()["schemaVersion"] == "1.0"

    debate = client.post("/lawyer/debate", json={
        "trialId": 1, "turn": 3, "totalTurns": 4, "side": "B", "postContent": "연락 갈등",
        **CONTEXT,
        "previousTurns": [
            {"side": "A", "content": "A의 첫 공방"},
            {"side": "B", "content": "B의 첫 공방"},
        ],
        "promptVersion": "lawyer-debate-v1",
    })
    assert debate.status_code == 200
    assert "B의 첫 공방" in debate.json()["content"]
    assert debate.json()["promptVersion"] == "lawyer-debate-v1"
    assert gateway.calls[-1]["facts"]["statements"] == CONTEXT["statements"]

    verdict = client.post("/judge/verdict", json={
        "trialId": 1, "postSummary": "연락 갈등", **CONTEXT,
        "debateTurns": [{"side": "A", "content": "A의 첫 공방"},
                        {"side": "B", "content": "B의 첫 공방"}],
        "promptVersion": "judge-v1",
    })
    assert verdict.status_code == 200
    assert verdict.json()["winnerSide"] is None
    assert verdict.json()["aFaultRatio"] + verdict.json()["bFaultRatio"] == 100
    assert len(verdict.json()["grounds"]) == 3
    assert len(gateway.calls) == 4
    assert gateway.calls[-1]["facts"]["debateTurns"][1]["content"] == "B의 첫 공방"


def test_missing_model_settings_return_503(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.delenv("OPENAI_MODEL", raising=False)
    client = TestClient(create_app())
    result = client.post("/lawyer/argument", json={
        "trialId": 1, "side": "A", "statement": STATEMENT_A, "guideAnswers": [],
    })
    assert result.status_code == 503
    assert "OPENAI_API_KEY" in result.json()["detail"]
    assert "OPENAI_MODEL" in result.json()["detail"]


def test_langchain_chain_builds_without_external_call(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-only-placeholder")
    monkeypatch.setenv("OPENAI_MODEL", "test-only-model")
    gateway = LangChainGateway()
    assert gateway.model.model_name == "test-only-model"
    assert gateway.prompt is not None


def test_failed_generation_is_502_without_fallback():
    gateway = FakeGateway()
    gateway.fail_next = 1
    client = TestClient(create_app(gateway))
    payload = {"trialId": 1, "side": "A", "statement": STATEMENT_A, "guideAnswers": []}
    result = client.post("/lawyer/argument", json=payload)
    assert result.status_code == 502
    assert "injected" not in result.text
    assert client.post("/lawyer/argument", json=payload).status_code == 200


def test_bad_model_ratio_is_rejected():
    class BadGateway(FakeGateway):
        def generate(self, schema, system, facts):
            if schema is VerdictGeneration:
                return VerdictGeneration(
                    winnerSide="A", aFaultRatio=80, bFaultRatio=80,
                    summary="양측의 진술과 변론 및 공방을 검토하여 각 책임을 판단했으며 결론에 한계가 있습니다.",
                    grounds=["A측 진술을 포함한 자료를 전체적으로 검토하였습니다.",
                             "B측 진술을 포함한 자료를 전체적으로 검토하였습니다.",
                             "양측 공방 발언을 포함한 자료를 전체적으로 검토하였습니다."],
                    recommendations={"a": "대화하세요.", "b": "대화하세요."},
                )
            return super().generate(schema, system, facts)

    client = TestClient(create_app(BadGateway()))
    result = client.post("/judge/verdict", json={
        "trialId": 1, "postSummary": "연락 갈등", **CONTEXT,
        "debateTurns": [], "promptVersion": "judge-v1",
    })
    assert result.status_code == 502


def test_debate_requires_complete_ordered_context():
    client = TestClient(create_app(FakeGateway()))
    result = client.post("/lawyer/debate", json={
        "trialId": 1, "turn": 3, "totalTurns": 4, "side": "B", "postContent": "연락 갈등",
        **CONTEXT, "previousTurns": [], "promptVersion": "lawyer-debate-v1",
    })
    assert result.status_code == 422


def test_fixture_can_delay_one_call_only():
    client = TestClient(fixture_app)
    client.post("/__test__/reset")
    assert client.post("/__test__/delay-next", json={"seconds": 0.05}).status_code == 200
    payload = {"trialId": 1, "side": "A", "statement": STATEMENT_A, "guideAnswers": []}
    started = time.monotonic()
    assert client.post("/lawyer/argument", json=payload).status_code == 200
    assert time.monotonic() - started >= 0.05
    assert client.post("/__test__/delay-next", json={"seconds": 10.1}).status_code == 422
    client.post("/__test__/reset")
