from typing import Literal

from pydantic import BaseModel, Field, ConfigDict, model_validator


Side = Literal["A", "B"]
SCHEMA_VERSION = "1.0"


class ContractModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class StatementPayload(ContractModel):
    incidentTime: str = Field(min_length=1, max_length=4000)
    situation: str = Field(min_length=1, max_length=4000)
    counterpartAction: str = Field(min_length=1, max_length=4000)
    ownAction: str = Field(min_length=1, max_length=4000)
    afterConversation: str = Field(min_length=1, max_length=4000)
    desiredResolution: str = Field(min_length=1, max_length=4000)


class GuideQuestion(ContractModel):
    sequence: int
    question: str = Field(min_length=1, max_length=1000)


class GuideAnswer(GuideQuestion):
    answer: str = Field(max_length=4000)


class QuestionsRequest(ContractModel):
    trialId: int = Field(gt=0)
    side: Side
    relationshipType: str = Field(min_length=1, max_length=100)
    statement: StatementPayload


class QuestionsResponse(ContractModel):
    questions: list[GuideQuestion]
    schemaVersion: Literal["1.0"] = SCHEMA_VERSION

    @model_validator(mode="after")
    def validate_sequences(self):
        if len(self.questions) != 3 or [q.sequence for q in self.questions] != [1, 2, 3]:
            raise ValueError("exactly three numbered questions are required")
        return self


class ArgumentRequest(ContractModel):
    trialId: int = Field(gt=0)
    side: Side
    statement: StatementPayload
    guideAnswers: list[GuideAnswer] = Field(max_length=10)


class ArgumentResponse(ContractModel):
    factSummary: str = Field(min_length=1)
    argumentText: str = Field(min_length=1)
    schemaVersion: Literal["1.0"] = SCHEMA_VERSION


class DebateTurn(ContractModel):
    side: Side
    content: str = Field(min_length=1, max_length=6000)


class DebateRequest(ContractModel):
    trialId: int = Field(gt=0)
    turn: int = Field(ge=1, le=20)
    totalTurns: int = Field(ge=1, le=20)
    side: Side
    postContent: str = Field(min_length=1, max_length=8000)
    statements: dict[Side, StatementPayload]
    arguments: dict[Side, str]
    previousTurns: list[DebateTurn] = Field(max_length=19)
    promptVersion: str = Field(min_length=1, max_length=100)

    @model_validator(mode="after")
    def validate_context(self):
        if set(self.statements) != {"A", "B"} or set(self.arguments) != {"A", "B"}:
            raise ValueError("both sides are required")
        if any(not value.strip() or len(value) > 8000 for value in self.arguments.values()):
            raise ValueError("both confirmed arguments are required")
        if self.turn > self.totalTurns or len(self.previousTurns) != self.turn - 1:
            raise ValueError("turn must follow previous turns")
        return self


class DebateResponse(ContractModel):
    content: str = Field(min_length=1)
    schemaVersion: Literal["1.0"] = SCHEMA_VERSION
    promptVersion: str


class RecommendationPair(ContractModel):
    a: str = Field(min_length=1)
    b: str = Field(min_length=1)


class VerdictRequest(ContractModel):
    trialId: int = Field(gt=0)
    postSummary: str = Field(min_length=1, max_length=8000)
    statements: dict[Side, StatementPayload]
    arguments: dict[Side, str]
    debateTurns: list[DebateTurn] = Field(max_length=20)
    promptVersion: str = Field(min_length=1, max_length=100)

    @model_validator(mode="after")
    def validate_context(self):
        if set(self.statements) != {"A", "B"} or set(self.arguments) != {"A", "B"}:
            raise ValueError("both sides are required")
        if any(not value.strip() or len(value) > 8000 for value in self.arguments.values()):
            raise ValueError("both confirmed arguments are required")
        return self


# API로 반환하기 전에 책임 비율과 근거의 최소 형식을 검증한다. 승자는 없어도 된다.
class VerdictResponse(ContractModel):
    winnerSide: Side | None
    aFaultRatio: int = Field(ge=0, le=100)
    bFaultRatio: int = Field(ge=0, le=100)
    summary: str = Field(min_length=30)
    grounds: list[str] = Field(min_length=3)
    recommendations: RecommendationPair
    schemaVersion: Literal["1.0"] = SCHEMA_VERSION
    promptVersion: str

    @model_validator(mode="after")
    def validate_ratios(self):
        # 60:40 같은 고정값을 넣지 않고 모델이 생성한 비율의 합계만 검사한다.
        if self.aFaultRatio + self.bFaultRatio != 100:
            raise ValueError("fault ratios must total 100")
        if len(self.summary.strip()) < 30 or any(len(ground.strip()) < 20 for ground in self.grounds):
            raise ValueError("verdict text is too short")
        return self


class QuestionsGeneration(BaseModel):
    """Exactly three neutral follow-up questions for one party."""
    questions: list[str] = Field(description="Three distinct Korean questions in order")


class ArgumentGeneration(BaseModel):
    """A statement-based fact summary and initial argument."""
    factSummary: str
    argumentText: str


class DebateGeneration(BaseModel):
    """One lawyer's next debate statement."""
    content: str


# 모델에 전달할 출력 스키마다. 생성 후 VerdictResponse에서 합계·문장 길이를 추가 검사한다.
class VerdictGeneration(BaseModel):
    """A balanced relationship-conflict opinion based on the complete record."""
    winnerSide: Side | None = Field(description="A, B, or null for no clear winner")
    aFaultRatio: int = Field(ge=0, le=100)
    bFaultRatio: int = Field(ge=0, le=100)
    summary: str
    grounds: list[str]
    recommendations: RecommendationPair
