package com.skala.team6.webmini.ai;

import com.skala.team6.webmini.common.config.AppAiProperties;
import com.skala.team6.webmini.common.exception.ApiException;
import com.skala.team6.webmini.common.exception.ErrorCode;
import com.skala.team6.webmini.common.model.TrialSide;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JudgeAiService {
    private final AiClient aiClient;
    private final AppAiProperties properties;

    public JudgeAiService(AiClient aiClient, AppAiProperties properties) {
        this.aiClient = aiClient;
        this.properties = properties;
    }

    public Verdict createVerdict(Long trialId, String postSummary,
                                 Map<TrialSide, String> arguments) {
        return createVerdict(trialId, postSummary, arguments, Map.of(), List.of());
    }

    public Verdict createVerdict(Long trialId, String postSummary,
                                 Map<TrialSide, String> arguments,
                                 Map<TrialSide, LawyerAiService.Statement> statements,
                                 List<LawyerAiService.DebateTurn> debateTurns) {
        if (arguments == null || arguments.size() != 2
                || !arguments.keySet().containsAll(List.of(TrialSide.A, TrialSide.B))
                || arguments.values().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        AiRequestContext context = new AiRequestContext(UUID.randomUUID().toString(), properties.promptVersion());
        JudgeVerdictRequest request = new JudgeVerdictRequest(
                trialId, postSummary, arguments,
                statements.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> new StatementPayload(
                                entry.getValue().incidentTime(), entry.getValue().situation(),
                                entry.getValue().counterpartAction(), entry.getValue().ownAction(),
                                entry.getValue().afterConversation(), entry.getValue().desiredResolution()))),
                debateTurns.stream().map(item -> new DebateTurnItem(item.side(), item.content())).toList(),
                properties.promptVersion());
        for (int attempt = 0; attempt < ("mock".equals(properties.provider()) ? 2 : 1); attempt++) {
            try {
                JudgeVerdictResponse response = aiClient.createVerdict(context, request);
                if (isValid(response)) {
                    return new Verdict(response.winnerSide(), response.aFaultRatio(), response.bFaultRatio(),
                            response.summary().trim(), response.grounds().stream().map(String::trim).toList(),
                            response.recommendations().a().trim(), response.recommendations().b().trim(),
                            response.promptVersion());
                }
            } catch (RuntimeException ignored) {
                // A Mock adapter failure is retried once before exposing a stable API error.
            }
        }
        throw new ApiException("mock".equals(properties.provider())
                ? ErrorCode.MOCK_AI_RESPONSE_INVALID : ErrorCode.AI_RESPONSE_INVALID);
    }

    // Python 검증과 별개로 DB 저장 전 계약을 다시 검사한다. 근거의 사실성 평가는 별도로 필요하다.
    private boolean isValid(JudgeVerdictResponse response) {
        return response != null
                && "1.0".equals(response.schemaVersion())
                && response.aFaultRatio() >= 0 && response.aFaultRatio() <= 100
                && response.bFaultRatio() >= 0 && response.bFaultRatio() <= 100
                && response.aFaultRatio() + response.bFaultRatio() == 100
                && hasEnoughText(response.summary(), 30)
                && response.grounds() != null
                && response.grounds().size() >= 3
                && response.grounds().stream().allMatch(ground -> hasEnoughText(ground, 20))
                && response.recommendations() != null
                && hasText(response.recommendations().a())
                && hasText(response.recommendations().b())
                && hasText(response.promptVersion());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasEnoughText(String value, int minimumLength) {
        return hasText(value) && value.trim().length() >= minimumLength;
    }

    public record Verdict(TrialSide winnerSide, int aFaultRatio, int bFaultRatio,
                          String summary, List<String> grounds, String aRecommendation,
                          String bRecommendation, String promptVersion) {
        JudgeVerdictResponse toResponse() {
            return new JudgeVerdictResponse(winnerSide, aFaultRatio, bFaultRatio, summary,
                    grounds, new RecommendationPair(aRecommendation, bRecommendation), "1.0", promptVersion);
        }
    }
}
