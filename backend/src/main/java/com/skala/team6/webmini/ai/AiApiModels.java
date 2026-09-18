package com.skala.team6.webmini.ai;

import com.skala.team6.webmini.common.model.RelationshipType;
import com.skala.team6.webmini.common.model.TrialSide;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

record LawyerQuestionsRequest(
        @Schema(example = "10")
        @NotNull
        Long trialId,
        @NotNull
        TrialSide side,
        @NotNull
        RelationshipType relationshipType,
        @NotNull
        @Valid
        StatementPayload statement
) {
}

record LawyerQuestionsResponse(
        @ArraySchema(schema = @Schema(implementation = GuideQuestionItem.class))
        List<GuideQuestionItem> questions,
        @Schema(example = "1.0")
        String schemaVersion
) {
}

record LawyerArgumentRequest(
        @Schema(example = "10")
        @NotNull
        Long trialId,
        @NotNull
        TrialSide side,
        @NotNull
        @Valid
        StatementPayload statement,
        @NotNull
        @Valid
        List<GuideAnswerItem> guideAnswers
) {
}

record LawyerArgumentResponse(
        @Schema(example = "양측은 연락 빈도에 대한 명확한 합의가 없었습니다.")
        String factSummary,
        @Schema(example = "A측은 불안감 때문에 반복 연락했으나 사전 합의가 없었다고 주장합니다.")
        String argumentText,
        @Schema(example = "1.0")
        String schemaVersion
) {
}

record JudgeVerdictRequest(
        @Schema(example = "10")
        @NotNull
        Long trialId,
        @Schema(example = "연락 빈도로 발생한 갈등")
        @NotBlank
        String postSummary,
        @NotNull
        @Size(min = 2, max = 2)
        Map<TrialSide, String> arguments,
        Map<TrialSide, StatementPayload> statements,
        List<DebateTurnItem> debateTurns,
        @Schema(example = "judge-v1")
        @NotBlank
        String promptVersion
) {
}

record JudgeVerdictResponse(
        TrialSide winnerSide,
        @Schema(example = "60")
        int aFaultRatio,
        @Schema(example = "40")
        int bFaultRatio,
        @Schema(example = "판결 요지")
        String summary,
        List<String> grounds,
        @NotNull
        RecommendationPair recommendations,
        @Schema(example = "1.0")
        String schemaVersion,
        @Schema(example = "judge-v1")
        String promptVersion
) {
}

record LawyerDebateRequest(Long trialId, int turn, int totalTurns, TrialSide side,
                           String postContent, Map<TrialSide, StatementPayload> statements,
                           Map<TrialSide, String> arguments,
                           List<DebateTurnItem> previousTurns, String promptVersion) {
}

record LawyerDebateResponse(String content, String schemaVersion, String promptVersion) {
}

record DebateTurnItem(TrialSide side, String content) {
}

record GuideQuestionItem(
        @Schema(example = "1")
        int sequence,
        @Schema(example = "평소 두 분이 합의한 연락 기준이 있었나요?")
        String question
) {
}

record GuideAnswerItem(
        @Schema(example = "1")
        int sequence,
        @Schema(example = "평소 두 분이 합의한 연락 기준이 있었나요?")
        String question,
        @Schema(example = "명확한 기준은 없었습니다.")
        String answer
) {
}

record RecommendationPair(
        @Schema(example = "불안할 때 반복 연락 전 의사를 확인합니다.")
        String a,
        @Schema(example = "답장이 어려운 시간을 미리 공유합니다.")
        String b
) {
}

record StatementPayload(
        @Schema(example = "어제 저녁")
        @NotBlank
        String incidentTime,
        @Schema(example = "연락 문제로 다투었습니다.")
        @NotBlank
        String situation,
        @Schema(example = "답장이 늦었습니다.")
        @NotBlank
        String counterpartAction,
        @Schema(example = "반복해서 연락했습니다.")
        @NotBlank
        String ownAction,
        @Schema(example = "감정이 상한 채 대화가 끝났습니다.")
        @NotBlank
        String afterConversation,
        @Schema(example = "연락 기준을 합의하고 싶습니다.")
        @NotBlank
        String desiredResolution
) {
}
