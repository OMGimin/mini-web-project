package com.skala.team6.webmini.trial;

import com.skala.team6.webmini.common.exception.ApiException;
import com.skala.team6.webmini.common.exception.ErrorCode;
import com.skala.team6.webmini.database.entity.TrialEntity;
import com.skala.team6.webmini.database.entity.TrialEventEntity;
import com.skala.team6.webmini.database.entity.TrialPartyEntity;
import com.skala.team6.webmini.database.repository.TrialPartyRepository;
import com.skala.team6.webmini.database.repository.TrialRepository;
import com.skala.team6.webmini.database.repository.ChatMessageRepository;
import com.skala.team6.webmini.database.repository.TrialEventRepository;
import com.skala.team6.webmini.common.model.TrialSide;
import com.skala.team6.webmini.common.model.TrialStatus;
import com.skala.team6.webmini.common.model.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TrialQueryService {
    private static final List<TrialStatus> PUBLIC_ACTIVE_STATUSES = List.of(
            TrialStatus.INTRODUCTION,
            TrialStatus.A_ARGUMENT,
            TrialStatus.B_ARGUMENT,
            TrialStatus.DEBATE,
            TrialStatus.VOTING,
            TrialStatus.VERDICT
    );
    private final TrialRepository trialRepository;
    private final TrialPartyRepository trialPartyRepository;
    private final TrialEventRepository trialEventRepository;
    private final ChatMessageRepository chatMessageRepository;

    public TrialQueryService(TrialRepository trialRepository,
                             TrialPartyRepository trialPartyRepository,
                             TrialEventRepository trialEventRepository,
                             ChatMessageRepository chatMessageRepository) {
        this.trialRepository = trialRepository;
        this.trialPartyRepository = trialPartyRepository;
        this.trialEventRepository = trialEventRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional(readOnly = true)
    public TrialDetail findDetail(Long trialId) {
        TrialEntity trial = trialRepository.findDetailById(trialId)
                .orElseThrow(() -> new ApiException(ErrorCode.TRIAL_NOT_FOUND));
        List<TrialPartyEntity> parties = trialPartyRepository.findByTrialIdOrderBySideAsc(trialId);
        return new TrialDetail(trial, parties);
    }

    @Transactional(readOnly = true)
    public TrialList findPublicActiveTrials(
            TrialStatus status,
            int page,
            int size
    ) {
        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<TrialEntity> trials;
        if (status == null) {
            trials = trialRepository.findByVisibilityAndStatusIn(
                    Visibility.PUBLIC, PUBLIC_ACTIVE_STATUSES, pageable);
        } else if (PUBLIC_ACTIVE_STATUSES.contains(status)) {
            trials = trialRepository.findByVisibilityAndStatus(
                    Visibility.PUBLIC, status, pageable);
        } else {
            trials = Page.empty(pageable);
        }

        List<Long> trialIds = trials.getContent().stream().map(TrialEntity::getId).toList();
        Map<Long, Map<TrialSide, TrialPartyEntity>> partiesByTrial = trialIds.isEmpty()
                ? Map.of()
                : trialPartyRepository.findByTrialIdInOrderByTrialIdAscSideAsc(trialIds).stream()
                        .collect(Collectors.groupingBy(
                                party -> party.getTrial().getId(),
                                Collectors.toMap(TrialPartyEntity::getSide, Function.identity())
                        ));
        List<TrialListEntry> entries = trials.getContent().stream()
                .map(trial -> toListEntry(trial, partiesByTrial.getOrDefault(trial.getId(), Map.of())))
                .toList();
        return new TrialList(entries, page, size, trials.getTotalElements(), trials.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TrialSnapshot findSnapshot(Long trialId) {
        TrialEntity trial = findPublicRecoverableTrial(trialId);
        return new TrialSnapshot(
                trial,
                trialEventRepository.findLatestSequenceByTrialId(trialId),
                chatMessageRepository.findLatestSequenceByTrialId(trialId),
                trialEventRepository.countByTrialIdAndEventTypeIn(trialId,
                        List.of("A_DEBATE", "B_DEBATE"))
        );
    }

    @Transactional(readOnly = true)
    public List<TrialEventEntity> findEventsAfter(Long trialId, long afterSequence) {
        findPublicRecoverableTrial(trialId);
        return trialEventRepository
                .findByTrialIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(
                        trialId, afterSequence);
    }

    private TrialEntity findPublicRecoverableTrial(Long trialId) {
        return trialRepository.findByIdAndVisibilityAndStatusNot(
                        trialId, Visibility.PUBLIC, TrialStatus.PREPARING)
                .orElseThrow(() -> new ApiException(ErrorCode.TRIAL_NOT_FOUND));
    }

    private TrialListEntry toListEntry(
            TrialEntity trial,
            Map<TrialSide, TrialPartyEntity> parties
    ) {
        TrialPartyEntity aParty = parties.get(TrialSide.A);
        TrialPartyEntity bParty = parties.get(TrialSide.B);
        return new TrialListEntry(
                trial,
                aParty == null ? null : aParty.getDisplayName(),
                bParty == null ? null : bParty.getDisplayName()
        );
    }

    public record TrialDetail(TrialEntity trial, List<TrialPartyEntity> parties) {
    }

    public record TrialList(
            List<TrialListEntry> entries,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record TrialListEntry(
            TrialEntity trial,
            String aDisplayName,
            String bDisplayName
    ) {
    }

    public record TrialSnapshot(
            TrialEntity trial,
            long latestEventSequence,
            long latestMessageSequence,
            long publishedDebateTurns
    ) {
    }
}
