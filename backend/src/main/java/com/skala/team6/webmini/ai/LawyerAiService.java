package com.skala.team6.webmini.ai;

import com.skala.team6.webmini.common.exception.ApiException;
import com.skala.team6.webmini.common.exception.ErrorCode;
import com.skala.team6.webmini.common.model.RelationshipType;
import com.skala.team6.webmini.common.model.TrialSide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Service
public class LawyerAiService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LawyerAiService.class);
    private static final String SCHEMA_VERSION = "1.0";
    private static final String QUESTIONS_PROMPT_VERSION = "lawyer-questions-v1";
    private static final String ARGUMENT_PROMPT_VERSION = "lawyer-argument-v1";
    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_QUESTION_LENGTH = 1000;

    private final AiClient aiClient;

    public LawyerAiService(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    public List<GuideQuestion> createGuideQuestions(
            long trialId,
            TrialSide side,
            RelationshipType relationshipType,
            Statement statement
    ) {
        AiRequestContext context = context(QUESTIONS_PROMPT_VERSION);
        LawyerQuestionsRequest request = new LawyerQuestionsRequest(
                trialId,
                side,
                relationshipType,
                toStatementPayload(statement)
        );
        LawyerQuestionsResponse response = invoke(
                () -> aiClient.createGuideQuestions(context, request),
                this::isValidQuestionsResponse
        );
        recordSuccess("lawyer-questions", trialId, side, context);
        return response.questions().stream()
                .map(question -> new GuideQuestion(
                        question.sequence(),
                        question.question().trim()
                ))
                .toList();
    }

    public ArgumentDraft createArgumentDraft(
            long trialId,
            TrialSide side,
            Statement statement,
            List<GuideAnswer> guideAnswers
    ) {
        AiRequestContext context = context(ARGUMENT_PROMPT_VERSION);
        LawyerArgumentRequest request = new LawyerArgumentRequest(
                trialId,
                side,
                toStatementPayload(statement),
                guideAnswers.stream()
                        .map(answer -> new GuideAnswerItem(
                                answer.sequence(), answer.question(), answer.answer()))
                        .toList()
        );
        LawyerArgumentResponse response = invoke(
                () -> aiClient.createArgumentDraft(context, request),
                this::isValidArgumentResponse
        );
        recordSuccess("lawyer-argument", trialId, side, context);
        return new ArgumentDraft(
                response.factSummary().trim(),
                response.argumentText().trim()
        );
    }

    public String createDebateTurn(long trialId, int turn, int totalTurns, TrialSide side,
                                   String postContent, java.util.Map<TrialSide, Statement> statements,
                                   java.util.Map<TrialSide, String> arguments,
                                   List<DebateTurn> previousTurns) {
        String promptVersion = "lawyer-debate-v1";
        LawyerDebateRequest request = new LawyerDebateRequest(trialId, turn, totalTurns, side,
                postContent, statements.entrySet().stream().collect(
                        java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey,
                                entry -> toStatementPayload(entry.getValue()))),
                arguments, previousTurns.stream()
                        .map(item -> new DebateTurnItem(item.side(), item.content())).toList(),
                promptVersion);
        LawyerDebateResponse response = aiClient.createDebateTurn(context(promptVersion), request);
        if (response == null || !SCHEMA_VERSION.equals(response.schemaVersion())
                || !promptVersion.equals(response.promptVersion()) || !hasText(response.content())) {
            throw new ApiException(ErrorCode.AI_RESPONSE_INVALID);
        }
        return response.content().trim();
    }

    private StatementPayload toStatementPayload(Statement statement) {
        return new StatementPayload(
                statement.incidentTime(),
                statement.situation(),
                statement.counterpartAction(),
                statement.ownAction(),
                statement.afterConversation(),
                statement.desiredResolution()
        );
    }

    private <T> T invoke(Supplier<T> invocation, Predicate<T> validator) {
        int maxAttempts = aiClient instanceof LangChainAiClient ? 1 : MAX_ATTEMPTS;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T response = invocation.get();
                if (validator.test(response)) {
                    return response;
                }
            } catch (RuntimeException exception) {
                LOGGER.warn(
                    "AI 호출 실패: attempt={}, errorType={}",
                        attempt,
                        exception.getClass().getSimpleName()
                );
            }
        }
        throw new ApiException(aiClient instanceof LangChainAiClient
                ? ErrorCode.AI_RESPONSE_INVALID : ErrorCode.MOCK_AI_RESPONSE_INVALID);
    }

    private boolean isValidQuestionsResponse(LawyerQuestionsResponse response) {
        if (response == null
                || !SCHEMA_VERSION.equals(response.schemaVersion())
                || response.questions() == null
                || response.questions().isEmpty()) {
            return false;
        }
        for (int index = 0; index < response.questions().size(); index++) {
            GuideQuestionItem question = response.questions().get(index);
            if (question == null
                    || question.sequence() != index + 1
                    || !hasText(question.question())
                    || question.question().length() > MAX_QUESTION_LENGTH) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidArgumentResponse(LawyerArgumentResponse response) {
        return response != null
                && SCHEMA_VERSION.equals(response.schemaVersion())
                && hasText(response.factSummary())
                && hasText(response.argumentText());
    }

    private AiRequestContext context(String promptVersion) {
        return new AiRequestContext(UUID.randomUUID().toString(), promptVersion);
    }

    private void recordSuccess(
            String operation,
            long trialId,
            TrialSide side,
            AiRequestContext context
    ) {
        LOGGER.info(
                "Mock AI 호출 완료: operation={}, trialId={}, side={}, aiRequestId={}, promptVersion={}",
                operation,
                trialId,
                side,
                context.aiRequestId(),
                context.promptVersion()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record Statement(
            String incidentTime,
            String situation,
            String counterpartAction,
            String ownAction,
            String afterConversation,
            String desiredResolution
    ) {
    }

    public record GuideQuestion(int sequence, String question) {
    }

    public record GuideAnswer(int sequence, String question, String answer) {
    }

    public record ArgumentDraft(String factSummary, String argumentText) {
    }

    public record DebateTurn(TrialSide side, String content) {
    }
}
