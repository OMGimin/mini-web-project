package com.skala.team6.webmini.trial;

import com.skala.team6.webmini.common.exception.ApiException;
import com.skala.team6.webmini.common.exception.ErrorCode;
import com.skala.team6.webmini.common.model.TrialSide;
import com.skala.team6.webmini.database.entity.TrialPartyEntity;
import com.skala.team6.webmini.database.entity.TrialStatementEntity;
import com.skala.team6.webmini.database.repository.TrialPartyRepository;
import com.skala.team6.webmini.database.repository.TrialRepository;
import com.skala.team6.webmini.database.repository.TrialStatementRepository;
import com.skala.team6.webmini.database.repository.AiGuideQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrialStatementService {
    private final TrialRepository trialRepository;
    private final TrialPartyRepository trialPartyRepository;
    private final TrialStatementRepository trialStatementRepository;
    private final AiGuideQuestionRepository questionRepository;

    public TrialStatementService(
            TrialRepository trialRepository,
            TrialPartyRepository trialPartyRepository,
            TrialStatementRepository trialStatementRepository,
            AiGuideQuestionRepository questionRepository
    ) {
        this.trialRepository = trialRepository;
        this.trialPartyRepository = trialPartyRepository;
        this.trialStatementRepository = trialStatementRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional
    public TrialStatementEntity save(Long trialId, TrialSide side, StatementRequest request) {
        var trial = trialRepository.findByIdForUpdate(trialId)
                .orElseThrow(() -> new ApiException(ErrorCode.TRIAL_NOT_FOUND));
        if (trial.getStatus() != com.skala.team6.webmini.common.model.TrialStatus.PREPARING) {
            throw new ApiException(ErrorCode.TRIAL_NOT_PREPARING);
        }
        TrialPartyEntity party = findParty(trialId, side);
        TrialStatementEntity statement = trialStatementRepository.findByTrialPartyId(party.getId())
                .orElseGet(() -> new TrialStatementEntity(
                        party,
                        request.incidentTime().trim(),
                        request.situation().trim(),
                        request.counterpartAction().trim(),
                        request.ownAction().trim(),
                        request.afterConversation().trim(),
                        request.desiredResolution().trim()
                ));
        boolean changed = !request.incidentTime().trim().equals(statement.getIncidentTime())
                || !request.situation().trim().equals(statement.getSituation())
                || !request.counterpartAction().trim().equals(statement.getCounterpartAction())
                || !request.ownAction().trim().equals(statement.getOwnAction())
                || !request.afterConversation().trim().equals(statement.getAfterConversation())
                || !request.desiredResolution().trim().equals(statement.getDesiredResolution());
        if (changed) {
            questionRepository.deleteAll(questionRepository.findByTrialPartyIdOrderBySequenceNoAsc(party.getId()));
            statement.clearArgumentDraft();
            statement.unconfirm();
            party.markNotReady();
            trialPartyRepository.save(party);
        }
        statement.updateStatement(
                request.incidentTime().trim(),
                request.situation().trim(),
                request.counterpartAction().trim(),
                request.ownAction().trim(),
                request.afterConversation().trim(),
                request.desiredResolution().trim()
        );
        return trialStatementRepository.save(statement);
    }

    private TrialPartyEntity findParty(Long trialId, TrialSide side) {
        if (!trialRepository.existsById(trialId)) {
            throw new ApiException(ErrorCode.TRIAL_NOT_FOUND);
        }
        return trialPartyRepository.findByTrialIdAndSide(trialId, side)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TRIAL_SIDE));
    }
}
