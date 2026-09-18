package com.skala.team6.webmini.trial;

import com.skala.team6.webmini.common.config.TrialTimingProperties;
import com.skala.team6.webmini.common.model.TrialSide;
import com.skala.team6.webmini.common.model.TrialSpeaker;
import com.skala.team6.webmini.common.model.TrialStatus;
import com.skala.team6.webmini.database.entity.TrialEntity;
import com.skala.team6.webmini.database.entity.TrialPartyEntity;
import com.skala.team6.webmini.database.entity.TrialStatementEntity;
import com.skala.team6.webmini.database.repository.TrialPartyRepository;
import com.skala.team6.webmini.database.repository.TrialRepository;
import com.skala.team6.webmini.database.repository.TrialStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class TrialPhaseService {
    private final TrialRepository trialRepository;
    private final TrialPartyRepository partyRepository;
    private final TrialStatementRepository statementRepository;
    private final TrialTimingProperties timings;
    private final TrialEventWriter eventWriter;

    public TrialPhaseService(TrialRepository trialRepository,
                             TrialPartyRepository partyRepository,
                             TrialStatementRepository statementRepository,
                             TrialTimingProperties timings,
                             TrialEventWriter eventWriter) {
        this.trialRepository = trialRepository;
        this.partyRepository = partyRepository;
        this.statementRepository = statementRepository;
        this.timings = timings;
        this.eventWriter = eventWriter;
    }

    @Transactional
    public boolean advanceIfExpired(Long trialId, OffsetDateTime now) {
        TrialEntity trial = trialRepository.findByIdForUpdate(trialId).orElse(null);
        if (trial == null || trial.getPhaseEndsAt() == null || trial.getPhaseEndsAt().isAfter(now)) {
            return false;
        }
        switch (trial.getStatus()) {
            case INTRODUCTION -> openArgument(trial, TrialSide.A, now);
            case A_ARGUMENT -> openArgument(trial, TrialSide.B, now);
            case B_ARGUMENT -> openDebate(trial, now);
            case DEBATE -> {
                if (!"IDLE".equals(trial.getGenerationStatus()) ||
                        eventWriter.countByTrialAndTypes(trialId, "A_DEBATE", "B_DEBATE")
                                != timings.debateTurns()) {
                    return false;
                }
                openVoting(trial, now);
            }
            case VERDICT -> endTrial(trial, now);
            default -> { return false; }
        }
        return true;
    }

    private void openArgument(TrialEntity trial, TrialSide side, OffsetDateTime now) {
        TrialStatus status = side == TrialSide.A ? TrialStatus.A_ARGUMENT : TrialStatus.B_ARGUMENT;
        OffsetDateTime endsAt = now.plusSeconds(timings.argumentSeconds());
        trial.startPhase(status, now, endsAt);
        announcePhase(trial, side == TrialSide.A
                        ? "이제 A측의 주장을 듣겠습니다. A측 AI 변호사는 핵심 입장을 말씀해 주세요."
                        : "A측의 주장을 확인했습니다. 이제 B측의 주장을 듣겠습니다.", status, endsAt);
        TrialPartyEntity party = partyRepository.findByTrialIdAndSide(trial.getId(), side).orElseThrow();
        TrialStatementEntity statement = statementRepository.findByTrialPartyId(party.getId()).orElseThrow();
        TrialSpeaker speaker = side == TrialSide.A ? TrialSpeaker.A_LAWYER : TrialSpeaker.B_LAWYER;
        eventWriter.save(trial, status.name(), speaker, statement.getArgumentText(), Map.of(
                "side", side.name(), "status", status.name(), "phaseEndsAt", endsAt.toString()));
    }

    private void openDebate(TrialEntity trial, OffsetDateTime now) {
        trial.startPhase(TrialStatus.DEBATE, now, now);
        announcePhase(trial, "양측의 주장을 모두 확인했습니다. 지금부터 상호 변론을 진행하겠습니다.",
                TrialStatus.DEBATE, now);
        eventWriter.save(trial, "DEBATE_STARTED", TrialSpeaker.SYSTEM, null, Map.of(
                "status", TrialStatus.DEBATE.name(), "phaseEndsAt", now.toString(),
                "totalTurns", timings.debateTurns()));
    }

    private void openVoting(TrialEntity trial, OffsetDateTime now) {
        OffsetDateTime endsAt = now.plusSeconds(timings.votingSeconds());
        trial.startPhase(TrialStatus.VOTING, now, endsAt);
        announcePhase(trial, "양측의 상호 변론이 마무리되었습니다. 지금부터 최종 판결 투표를 시작하겠습니다.",
                TrialStatus.VOTING, endsAt);
        eventWriter.save(trial, "VOTING_STARTED", TrialSpeaker.SYSTEM, null, Map.of(
                "status", TrialStatus.VOTING.name(), "voteStartedAt", now.toString(),
                "voteEndsAt", endsAt.toString(), "allowedSides", new String[]{"A", "B"}));
    }

    private void endTrial(TrialEntity trial, OffsetDateTime now) {
        trial.complete(now);
        announcePhase(trial, "이상으로 재판을 종료하겠습니다. 참여해 주신 배심원 여러분께 감사드립니다.",
                TrialStatus.ENDED, now);
        eventWriter.save(trial, "TRIAL_ENDED", TrialSpeaker.SYSTEM, null, Map.of(
                "status", TrialStatus.ENDED.name(), "endedAt", now.toString(),
                "resultPath", "/api/v1/trials/" + trial.getId() + "/results"));
    }

    private void announcePhase(TrialEntity trial, String content, TrialStatus status, OffsetDateTime endsAt) {
        eventWriter.save(trial, "JUDGE_PHASE_NOTICE", TrialSpeaker.JUDGE, content, Map.of(
                "status", status.name(), "phaseEndsAt", endsAt.toString()));
    }
}
