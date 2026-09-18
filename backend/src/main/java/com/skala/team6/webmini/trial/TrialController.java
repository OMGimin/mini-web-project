package com.skala.team6.webmini.trial;

import com.skala.team6.webmini.common.api.ApiResponse;
import com.skala.team6.webmini.common.config.AppAiProperties;
import com.skala.team6.webmini.common.config.TrialTimingProperties;
import com.skala.team6.webmini.common.model.TrialSide;
import com.skala.team6.webmini.common.model.TrialSpeaker;
import com.skala.team6.webmini.common.model.TrialStatus;
import com.skala.team6.webmini.demo.DemoUserContext;
import com.skala.team6.webmini.demo.DemoUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Trial", description = "재판 진행 API")
@Validated
@RestController
@RequestMapping("/api/v1/trials")
public class TrialController {
    private final TrialQueryService trialQueryService;
    private final TrialStatementService trialStatementService;
    private final GuideAnswerService guideAnswerService;
    private final TrialArgumentService trialArgumentService;
    private final TrialPreparationAiService trialPreparationAiService;
    private final TrialStartService trialStartService;
    private final TrialChatQueryService trialChatQueryService;
    private final TrialVoteService trialVoteService;
    private final TrialResultService trialResultService;
    private final TrialPresenceService trialPresenceService;
    private final TrialGenerationService generationService;
    private final AppAiProperties aiProperties;
    private final TrialTimingProperties timings;

    public TrialController(
            TrialQueryService trialQueryService,
            TrialStatementService trialStatementService,
            GuideAnswerService guideAnswerService,
            TrialArgumentService trialArgumentService,
            TrialPreparationAiService trialPreparationAiService,
            TrialStartService trialStartService,
            TrialChatQueryService trialChatQueryService,
            TrialVoteService trialVoteService,
            TrialResultService trialResultService,
            TrialPresenceService trialPresenceService,
            TrialGenerationService generationService,
            AppAiProperties aiProperties,
            TrialTimingProperties timings
    ) {
        this.trialQueryService = trialQueryService;
        this.trialStatementService = trialStatementService;
        this.guideAnswerService = guideAnswerService;
        this.trialArgumentService = trialArgumentService;
        this.trialPreparationAiService = trialPreparationAiService;
        this.trialStartService = trialStartService;
        this.trialChatQueryService = trialChatQueryService;
        this.trialVoteService = trialVoteService;
        this.trialResultService = trialResultService;
        this.trialPresenceService = trialPresenceService;
        this.generationService = generationService;
        this.aiProperties = aiProperties;
        this.timings = timings;
    }

    @Operation(summary = "재판 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<TrialListResponse>> getTrials(
            @RequestParam(required = false) TrialStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        TrialQueryService.TrialList result =
                trialQueryService.findPublicActiveTrials(status, page, size);
        List<TrialListItem> items = result.entries().stream()
                .map(entry -> new TrialListItem(
                        entry.trial().getId(),
                        entry.trial().getStatus(),
                        entry.trial().getPost().getTitle(),
                        entry.aDisplayName(),
                        entry.bDisplayName(),
                        entry.trial().getVisibility(),
                        trialPresenceService.getAudienceCount(entry.trial().getId())
                ))
                .toList();
        TrialListResponse response = new TrialListResponse(
                items,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @Operation(summary = "재판 기본 정보 조회")
    @GetMapping("/{trialId}")
    public ResponseEntity<ApiResponse<TrialDetailResponse>> getTrial(
            @PathVariable @Min(1) Long trialId
    ) {
        TrialQueryService.TrialDetail detail = trialQueryService.findDetail(trialId);
        var trial = detail.trial();
        var post = trial.getPost();
        var parties = detail.parties();
        TrialDetailResponse response = new TrialDetailResponse(
                trial.getId(),
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getRelationshipType(),
                trial.getVisibility(),
                trial.getStatus(),
                trial.getPhaseStartedAt() == null ? null : trial.getPhaseStartedAt().toString(),
                trial.getPhaseEndsAt() == null ? null : trial.getPhaseEndsAt().toString(),
                new TrialPartyInfo(
                        parties.get(0).getSide(),
                        parties.get(0).getDisplayName(),
                        parties.get(0).isReady()),
                new TrialPartyInfo(
                        parties.get(1).getSide(),
                        parties.get(1).getDisplayName(),
                        parties.get(1).isReady())
        );
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @Operation(summary = "당사자 진술 저장")
    @PutMapping("/{trialId}/parties/{side}/statement")
    public ResponseEntity<ApiResponse<StatementResponse>> saveStatement(
            @PathVariable @Min(1) Long trialId,
            @PathVariable TrialSide side,
            @Parameter(
                    name = "X-Demo-User-Id",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "브라우저 Local Storage Demo 사용자 UUID"
            )
            @DemoUserId DemoUserContext demoUser,
            @Valid @RequestBody StatementRequest request
    ) {
        var statement = trialStatementService.save(trialId, side, request);
        StatementRequest saved = new StatementRequest(
                statement.getIncidentTime(),
                statement.getSituation(),
                statement.getCounterpartAction(),
                statement.getOwnAction(),
                statement.getAfterConversation(),
                statement.getDesiredResolution()
        );
        return ResponseEntity.ok(ApiResponse.of(new StatementResponse(side, saved)));
    }

    @Operation(summary = "안내 질문 생성")
    @PostMapping("/{trialId}/parties/{side}/guide-questions")
    public ResponseEntity<ApiResponse<GuideQuestionsResponse>> createGuideQuestions(
            @PathVariable @Min(1) Long trialId,
            @PathVariable TrialSide side
    ) {
        GuideQuestionsResponse response = new GuideQuestionsResponse(
                trialPreparationAiService.createGuideQuestions(trialId, side).stream()
                        .map(question -> new GuideQuestionResponse(
                                question.getId(),
                                question.getSequenceNo(),
                                question.getQuestion()
                        ))
                        .toList(), aiProperties.provider()
        );
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @Operation(summary = "안내 질문 답변 저장")
    @PutMapping("/{trialId}/parties/{side}/guide-answers")
    public ResponseEntity<ApiResponse<GuideAnswersResponse>> saveGuideAnswers(
            @PathVariable @Min(1) Long trialId,
            @PathVariable TrialSide side,
            @Parameter(
                    name = "X-Demo-User-Id",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "브라우저 Local Storage Demo 사용자 UUID"
            )
            @DemoUserId DemoUserContext demoUser,
            @Valid @RequestBody GuideAnswersRequest request
    ) {
        GuideAnswerService.SavedGuideAnswers saved = guideAnswerService.save(trialId, side, request);
        GuideAnswersResponse response = new GuideAnswersResponse(saved.answers(), saved.allAnswered());
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @Operation(summary = "변론문 초안 생성")
    @PostMapping("/{trialId}/parties/{side}/argument-draft")
    public ResponseEntity<ApiResponse<ArgumentDraftResponse>> createArgumentDraft(
            @PathVariable @Min(1) Long trialId,
            @PathVariable TrialSide side
    ) {
        var statement = trialPreparationAiService.createArgumentDraft(trialId, side);
        return ResponseEntity.ok(ApiResponse.of(new ArgumentDraftResponse(
                side, statement.getFactSummary(), statement.getArgumentText(), aiProperties.provider())));
    }

    @Operation(summary = "변론문 초안 수정")
    @PutMapping("/{trialId}/parties/{side}/argument-draft")
    public ResponseEntity<ApiResponse<ArgumentDraftResponse>> updateArgumentDraft(
            @PathVariable @Min(1) Long trialId,
            @PathVariable TrialSide side,
            @Parameter(
                    name = "X-Demo-User-Id",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "브라우저 Local Storage Demo 사용자 UUID"
            )
            @DemoUserId DemoUserContext demoUser,
            @Valid @RequestBody UpdateArgumentDraftRequest request
    ) {
        var statement = trialArgumentService.updateDraft(trialId, side, request);
        return ResponseEntity.ok(ApiResponse.of(new ArgumentDraftResponse(
                side, statement.getFactSummary(), statement.getArgumentText(), aiProperties.provider())));
    }

    @Operation(summary = "변론문 최종 확인")
    @PostMapping("/{trialId}/parties/{side}/confirm")
    public ResponseEntity<ApiResponse<ConfirmArgumentResponse>> confirmArgument(
            @PathVariable @Min(1) Long trialId,
            @PathVariable TrialSide side,
            @Parameter(
                    name = "X-Demo-User-Id",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "브라우저 Local Storage Demo 사용자 UUID"
            )
            @DemoUserId DemoUserContext demoUser
    ) {
        TrialArgumentService.ConfirmedArgument confirmed =
                trialArgumentService.confirm(trialId, side);
        return ResponseEntity.ok(ApiResponse.of(new ConfirmArgumentResponse(
                side,
                confirmed.statement().getConfirmedAt().toString(),
                confirmed.bothConfirmed()
        )));
    }

    @Operation(summary = "재판 시작")
    @PostMapping("/{trialId}/start")
    public ResponseEntity<ApiResponse<TrialSnapshotResponse>> startTrial(
            @PathVariable @Min(1) Long trialId,
            @Parameter(
                    name = "X-Demo-User-Id",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "브라우저 Local Storage Demo 사용자 UUID"
            )
            @DemoUserId DemoUserContext demoUser
    ) {
        TrialStartService.StartedTrial started = trialStartService.start(trialId);
        var trial = started.trial();
        return ResponseEntity.ok(ApiResponse.of(new TrialSnapshotResponse(
                trial.getStatus(),
                formatTime(trial.getPhaseStartedAt()),
                formatTime(trial.getPhaseEndsAt()),
                formatTime(trial.getScheduledEndAt()),
                started.latestEventSequence(),
                0,
                trialPresenceService.getAudienceCount(trialId),
                false,
                false,
                aiProperties.provider(), "IDLE", null, null,
                Math.toIntExact(timings.debateTurns()), null, null, false)));
    }

    @Operation(summary = "현재 상태 스냅샷 조회")
    @GetMapping("/{trialId}/snapshot")
    public ResponseEntity<ApiResponse<TrialSnapshotResponse>> getSnapshot(
            @PathVariable @Min(1) Long trialId
    ) {
        TrialQueryService.TrialSnapshot snapshot = trialQueryService.findSnapshot(trialId);
        var trial = snapshot.trial();
        TrialSnapshotResponse response = new TrialSnapshotResponse(
                trial.getStatus(),
                formatTime(trial.getPhaseStartedAt()),
                formatTime(trial.getPhaseEndsAt()),
                formatTime(trial.getScheduledEndAt()),
                snapshot.latestEventSequence(),
                snapshot.latestMessageSequence(),
                trialPresenceService.getAudienceCount(trialId),
                trial.getStatus() == TrialStatus.VOTING
                        && trial.getPhaseEndsAt() != null
                        && trial.getPhaseEndsAt().isAfter(java.time.OffsetDateTime.now()),
                trial.getStatus() == TrialStatus.ENDED,
                aiProperties.provider(),
                trial.getGenerationStatus(),
                trial.getGenerationStage(),
                generationTurn(trial, snapshot.publishedDebateTurns()),
                Math.toIntExact(timings.debateTurns()),
                nextSpeaker(trial, snapshot.publishedDebateTurns()),
                "FAILED".equals(trial.getGenerationStatus())
                        ? "AI 생성에 실패했습니다. 재판 생성자가 다시 시도할 수 있습니다." : null,
                "FAILED".equals(trial.getGenerationStatus())
        );
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @Operation(summary = "실패한 AI 생성 재시도")
    @PostMapping("/{trialId}/ai/retry")
    public ResponseEntity<ApiResponse<TrialSnapshotResponse>> retryGeneration(
            @PathVariable @Min(1) Long trialId,
            @DemoUserId DemoUserContext demoUser
    ) {
        generationService.retry(trialId, demoUser.demoUserId());
        return getSnapshot(trialId);
    }

    private Integer generationTurn(com.skala.team6.webmini.database.entity.TrialEntity trial,
                                   long published) {
        if (trial.getGenerationTurn() != null) { return trial.getGenerationTurn(); }
        if (trial.getStatus() != TrialStatus.DEBATE || published >= timings.debateTurns()) { return null; }
        return Math.toIntExact(published + 1);
    }

    private TrialSpeaker nextSpeaker(com.skala.team6.webmini.database.entity.TrialEntity trial,
                                     long published) {
        if (trial.getStatus() != TrialStatus.DEBATE || published >= timings.debateTurns()) { return null; }
        TrialSide side = switch ((int) (published % 4)) {
            case 0, 3 -> TrialSide.A;
            default -> TrialSide.B;
        };
        return side == TrialSide.A ? TrialSpeaker.A_LAWYER : TrialSpeaker.B_LAWYER;
    }

    @Operation(summary = "재판 이벤트 조회")
    @GetMapping("/{trialId}/events")
    public ResponseEntity<ApiResponse<List<TrialEventResponse>>> getEvents(
            @PathVariable @Min(1) Long trialId,
            @RequestParam(defaultValue = "0") @Min(0) long afterSequence
    ) {
        List<TrialEventResponse> response = trialQueryService
                .findEventsAfter(trialId, afterSequence)
                .stream()
                .map(event -> new TrialEventResponse(
                        event.getId(),
                        event.getTrial().getId(),
                        event.getSequenceNo(),
                        event.getEventType(),
                        event.getSpeaker(),
                        event.getContent(),
                        event.getPayload(),
                        event.getCreatedAt().toString()
                ))
                .toList();
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @Operation(summary = "이전 채팅 조회")
    @GetMapping("/{trialId}/messages")
    public ResponseEntity<ApiResponse<TrialMessagesResponse>> getMessages(
            @PathVariable @Min(1) Long trialId,
            @RequestParam(defaultValue = "0") @Min(0) long afterSequence,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                trialChatQueryService.findMessages(trialId, afterSequence, size)));
    }

    @Operation(summary = "승소 투표")
    @PostMapping("/{trialId}/votes")
    public ResponseEntity<ApiResponse<VoteResponse>> vote(
            @PathVariable @Min(1) Long trialId,
            @Parameter(
                    name = "X-Demo-User-Id",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "브라우저 Local Storage Demo 사용자 UUID"
            )
            @DemoUserId DemoUserContext demoUser,
            @Valid @RequestBody VoteRequest request
    ) {
        TrialVoteService.VoteResult result = trialVoteService.vote(
                trialId, demoUser.demoUserId(), request.selectedSide());
        return ResponseEntity.status(201).body(ApiResponse.of(new VoteResponse(
                result.selectedSide(), result.votedAt().toString())));
    }

    @Operation(summary = "재판 결과 조회")
    @GetMapping("/{trialId}/results")
    public ResponseEntity<ApiResponse<TrialResultResponse>> getResults(
            @PathVariable @Min(1) Long trialId
    ) {
        return ResponseEntity.ok(ApiResponse.of(trialResultService.getResult(trialId)));
    }

    private TrialSnapshotResponse sampleSnapshot(TrialStatus status) {
        return new TrialSnapshotResponse(
                status,
                "2026-09-03T03:00:00Z",
                "2026-09-03T03:10:00Z",
                "2026-09-03T03:30:00Z",
                12,
                15,
                3,
                true,
                false
        );
    }

    private String formatTime(java.time.OffsetDateTime value) {
        return value == null ? null : value.toString();
    }
}
