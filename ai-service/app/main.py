from fastapi import FastAPI, HTTPException

from .models import (
    ArgumentRequest,
    ArgumentResponse,
    DebateRequest,
    DebateResponse,
    QuestionsRequest,
    QuestionsResponse,
    VerdictRequest,
    VerdictResponse,
)
from .service import AiService, LangChainGateway, ModelGateway, ModelUnavailable, InvalidGeneration


# 운영 앱은 실제 모델을 사용하고, 테스트만 gateway를 주입해 외부 호출 없이 검증한다.
def create_app(gateway: ModelGateway | None = None) -> FastAPI:
    app = FastAPI(title="사랑과 전쟁터 AI service", version="1.0")

    def service() -> AiService:
        try:
            return AiService(gateway if gateway is not None else LangChainGateway())
        except ModelUnavailable as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from None

    # 설정 누락은 503, 모델 호출·응답 검증 실패는 502로 반환한다. Mock으로 대체하지 않는다.
    def run(operation):
        try:
            return operation()
        except ModelUnavailable as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from None
        except InvalidGeneration:
            raise HTTPException(status_code=502, detail="AI model generation failed") from None

    # 프로세스 생존 확인용이다. 이 응답만으로 API 키나 모델 호출 성공을 판단할 수 없다.
    @app.get("/health")
    def health():
        return {"status": "ok"}

    @app.post("/lawyer/questions", response_model=QuestionsResponse)
    def questions(request: QuestionsRequest):
        return run(lambda: service().questions(request))

    @app.post("/lawyer/argument", response_model=ArgumentResponse)
    def argument(request: ArgumentRequest):
        return run(lambda: service().argument(request))

    @app.post("/lawyer/debate", response_model=DebateResponse)
    def debate(request: DebateRequest):
        return run(lambda: service().debate(request))

    @app.post("/judge/verdict", response_model=VerdictResponse)
    def verdict(request: VerdictRequest):
        return run(lambda: service().verdict(request))

    return app


app = create_app()
