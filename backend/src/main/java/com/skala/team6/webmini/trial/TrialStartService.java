package com.skala.team6.webmini.trial;

import com.skala.team6.webmini.common.config.TrialTimingProperties;
import com.skala.team6.webmini.common.exception.ApiException;
import com.skala.team6.webmini.common.exception.ErrorCode;
import com.skala.team6.webmini.common.model.TrialSpeaker;
import com.skala.team6.webmini.common.model.TrialStatus;
import com.skala.team6.webmini.database.entity.TrialEntity;
import com.skala.team6.webmini.database.entity.TrialPartyEntity;
import com.skala.team6.webmini.database.repository.TrialPartyRepository;
import com.skala.team6.webmini.database.repository.TrialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class TrialStartService {
    private final TrialRepository trialRepository;
    private final TrialPartyRepository trialPartyRepository;
    private final TrialTimingProperties timings;
    private final TrialEventWriter eventWriter;

    public TrialStartService(
            TrialRepository trialRepository,
            TrialPartyRepository trialPartyRepository,
            TrialTimingProperties timings,
            TrialEventWriter eventWriter
    ) {
        this.trialRepository = trialRepository;
        this.trialPartyRepository = trialPartyRepository;
        this.timings = timings;
        this.eventWriter = eventWriter;
    }

    @Transactional
    public StartedTrial start(Long trialId) {
        TrialEntity trial = trialRepository.findByIdForUpdate(trialId)
                .orElseThrow(() -> new ApiException(ErrorCode.TRIAL_NOT_FOUND));
        if (trial.getStatus() != TrialStatus.PREPARING) {
            throw new ApiException(ErrorCode.TRIAL_ALREADY_STARTED);
        }

        var parties = trialPartyRepository.findByTrialIdOrderBySideAsc(trialId);
        boolean bothReady = parties.size() == 2
                && parties.stream().allMatch(TrialPartyEntity::isReady);
        if (!bothReady) {
            throw new ApiException(ErrorCode.PARTIES_NOT_READY);
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime phaseEndsAt = now.plusSeconds(timings.introductionSeconds());
        OffsetDateTime scheduledEndAt = phaseEndsAt
                .plusSeconds(timings.argumentSeconds() * 2
                        + timings.debateIntervalSeconds() * timings.debateTurns()
                        + timings.votingSeconds());
        trial.startPhase(TrialStatus.INTRODUCTION, now, phaseEndsAt);
        trial.scheduleEnd(scheduledEndAt);

        eventWriter.save(trial, "TRIAL_STARTED", TrialSpeaker.SYSTEM, null, Map.of(
                "status", TrialStatus.INTRODUCTION.name(),
                "startedAt", now.toString(),
                "phaseEndsAt", phaseEndsAt.toString(),
                "scheduledEndAt", scheduledEndAt.toString()));
        eventWriter.save(trial, "JUDGE_INTRODUCTION", TrialSpeaker.JUDGE,
                "지금부터 재판을 시작합니다. 사건의 주요 내용을 확인한 뒤 양측의 주장을 듣겠습니다. "
                        + trial.getPost().getTitle(), Map.of(
                        "status", TrialStatus.INTRODUCTION.name(),
                        "phaseEndsAt", phaseEndsAt.toString()));
        return new StartedTrial(trial, 2);
    }

    public record StartedTrial(TrialEntity trial, long latestEventSequence) {
    }
}
