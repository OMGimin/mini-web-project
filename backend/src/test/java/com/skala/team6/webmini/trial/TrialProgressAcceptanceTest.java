package com.skala.team6.webmini.trial;

import com.skala.team6.webmini.common.model.RelationshipType;
import com.skala.team6.webmini.common.model.TrialSide;
import com.skala.team6.webmini.common.model.TrialStatus;
import com.skala.team6.webmini.database.entity.PostEntity;
import com.skala.team6.webmini.database.entity.TrialEntity;
import com.skala.team6.webmini.database.entity.TrialPartyEntity;
import com.skala.team6.webmini.database.entity.TrialStatementEntity;
import com.skala.team6.webmini.database.entity.UserEntity;
import com.skala.team6.webmini.database.repository.PostRepository;
import com.skala.team6.webmini.database.repository.TrialEventRepository;
import com.skala.team6.webmini.database.repository.TrialPartyRepository;
import com.skala.team6.webmini.database.repository.TrialRepository;
import com.skala.team6.webmini.database.repository.TrialStatementRepository;
import com.skala.team6.webmini.database.repository.UserRepository;
import com.skala.team6.webmini.database.repository.VerdictRepository;
import com.skala.team6.webmini.common.model.TrialSpeaker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.trial.scheduler-enabled=false")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_INTEGRATION_TEST", matches = "true")
class TrialProgressAcceptanceTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PostRepository postRepository;
    @Autowired TrialRepository trialRepository;
    @Autowired TrialPartyRepository trialPartyRepository;
    @Autowired TrialEventRepository trialEventRepository;
    @Autowired TrialStatementRepository trialStatementRepository;
    @Autowired VerdictRepository verdictRepository;
    @Autowired TrialPhaseService trialPhaseService;
    @Autowired TrialGenerationService generationService;
    @MockitoBean SimpMessagingTemplate messagingTemplate;

    @AfterEach
    void cleanUp() {
        trialEventRepository.deleteAll();
        verdictRepository.deleteAll();
        trialStatementRepository.deleteAll();
        trialPartyRepository.deleteAll();
        trialRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void startsReadyTrialAndPublishesPersistedEventsInSequence() throws Exception {
        UserEntity user = userRepository.save(new UserEntity(UUID.randomUUID().toString(), "작성자"));
        PostEntity post = postRepository.save(new PostEntity(
                user, "재판 제목", "재판 내용", RelationshipType.COUPLE, true));
        TrialEntity trial = trialRepository.save(new TrialEntity(post, user));
        TrialPartyEntity a = new TrialPartyEntity(trial, TrialSide.A, "A측");
        TrialPartyEntity b = new TrialPartyEntity(trial, TrialSide.B, "B측");
        a.markReady();
        b.markReady();
        trialPartyRepository.save(a);
        trialPartyRepository.save(b);

        mockMvc.perform(post("/api/v1/trials/{trialId}/start", trial.getId())
                        .header("X-Demo-User-Id", user.getDemoKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INTRODUCTION"))
                .andExpect(jsonPath("$.data.phaseStartedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.phaseEndsAt").isNotEmpty())
                .andExpect(jsonPath("$.data.latestEventSequence").value(2));

        TrialEntity started = trialRepository.findById(trial.getId()).orElseThrow();
        assertThat(started.getStatus()).isEqualTo(TrialStatus.INTRODUCTION);
        assertThat(started.getPhaseStartedAt()).isNotNull();
        assertThat(started.getPhaseEndsAt()).isNotNull();
        assertThat(started.getScheduledEndAt()).isNotNull();
        assertThat(Duration.between(started.getPhaseStartedAt(), started.getScheduledEndAt()))
                .isEqualTo(Duration.ofSeconds(74));
        assertThat(trialEventRepository.findByTrialIdOrderBySequenceNoAsc(trial.getId()))
                .extracting("sequenceNo", "eventType")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, "TRIAL_STARTED"),
                        org.assertj.core.groups.Tuple.tuple(2L, "JUDGE_INTRODUCTION"));
        verify(messagingTemplate, timeout(1000).times(2))
                .convertAndSend(eq("/topic/trials/" + trial.getId() + "/events"),
                        org.mockito.ArgumentMatchers.any(TrialEventMessage.class));
    }

    @Test
    void advancesAllPhasesAndPersistsArgumentsVerdictAndEndEvents() throws Exception {
        UserEntity user = userRepository.save(new UserEntity(UUID.randomUUID().toString(), "작성자"));
        PostEntity post = postRepository.save(new PostEntity(
                user, "재판 제목", "재판 내용", RelationshipType.COUPLE, true));
        TrialEntity trial = trialRepository.save(new TrialEntity(post, user));
        readyParty(trial, TrialSide.A, "A측", "A측 최종 변론");
        readyParty(trial, TrialSide.B, "B측", "B측 최종 변론");

        // 시작 이후 각 phaseEndsAt을 기준으로 스케줄러와 동일한 전이를 직접 실행한다.
        startService.start(trial.getId());
        for (int i = 0; i < 3; i++) {
            TrialEntity current = trialRepository.findById(trial.getId()).orElseThrow();
            trialPhaseService.advanceIfExpired(trial.getId(), current.getPhaseEndsAt());
        }

        // 실제 생성이 저장되기 전에는 시간이 지나도 투표로 넘어갈 수 없다.
        trialPhaseService.advanceIfExpired(trial.getId(), OffsetDateTime.now().plusMinutes(10));
        assertThat(trialRepository.findById(trial.getId()).orElseThrow().getStatus())
                .isEqualTo(TrialStatus.DEBATE);
        for (int turn = 1; turn <= 4; turn++) {
            int expected = turn;
            generationService.tick(trial.getId(), OffsetDateTime.now().plusMinutes(10));
            await(() -> trialEventRepository.countByTrialIdAndEventTypeIn(
                    trial.getId(), java.util.List.of("A_DEBATE", "B_DEBATE")) == expected);
        }
        trialPhaseService.advanceIfExpired(trial.getId(), OffsetDateTime.now().plusMinutes(10));
        assertThat(trialRepository.findById(trial.getId()).orElseThrow().getStatus())
                .isEqualTo(TrialStatus.VOTING);
        generationService.tick(trial.getId(), trialRepository.findById(trial.getId())
                .orElseThrow().getPhaseEndsAt());
        await(() -> verdictRepository.findByTrialId(trial.getId()).isPresent());
        trialPhaseService.advanceIfExpired(trial.getId(), OffsetDateTime.now().plusMinutes(10));

        TrialEntity ended = trialRepository.findById(trial.getId()).orElseThrow();
        assertThat(ended.getStatus()).isEqualTo(TrialStatus.ENDED);
        assertThat(verdictRepository.findByTrialId(trial.getId())).isPresent();
        var events = trialEventRepository.findByTrialIdOrderBySequenceNoAsc(trial.getId());
        assertThat(events).extracting("eventType")
                .contains("DEBATE_STARTED", "VOTING_STARTED", "VERDICT_ANNOUNCED", "TRIAL_ENDED")
                .containsSequence("GENERATION_STARTED", "A_DEBATE")
                .containsSequence("GENERATION_STARTED", "B_DEBATE");
        assertThat(events).filteredOn(event -> event.getEventType().endsWith("_DEBATE"))
                .extracting("speaker")
                .containsExactly(TrialSpeaker.A_LAWYER, TrialSpeaker.B_LAWYER,
                        TrialSpeaker.B_LAWYER, TrialSpeaker.A_LAWYER);
    }

    @Autowired TrialStartService startService;

    private void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (condition.getAsBoolean()) { return; }
            Thread.sleep(20);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private TrialPartyEntity readyParty(TrialEntity trial, TrialSide side,
                                        String displayName, String argument) {
        TrialPartyEntity party = new TrialPartyEntity(trial, side, displayName);
        party.markReady();
        trialPartyRepository.save(party);
        TrialStatementEntity statement = new TrialStatementEntity(
                party, "어제", "상황", "상대 행동", "내 행동", "대화", "해결");
        statement.updateArgumentDraft("사실", argument);
        trialStatementRepository.save(statement);
        return party;
    }
}
