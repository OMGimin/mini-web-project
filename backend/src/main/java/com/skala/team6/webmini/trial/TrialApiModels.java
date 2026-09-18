package com.skala.team6.webmini.trial;

import com.skala.team6.webmini.common.model.RelationshipType;
import com.skala.team6.webmini.common.model.TrialSide;
import com.skala.team6.webmini.common.model.TrialSpeaker;
import com.skala.team6.webmini.common.model.TrialStatus;
import com.skala.team6.webmini.common.model.Visibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

record TrialListResponse(
        List<TrialListItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}

record TrialListItem(
        Long trialId,
        TrialStatus status,
        String title,
        String aDisplayName,
        String bDisplayName,
        Visibility visibility,
        int audienceCount
) {
}

record TrialDetailResponse(
        Long trialId,
        Long postId,
        String title,
        String content,
        RelationshipType relationshipType,
        Visibility visibility,
        TrialStatus status,
        String phaseStartedAt,
        String phaseEndsAt,
        TrialPartyInfo aParty,
        TrialPartyInfo bParty
) {
}

record TrialPartyInfo(
        TrialSide side,
        String displayName,
        boolean ready
) {
}

record StatementRequest(
        @NotBlank
        @Size(min = 1, max = 1000)
        String incidentTime,
        @NotBlank
        @Size(min = 1, max = 1000)
        String situation,
        @NotBlank
        @Size(min = 1, max = 1000)
        String counterpartAction,
        @NotBlank
        @Size(min = 1, max = 1000)
        String ownAction,
        @NotBlank
        @Size(min = 1, max = 1000)
        String afterConversation,
        @NotBlank
        @Size(min = 1, max = 1000)
        String desiredResolution
) {
}

record StatementResponse(
        TrialSide side,
        StatementRequest statement
) {
}

record GuideQuestionsResponse(
        List<GuideQuestionResponse> questions,
        String aiProvider
) {
}

record GuideQuestionResponse(
        Long questionId,
        int sequence,
        String question
) {
}

record GuideAnswersRequest(
        @NotEmpty
        @Valid
        List<GuideAnswerPayload> answers
) {
}

record GuideAnswerPayload(
        @NotNull
        Long questionId,
        @NotBlank
        String answer
) {
}

record GuideAnswersResponse(
        List<GuideAnswerPayload> answers,
        boolean allAnswered
) {
}

record ArgumentDraftResponse(
        TrialSide side,
        String factSummary,
        String argumentText,
        String aiProvider
) {
}

record UpdateArgumentDraftRequest(
        @NotBlank
        String factSummary,
        @NotBlank
        String argumentText
) {
}

record ConfirmArgumentResponse(
        TrialSide side,
        String confirmedAt,
        boolean bothConfirmed
) {
}

record TrialSnapshotResponse(
        TrialStatus status,
        String phaseStartedAt,
        String phaseEndsAt,
        String scheduledEndAt,
        long latestEventSequence,
        long latestMessageSequence,
        int audienceCount,
        boolean voteOpen,
        boolean ended,
        String aiProvider,
        String generationStatus,
        String generationStage,
        Integer generationTurn,
        int totalDebateTurns,
        TrialSpeaker nextSpeaker,
        String generationError,
        boolean retryable
) {
    TrialSnapshotResponse(TrialStatus status, String phaseStartedAt, String phaseEndsAt,
                          String scheduledEndAt, long latestEventSequence,
                          long latestMessageSequence, int audienceCount,
                          boolean voteOpen, boolean ended) {
        this(status, phaseStartedAt, phaseEndsAt, scheduledEndAt,
                latestEventSequence, latestMessageSequence, audienceCount,
                voteOpen, ended, "mock", "IDLE", null, null, 4, null, null, false);
    }
}

record TrialEventResponse(
        Long eventId,
        Long trialId,
        long sequence,
        String eventType,
        TrialSpeaker speaker,
        String content,
        String payload,
        String createdAt
) {
}

record TrialMessagesResponse(
        List<TrialMessageItem> items,
        long latestMessageSequence,
        boolean hasMore
) {
}

record TrialMessageItem(
        Long messageId,
        long messageSequence,
        Long trialId,
        TrialMessageSender sender,
        String content,
        String createdAt
) {
}

record TrialMessageSender(
        String demoUserId,
        String nickname
) {
}

record VoteRequest(
        @NotNull
        TrialSide selectedSide
) {
}

record VoteResponse(
        TrialSide selectedSide,
        String votedAt
) {
}

record TrialResultResponse(
        Long trialId,
        VerdictPayload verdict,
        PublicVotePayload publicVote
) {
}

record VerdictPayload(
        TrialSide winnerSide,
        @Min(0) @Max(100) int aFaultRatio,
        @Min(0) @Max(100) int bFaultRatio,
        String summary,
        List<String> grounds,
        String aRecommendation,
        String bRecommendation
) {
}

record PublicVotePayload(
        int aVotes,
        int bVotes,
        int totalVotes
) {
}
