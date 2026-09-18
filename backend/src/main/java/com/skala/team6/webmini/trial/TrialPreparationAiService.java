package com.skala.team6.webmini.trial;

import com.skala.team6.webmini.ai.LawyerAiService;
import com.skala.team6.webmini.common.exception.ApiException;
import com.skala.team6.webmini.common.exception.ErrorCode;
import com.skala.team6.webmini.common.model.RelationshipType;
import com.skala.team6.webmini.common.model.TrialSide;
import com.skala.team6.webmini.common.model.TrialStatus;
import com.skala.team6.webmini.database.entity.AiGuideQuestionEntity;
import com.skala.team6.webmini.database.entity.TrialEntity;
import com.skala.team6.webmini.database.entity.TrialPartyEntity;
import com.skala.team6.webmini.database.entity.TrialStatementEntity;
import com.skala.team6.webmini.database.repository.AiGuideQuestionRepository;
import com.skala.team6.webmini.database.repository.TrialPartyRepository;
import com.skala.team6.webmini.database.repository.TrialRepository;
import com.skala.team6.webmini.database.repository.TrialStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
public class TrialPreparationAiService {
    private final Object[] inFlightLocks = java.util.stream.IntStream.range(0, 64)
            .mapToObj(index -> new Object()).toArray();
    private final TrialRepository trials;
    private final TrialPartyRepository parties;
    private final TrialStatementRepository statements;
    private final AiGuideQuestionRepository questions;
    private final LawyerAiService lawyer;
    private final TransactionTemplate transactions;

    public TrialPreparationAiService(TrialRepository trials, TrialPartyRepository parties,
                                     TrialStatementRepository statements,
                                     AiGuideQuestionRepository questions, LawyerAiService lawyer,
                                     PlatformTransactionManager transactionManager) {
        this.trials = trials;
        this.parties = parties;
        this.statements = statements;
        this.questions = questions;
        this.lawyer = lawyer;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public List<AiGuideQuestionEntity> createGuideQuestions(Long trialId, TrialSide side) {
        synchronized (lockFor(trialId, side)) {
            return createGuideQuestionsOnce(trialId, side);
        }
    }

    private List<AiGuideQuestionEntity> createGuideQuestionsOnce(Long trialId, TrialSide side) {
        Input input = transactions.execute(status -> readInput(trialId, side));
        if (!input.existingQuestions().isEmpty()) { return input.existingQuestions(); }
        List<LawyerAiService.GuideQuestion> generated = lawyer.createGuideQuestions(
                trialId, side, input.relationshipType(), input.statement());
        return transactions.execute(status -> {
            TrialPartyEntity party = findCurrentParty(trialId, side, input.statement());
            List<AiGuideQuestionEntity> existing = questions.findByTrialPartyIdOrderBySequenceNoAsc(party.getId());
            if (!existing.isEmpty()) { return existing; }
            return questions.saveAll(generated.stream().map(question -> new AiGuideQuestionEntity(
                    party, question.sequence(), question.question())).toList());
        });
    }

    public TrialStatementEntity createArgumentDraft(Long trialId, TrialSide side) {
        synchronized (lockFor(trialId, side)) {
            return createArgumentDraftOnce(trialId, side);
        }
    }

    private TrialStatementEntity createArgumentDraftOnce(Long trialId, TrialSide side) {
        Input input = transactions.execute(status -> readInput(trialId, side));
        if (input.existingDraft() != null) { return input.existingDraft(); }
        LawyerAiService.ArgumentDraft generated = lawyer.createArgumentDraft(
                trialId, side, input.statement(), input.answers());
        return transactions.execute(status -> {
            TrialPartyEntity party = findCurrentParty(trialId, side, input.statement());
            TrialStatementEntity statement = findStatement(party);
            if (statement.getFactSummary() != null && statement.getArgumentText() != null) {
                return statement;
            }
            if (!input.answers().equals(readAnswers(party))) {
                throw new ApiException(ErrorCode.PREPARATION_CHANGED);
            }
            statement.updateArgumentDraft(generated.factSummary(), generated.argumentText());
            return statements.save(statement);
        });
    }

    private Input readInput(Long trialId, TrialSide side) {
        TrialEntity trial = requirePreparing(trialId);
        TrialPartyEntity party = findParty(trialId, side);
        TrialStatementEntity statement = findStatement(party);
        List<AiGuideQuestionEntity> existing = questions.findByTrialPartyIdOrderBySequenceNoAsc(party.getId());
        return new Input(toStatement(statement), trial.getPost().getRelationshipType(), existing,
                readAnswers(party), hasText(statement.getFactSummary()) && hasText(statement.getArgumentText())
                        ? statement : null);
    }

    private TrialPartyEntity findCurrentParty(Long trialId, TrialSide side,
                                              LawyerAiService.Statement expected) {
        requirePreparing(trialId);
        TrialPartyEntity party = findParty(trialId, side);
        TrialStatementEntity current = findStatement(party);
        if (!expected.equals(toStatement(current)) || current.getConfirmedAt() != null) {
            throw new ApiException(ErrorCode.PREPARATION_CHANGED);
        }
        return party;
    }

    private TrialEntity requirePreparing(Long trialId) {
        TrialEntity trial = trials.findByIdForUpdate(trialId)
                .orElseThrow(() -> new ApiException(ErrorCode.TRIAL_NOT_FOUND));
        if (trial.getStatus() != TrialStatus.PREPARING) {
            throw new ApiException(ErrorCode.TRIAL_NOT_PREPARING);
        }
        return trial;
    }

    private TrialPartyEntity findParty(Long trialId, TrialSide side) {
        return parties.findByTrialIdAndSide(trialId, side)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TRIAL_SIDE));
    }

    private TrialStatementEntity findStatement(TrialPartyEntity party) {
        return statements.findByTrialPartyId(party.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.ARGUMENT_DRAFT_REQUIRED));
    }

    private List<LawyerAiService.GuideAnswer> readAnswers(TrialPartyEntity party) {
        return questions.findByTrialPartyIdOrderBySequenceNoAsc(party.getId()).stream()
                .filter(question -> hasText(question.getAnswer()))
                .map(question -> new LawyerAiService.GuideAnswer(
                        question.getSequenceNo(), question.getQuestion(), question.getAnswer()))
                .toList();
    }

    private LawyerAiService.Statement toStatement(TrialStatementEntity statement) {
        return new LawyerAiService.Statement(statement.getIncidentTime(), statement.getSituation(),
                statement.getCounterpartAction(), statement.getOwnAction(),
                statement.getAfterConversation(), statement.getDesiredResolution());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Object lockFor(Long trialId, TrialSide side) {
        return inFlightLocks[Math.floorMod(java.util.Objects.hash(trialId, side), inFlightLocks.length)];
    }

    private record Input(LawyerAiService.Statement statement, RelationshipType relationshipType,
                         List<AiGuideQuestionEntity> existingQuestions,
                         List<LawyerAiService.GuideAnswer> answers,
                         TrialStatementEntity existingDraft) {
    }
}
