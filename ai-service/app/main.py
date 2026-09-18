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


def create_app(gateway: ModelGateway | None = None) -> FastAPI:
    app = FastAPI(title="사랑과 전쟁터 AI service", version="1.0")

    def service() -> AiService:
        try:
            return AiService(gateway if gateway is not None else LangChainGateway())
        except ModelUnavailable as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from None

    def run(operation):
        try:
            return operation()
        except ModelUnavailable as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from None
        except InvalidGeneration:
            raise HTTPException(status_code=502, detail="AI model generation failed") from None

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
