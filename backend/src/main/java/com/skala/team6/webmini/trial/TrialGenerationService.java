package com.skala.team6.webmini.trial;

import com.skala.team6.webmini.ai.JudgeAiService;
import com.skala.team6.webmini.ai.LawyerAiService;
import com.skala.team6.webmini.common.config.TrialTimingProperties;
import com.skala.team6.webmini.common.exception.ApiException;
import com.skala.team6.webmini.common.exception.ErrorCode;
import com.skala.team6.webmini.common.model.TrialSide;
import com.skala.team6.webmini.common.model.TrialSpeaker;
import com.skala.team6.webmini.common.model.TrialStatus;
import com.skala.team6.webmini.database.entity.TrialEntity;
import com.skala.team6.webmini.database.entity.TrialEventEntity;
import com.skala.team6.webmini.database.entity.TrialStatementEntity;
import com.skala.team6.webmini.database.repository.TrialEventRepository;
import com.skala.team6.webmini.database.repository.TrialPartyRepository;
import com.skala.team6.webmini.database.repository.TrialRepository;
import com.skala.team6.webmini.database.repository.TrialStatementRepository;
import com.skala.team6.webmini.database.repository.VerdictRepository;
import com.skala.team6.webmini.database.entity.VerdictEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TrialGenerationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrialGenerationService.class);
    private static final Duration STALE_AFTER = Duration.ofSeconds(120);
    private static final List<TrialSide> ORDER = List.of(TrialSide.A, TrialSide.B, TrialSide.B, TrialSide.A);
    private final TransactionTemplate transactions;
    private final TaskExecutor executor;
    private final TrialRepository trials;
    private final TrialPartyRepository parties;
    private final TrialStatementRepository statements;
    private final TrialEventRepository events;
    private final VerdictRepository verdicts;
    private final TrialEventWriter eventWriter;
    private final TrialTimingProperties timings;
    private final LawyerAiService lawyer;
    private final JudgeAiService judge;
    private final ObjectMapper mapper;

    public TrialGenerationService(PlatformTransactionManager transactionManager,
                                  @Qualifier("trialAiExecutor") TaskExecutor executor,
                                  TrialRepository trials, TrialPartyRepository parties,
                                  TrialStatementRepository statements, TrialEventRepository events,
                                  VerdictRepository verdicts, TrialEventWriter eventWriter,
                                  TrialTimingProperties timings, LawyerAiService lawyer,
                                  JudgeAiService judge, ObjectMapper mapper) {
        this.transactions = new TransactionTemplate(transactionManager);
        this.executor = executor;
        this.trials = trials;
        this.parties = parties;
        this.statements = statements;
        this.events = events;
        this.verdicts = verdicts;
        this.eventWriter = eventWriter;
        this.timings = timings;
        this.lawyer = lawyer;
        this.judge = judge;
        this.mapper = mapper;
    }

    public void tick(Long trialId, OffsetDateTime now) {
        Work work = transactions.execute(status -> claim(trialId, now));
        if (work == null) { return; }
        try {
            executor.execute(() -> generate(work));
        } catch (RuntimeException exception) {
            LOGGER.warn("Trial AI work failed: trialId={}, stage={}, errorType={}",
                    work.trialId(), work.stage(), exception.getClass().getSimpleName());
            fail(work, OffsetDateTime.now());
        }
    }

    public void retry(Long trialId, String demoUserId) {
        transactions.executeWithoutResult(status -> {
            TrialEntity trial = trials.findByIdForUpdate(trialId)
                    .orElseThrow(() -> new ApiException(ErrorCode.TRIAL_NOT_FOUND));
            if (!trial.getCreator().getDemoKey().equals(demoUserId)) {
                throw new ApiException(ErrorCode.AI_RETRY_FORBIDDEN);
            }
            if (!"FAILED".equals(trial.getGenerationStatus())) {
                throw new ApiException(ErrorCode.AI_RETRY_NOT_ALLOWED);
            }
            trial.finishGeneration();
        });
        tick(trialId, OffsetDateTime.now());
    }

    private Work claim(Long trialId, OffsetDateTime now) {
        TrialEntity trial = trials.findByIdForUpdate(trialId).orElse(null);
        if (trial == null || (trial.getStatus() != TrialStatus.DEBATE
                && trial.getStatus() != TrialStatus.VOTING)) { return null; }
        if ("FAILED".equals(trial.getGenerationStatus())) { return null; }
        if ("GENERATING".equals(trial.getGenerationStatus())) {
            if (trial.getGenerationStartedAt() != null
                    && trial.getGenerationStartedAt().plus(STALE_AFTER).isBefore(now)) {
                markFailed(trial, now);
            }
            return null;
        }
        if (trial.getPhaseEndsAt() == null || trial.getPhaseEndsAt().isAfter(now)) { return null; }
        List<TrialEventEntity> priorEvents = events.findByTrialIdOrderBySequenceNoAsc(trialId);
        List<LawyerAiService.DebateTurn> turns = new ArrayList<>();
        for (TrialEventEntity event : priorEvents) {
            if ("A_DEBATE".equals(event.getEventType()) || "B_DEBATE".equals(event.getEventType())) {
                turns.add(new LawyerAiService.DebateTurn(
                        "A_DEBATE".equals(event.getEventType()) ? TrialSide.A : TrialSide.B,
                        event.getContent()));
            }
        }
        String stage;
        Integer turn;
        TrialSide side = null;
        if (trial.getStatus() == TrialStatus.DEBATE) {
            if (turns.size() >= timings.debateTurns()) { return null; }
            stage = "DEBATE";
            turn = turns.size() + 1;
            side = ORDER.get((turn - 1) % ORDER.size());
        } else {
            if (turns.size() != timings.debateTurns()
                    || verdicts.findByTrialId(trialId).isPresent()) { return null; }
            stage = "VERDICT";
            turn = null;
        }
        Map<TrialSide, LawyerAiService.Statement> sourceStatements = new EnumMap<>(TrialSide.class);
        Map<TrialSide, String> arguments = new EnumMap<>(TrialSide.class);
        for (var party : parties.findByTrialIdOrderBySideAsc(trialId)) {
            TrialStatementEntity statement = statements.findByTrialPartyId(party.getId()).orElseThrow();
            sourceStatements.put(party.getSide(), new LawyerAiService.Statement(
                    statement.getIncidentTime(), statement.getSituation(),
                    statement.getCounterpartAction(), statement.getOwnAction(),
                    statement.getAfterConversation(), statement.getDesiredResolution()));
            arguments.put(party.getSide(), statement.getArgumentText());
        }
        String requestId = UUID.randomUUID().toString();
        trial.beginGeneration(stage, turn, requestId, now);
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("status", trial.getStatus().name());
        payload.put("stage", stage);
        payload.put("turn", turn);
        payload.put("totalTurns", timings.debateTurns());
        payload.put("nextSpeaker", side == null ? null : speaker(side).name());
        payload.put("generationStatus", "GENERATING");
        eventWriter.save(trial, "GENERATION_STARTED", TrialSpeaker.SYSTEM, null, payload);
        return new Work(trialId, requestId, stage, turn, side, trial.getPost().getContent(),
                sourceStatements, arguments, turns);
    }

    private void generate(Work work) {
        try {
            Boolean active = transactions.execute(status -> matching(work,
                    "DEBATE".equals(work.stage()) ? TrialStatus.DEBATE : TrialStatus.VOTING) != null);
            if (!Boolean.TRUE.equals(active)) { return; }
            if ("DEBATE".equals(work.stage())) {
                String content = lawyer.createDebateTurn(work.trialId(), work.turn(),
                        Math.toIntExact(timings.debateTurns()), work.side(), work.postContent(),
                        work.statements(), work.arguments(), work.turns());
                transactions.executeWithoutResult(status -> publishTurn(work, content, OffsetDateTime.now()));
            } else {
                JudgeAiService.Verdict verdict = judge.createVerdict(work.trialId(), work.postContent(),
                        work.arguments(), work.statements(), work.turns());
                transactions.executeWithoutResult(status -> publishVerdict(work, verdict, OffsetDateTime.now()));
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Trial AI generation failed: trialId={}, stage={}, errorType={}",
                    work.trialId(), work.stage(), exception.getClass().getSimpleName());
            fail(work, OffsetDateTime.now());
        }
    }

    private void publishTurn(Work work, String content, OffsetDateTime now) {
        TrialEntity trial = matching(work, TrialStatus.DEBATE);
        if (trial == null) { return; }
        long saved = eventWriter.countByTrialAndTypes(work.trialId(), "A_DEBATE", "B_DEBATE");
        if (saved != work.turn() - 1) { return; }
        OffsetDateTime next = now.plusSeconds(timings.debateIntervalSeconds());
        trial.extendPhaseTo(next);
        trial.scheduleEnd(next.plusSeconds((timings.debateTurns() - work.turn())
                * timings.debateIntervalSeconds() + timings.votingSeconds()));
        trial.finishGeneration();
        eventWriter.save(trial, work.side() == TrialSide.A ? "A_DEBATE" : "B_DEBATE",
                speaker(work.side()), content, Map.of(
                        "status", TrialStatus.DEBATE.name(), "phaseEndsAt", next.toString(),
                        "turn", work.turn(), "totalTurns", timings.debateTurns(),
                        "generationStatus", "IDLE"));
    }

    private void publishVerdict(Work work, JudgeAiService.Verdict result, OffsetDateTime now) {
        TrialEntity trial = matching(work, TrialStatus.VOTING);
        if (trial == null || verdicts.findByTrialId(work.trialId()).isPresent()) { return; }
        VerdictEntity verdict = verdicts.saveAndFlush(new VerdictEntity(trial, result.winnerSide(),
                result.aFaultRatio(), result.bFaultRatio(), result.summary(),
                mapper.writeValueAsString(result.grounds()), result.aRecommendation(),
                result.bRecommendation(), result.promptVersion()));
        trial.finishGeneration();
        trial.startPhase(TrialStatus.VERDICT, now, now);
        trial.scheduleEnd(now);
        eventWriter.save(trial, "JUDGE_PHASE_NOTICE", TrialSpeaker.JUDGE,
                "최종 판결 투표가 종료되었습니다. 지금부터 AI 판사의 최종 판결을 발표하겠습니다.",
                Map.of("status", TrialStatus.VERDICT.name(), "phaseEndsAt", now.toString()));
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("status", TrialStatus.VERDICT.name());
        payload.put("verdictId", verdict.getId());
        payload.put("winnerSide", result.winnerSide() == null ? null : result.winnerSide().name());
        payload.put("aFaultRatio", result.aFaultRatio());
        payload.put("bFaultRatio", result.bFaultRatio());
        payload.put("publishedAt", now.toString());
        payload.put("generationStatus", "IDLE");
        eventWriter.save(trial, "VERDICT_ANNOUNCED", TrialSpeaker.JUDGE, result.summary(), payload);
    }

    private TrialEntity matching(Work work, TrialStatus status) {
        TrialEntity trial = trials.findByIdForUpdate(work.trialId()).orElse(null);
        return trial != null && trial.getStatus() == status
                && work.requestId().equals(trial.getGenerationRequestId())
                && "GENERATING".equals(trial.getGenerationStatus()) ? trial : null;
    }

    private void fail(Work work, OffsetDateTime now) {
        transactions.executeWithoutResult(status -> {
            TrialEntity trial = trials.findByIdForUpdate(work.trialId()).orElse(null);
            if (trial != null && work.requestId().equals(trial.getGenerationRequestId())) {
                markFailed(trial, now);
            }
        });
    }

    private void markFailed(TrialEntity trial, OffsetDateTime now) {
        String stage = trial.getGenerationStage();
        Integer turn = trial.getGenerationTurn();
        trial.failGeneration();
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("status", trial.getStatus().name());
        payload.put("stage", stage);
        payload.put("turn", turn);
        payload.put("totalTurns", timings.debateTurns());
        payload.put("generationStatus", "FAILED");
        payload.put("retryable", true);
        eventWriter.save(trial, "GENERATION_FAILED", TrialSpeaker.SYSTEM, null, payload);
    }

    private TrialSpeaker speaker(TrialSide side) {
        return side == TrialSide.A ? TrialSpeaker.A_LAWYER : TrialSpeaker.B_LAWYER;
    }

    private record Work(Long trialId, String requestId, String stage, Integer turn,
                        TrialSide side, String postContent,
                        Map<TrialSide, LawyerAiService.Statement> statements,
                        Map<TrialSide, String> arguments,
                        List<LawyerAiService.DebateTurn> turns) {
    }
}
