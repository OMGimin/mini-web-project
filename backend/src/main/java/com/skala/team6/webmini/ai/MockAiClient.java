package com.skala.team6.webmini.ai;

import com.skala.team6.webmini.common.config.AppAiProperties;
import com.skala.team6.webmini.common.model.TrialSide;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAiClient implements AiClient {

    private final AppAiProperties appAiProperties;

    public MockAiClient(AppAiProperties appAiProperties) {
        this.appAiProperties = appAiProperties;
    }

    @Override
    public LawyerQuestionsResponse createGuideQuestions(
            AiRequestContext context,
            LawyerQuestionsRequest request
    ) {
        String sideLabel = sideLabel(request.side());
        StatementPayload statement = request.statement();
        return new LawyerQuestionsResponse(
                List.of(
                        new GuideQuestionItem(
                                1,
                                "%s이 설명한 '%s' 상황에서 가장 중요했던 사실은 무엇인가요?"
                                        .formatted(sideLabel, statement.situation())
                        ),
                        new GuideQuestionItem(
                                2,
                                "%s이 '%s' 행동을 한 이유와 당시 의도는 무엇이었나요?"
                                        .formatted(sideLabel, statement.ownAction())
                        ),
                        new GuideQuestionItem(
                                3,
                                "%s이 원하는 해결인 '%s'을 위해 상대측과 합의할 기준은 무엇인가요?"
                                        .formatted(sideLabel, statement.desiredResolution())
                        )
                ),
                "1.0"
        );
    }

    @Override
    public LawyerArgumentResponse createArgumentDraft(
            AiRequestContext context,
            LawyerArgumentRequest request
    ) {
        String sideLabel = sideLabel(request.side());
        StatementPayload statement = request.statement();
        String answers = request.guideAnswers().stream()
                .map(GuideAnswerItem::answer)
                .filter(answer -> answer != null && !answer.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
        String basis = answers.isBlank()
                ? "양측 진술에서 확인된 사실관계입니다."
                : ensureSentence(answers);
        return new LawyerArgumentResponse(
                "%s에 %s 상대측의 행동은 '%s'였습니다. 이에 대해 %s은 '%s'라고 설명했고, 사건 이후에는 '%s'라는 상황이 이어졌습니다."
                        .formatted(
                                statement.incidentTime(),
                                statement.situation(),
                                statement.counterpartAction(),
                                sideLabel,
                                statement.ownAction(),
                                statement.afterConversation()
                        ),
                "%s은 '%s'라는 해결을 요청합니다. 핵심 근거는 %s"
                        .formatted(sideLabel, statement.desiredResolution(), basis),
                "1.0"
        );
    }

    @Override
    public LawyerDebateResponse createDebateTurn(AiRequestContext context, LawyerDebateRequest request) {
        String previous = request.previousTurns().isEmpty()
                ? "양측의 최종 변론을 검토했습니다."
                : "직전 반론의 핵심도 검토했습니다.";
        return new LawyerDebateResponse(
                "%s측의 %d번째 상호 변론입니다. %s %s측의 입장은 %s"
                        .formatted(request.side(), request.turn(), previous, request.side(),
                                request.arguments().get(request.side())),
                "1.0", request.promptVersion());
    }

    @Override
    public JudgeVerdictResponse createVerdict(
            AiRequestContext context,
            JudgeVerdictRequest request
    ) {
        return new JudgeVerdictResponse(
                TrialSide.B,
                60,
                40,
                "주문: B측의 손을 들어주되, 양측 모두 관계 회복을 위한 소통 기준을 다시 합의할 것을 권고합니다. A측의 불안과 서운함은 인정되지만, 명확한 약속 없이 상대의 즉시 응답 의무를 단정하기는 어렵습니다.",
                List.of(
                        "첫째, 양측 사이에 연락 빈도와 답장 가능 시간에 대한 명시적인 합의가 없었으므로 B측의 모든 지연 답장을 곧바로 책임 있는 회피로 보기는 어렵습니다.",
                        "둘째, A측은 서운함을 느낄 만한 사정이 있었으나 반복적인 확인과 감정적 표현으로 갈등을 확대시킨 일부 책임이 인정됩니다.",
                        "셋째, B측 역시 답장이 어려운 상황을 사전에 설명하거나 이후 대화를 회복하려는 노력이 부족했으므로 관계 신뢰를 흔든 책임이 일부 있습니다."
                ),
                new RecommendationPair(
                        "불안할 때 반복 연락하기보다 원하는 연락 기준과 기다릴 수 있는 시간을 먼저 구체적으로 말합니다.",
                        "답장이 어려운 시간대와 이후 다시 연락할 시점을 미리 공유해 상대가 방치됐다고 느끼지 않게 합니다."
                ),
                "1.0",
                request.promptVersion().isBlank()
                        ? appAiProperties.promptVersion()
                        : request.promptVersion()
        );
    }

    private String sideLabel(TrialSide side) {
        return side.name() + "측";
    }

    private String ensureSentence(String value) {
        String trimmed = value.trim();
        if (trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")) {
            return trimmed;
        }
        return trimmed + ".";
    }
}
