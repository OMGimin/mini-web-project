package com.skala.team6.webmini.trial;

import com.skala.team6.webmini.common.model.TrialStatus;
import com.skala.team6.webmini.database.repository.TrialRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.trial", name = "scheduler-enabled",
        havingValue = "true", matchIfMissing = true)
public class TrialPhaseScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrialPhaseScheduler.class);
    private static final List<TrialStatus> ACTIVE_PHASES = List.of(
            TrialStatus.INTRODUCTION, TrialStatus.A_ARGUMENT, TrialStatus.B_ARGUMENT,
            TrialStatus.DEBATE, TrialStatus.VOTING, TrialStatus.VERDICT);

    private final TrialRepository trialRepository;
    private final TrialPhaseService phaseService;
    private final TrialGenerationService generationService;

    public TrialPhaseScheduler(TrialRepository trialRepository, TrialPhaseService phaseService,
                               TrialGenerationService generationService) {
        this.trialRepository = trialRepository;
        this.phaseService = phaseService;
        this.generationService = generationService;
    }

    @Scheduled(fixedDelayString = "${app.trial.scheduler-interval-millis:1000}")
    public void advanceExpiredTrials() {
        OffsetDateTime now = OffsetDateTime.now();
        for (Long trialId : trialRepository.findExpiredTrialIds(ACTIVE_PHASES, now)) {
            try {
                phaseService.advanceIfExpired(trialId, now);
            } catch (RuntimeException exception) {
                LOGGER.warn("Trial phase transition failed: trialId={}, errorType={}",
                        trialId, exception.getClass().getSimpleName());
            }
        }
        for (Long trialId : trialRepository.findTrialIdsByStatus(TrialStatus.DEBATE)) {
            tick(trialId, now);
        }
        for (Long trialId : trialRepository.findTrialIdsByStatus(TrialStatus.VOTING)) {
            tick(trialId, now);
        }
    }

    private void tick(Long trialId, OffsetDateTime now) {
        try {
            generationService.tick(trialId, now);
        } catch (RuntimeException exception) {
            LOGGER.warn("Trial generation scheduling failed: trialId={}, errorType={}",
                    trialId, exception.getClass().getSimpleName());
        }
    }
}
