package com.skala.team6.webmini.trial;

import com.skala.team6.webmini.ai.LawyerAiService;
import com.skala.team6.webmini.common.exception.ApiException;
import com.skala.team6.webmini.common.exception.ErrorCode;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "app.trial.scheduler-enabled=false")
@EnabledIfEnvironmentVariable(named = "DB_INTEGRATION_TEST", matches = "true")
class TrialGenerationRecoveryAcceptanceTest {
    @Autowired TrialGenerationService generation;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired TrialRepository trials;
    @Autowired TrialPartyRepository parties;
    @Autowired TrialStatementRepository statements;
    @Autowired TrialEventRepository events;
    @MockitoBean LawyerAiService lawyer;

    @AfterEach
    void cleanUp() {
        events.deleteAll();
        statements.deleteAll();
        parties.deleteAll();
        trials.deleteAll();
        posts.deleteAll();
        users.deleteAll();
    }

    @Test
    void staleResultCannotOverwriteRetriedTurn() throws Exception {
        UserEntity creator = users.save(new UserEntity(UUID.randomUUID().toString(), "작성자"));
        PostEntity post = posts.save(new PostEntity(
                creator, "재시도 재판", "사건 내용", RelationshipType.COUPLE, true));
        TrialEntity trial = trials.save(new TrialEntity(post, creator));
        OffsetDateTime now = OffsetDateTime.now();
        trial.startPhase(TrialStatus.DEBATE, now, now);
        trials.save(trial);
        for (TrialSide side : TrialSide.values()) {
            TrialPartyEntity party = parties.save(new TrialPartyEntity(trial, side, side + "측"));
            TrialStatementEntity statement = new TrialStatementEntity(
                    party, "어제", "상황", "상대 행동", "내 행동", "대화", "해결");
            statement.updateArgumentDraft("요약", side + "측 변론");
            statements.save(statement);
        }
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(lawyer.createDebateTurn(anyLong(), anyInt(), anyInt(), any(), anyString(),
                anyMap(), anyMap(), anyList())).thenAnswer(invocation -> {
                    if (calls.incrementAndGet() == 1) {
                        firstStarted.countDown();
                        assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();
                        return "늦게 도착한 이전 응답";
                    }
                    return "재시도에서 생성한 새 발언";
                });

        generation.tick(trial.getId(), now);
        assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
        generation.tick(trial.getId(), now);
        assertThat(calls.get()).isEqualTo(1);
        generation.tick(trial.getId(), now.plusSeconds(121));
        assertThat(trials.findById(trial.getId()).orElseThrow().getGenerationStatus())
                .isEqualTo("FAILED");
        generation.tick(trial.getId(), now.plusSeconds(122));
        assertThat(calls.get()).isEqualTo(1);
        assertThatThrownBy(() -> generation.retry(trial.getId(), UUID.randomUUID().toString()))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
                        .isEqualTo(ErrorCode.AI_RETRY_FORBIDDEN));
        generation.retry(trial.getId(), creator.getDemoKey());
        await(() -> events.countByTrialIdAndEventTypeIn(trial.getId(),
                List.of("A_DEBATE", "B_DEBATE")) == 1);
        releaseFirst.countDown();
        await(() -> calls.get() == 2);
        assertThat(events.findByTrialIdOrderBySequenceNoAsc(trial.getId()).stream()
                .filter(event -> "A_DEBATE".equals(event.getEventType()))
                .map(event -> event.getContent()).toList())
                .containsExactly("재시도에서 생성한 새 발언");
        assertThat(trials.findById(trial.getId()).orElseThrow().getGenerationStatus())
                .isEqualTo("IDLE");
    }

    private void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (condition.getAsBoolean()) { return; }
            Thread.sleep(20);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
