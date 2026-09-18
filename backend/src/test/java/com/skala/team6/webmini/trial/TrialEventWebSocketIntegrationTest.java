package com.skala.team6.webmini.trial;

import com.skala.team6.webmini.common.model.RelationshipType;
import com.skala.team6.webmini.common.model.TrialSide;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.trial.introduction-seconds=1",
        "app.trial.argument-seconds=1",
        "app.trial.debate-interval-seconds=1",
        "app.trial.voting-seconds=1",
        "app.trial.scheduler-enabled=false"
})
@EnabledIfEnvironmentVariable(named = "DB_INTEGRATION_TEST", matches = "true")
class TrialEventWebSocketIntegrationTest {
    @LocalServerPort int port;
    @Autowired UserRepository userRepository;
    @Autowired PostRepository postRepository;
    @Autowired TrialRepository trialRepository;
    @Autowired TrialPartyRepository partyRepository;
    @Autowired TrialStatementRepository statementRepository;
    @Autowired TrialEventRepository eventRepository;
    @Autowired VerdictRepository verdictRepository;
    @Autowired TrialStartService startService;
    @Autowired TrialPhaseService phaseService;
    @Autowired TrialGenerationService generationService;
    @Autowired @Qualifier("clientInboundChannel") AbstractMessageChannel inboundChannel;

    private WebSocketStompClient client;
    private StompSession first;
    private StompSession second;
    private ChannelInterceptor interceptor;
    private TrialEntity trial;

    @BeforeEach
    void setUp() {
        UserEntity creator = userRepository.save(new UserEntity(UUID.randomUUID().toString(), "작성자"));
        PostEntity post = postRepository.save(new PostEntity(
                creator, "동기화 재판", "사건 내용", RelationshipType.COUPLE, true));
        trial = trialRepository.save(new TrialEntity(post, creator));
        readyParty(TrialSide.A, "A 변론");
        readyParty(TrialSide.B, "B 변론");
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void cleanUp() {
        if (interceptor != null) {
            inboundChannel.removeInterceptor(interceptor);
        }
        if (first != null && first.isConnected()) {
            first.disconnect();
        }
        if (second != null && second.isConnected()) {
            second.disconnect();
        }
        if (client != null) {
            client.stop();
        }
        eventRepository.deleteAll();
        verdictRepository.deleteAll();
        statementRepository.deleteAll();
        partyRepository.deleteAll();
        trialRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void deliversTheSameOrderedPhaseEventsToTwoClients() throws Exception {
        CountDownLatch subscribed = new CountDownLatch(2);
        interceptor = new ChannelInterceptor() {
            @Override
            public void afterSendCompletion(Message<?> message, MessageChannel channel,
                                            boolean sent, Exception exception) {
                if (StompCommand.SUBSCRIBE.equals(StompHeaderAccessor.wrap(message).getCommand())) {
                    subscribed.countDown();
                }
            }
        };
        inboundChannel.addInterceptor(interceptor);
        first = connect();
        second = connect();
        String destination = "/topic/trials/" + trial.getId() + "/events";
        LinkedBlockingQueue<TrialEventMessage> firstEvents = subscribe(first, destination);
        LinkedBlockingQueue<TrialEventMessage> secondEvents = subscribe(second, destination);
        assertThat(subscribed.await(5, TimeUnit.SECONDS)).isTrue();

        startService.start(trial.getId());
        for (int i = 0; i < 3; i++) {
            TrialEntity current = trialRepository.findById(trial.getId()).orElseThrow();
            phaseService.advanceIfExpired(trial.getId(), current.getPhaseEndsAt());
        }
        for (int turn = 1; turn <= 4; turn++) {
            int expected = turn;
            generationService.tick(trial.getId(), OffsetDateTime.now().plusMinutes(5));
            await(() -> eventRepository.countByTrialIdAndEventTypeIn(
                    trial.getId(), List.of("A_DEBATE", "B_DEBATE")) == expected);
        }
        phaseService.advanceIfExpired(trial.getId(), OffsetDateTime.now().plusMinutes(5));
        generationService.tick(trial.getId(), trialRepository.findById(trial.getId())
                .orElseThrow().getPhaseEndsAt());
        await(() -> verdictRepository.findByTrialId(trial.getId()).isPresent());
        phaseService.advanceIfExpired(trial.getId(), OffsetDateTime.now().plusMinutes(5));

        List<TrialEventMessage> receivedFirst = receiveEvents(firstEvents, 23);
        List<TrialEventMessage> receivedSecond = receiveEvents(secondEvents, 23);
        receivedFirst.sort((left, right) -> Long.compare(left.sequence(), right.sequence()));
        receivedSecond.sort((left, right) -> Long.compare(left.sequence(), right.sequence()));
        assertThat(receivedSecond).isEqualTo(receivedFirst);
        assertThat(receivedFirst).extracting(TrialEventMessage::sequence)
                .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1, 23)
                        .boxed().toList());
        assertThat(receivedFirst).extracting(TrialEventMessage::type).containsExactly(
                "TRIAL_STARTED", "JUDGE_INTRODUCTION", "JUDGE_PHASE_NOTICE", "A_ARGUMENT",
                "JUDGE_PHASE_NOTICE", "B_ARGUMENT", "JUDGE_PHASE_NOTICE", "DEBATE_STARTED",
                "GENERATION_STARTED", "A_DEBATE", "GENERATION_STARTED", "B_DEBATE",
                "GENERATION_STARTED", "B_DEBATE", "GENERATION_STARTED", "A_DEBATE",
                "JUDGE_PHASE_NOTICE", "VOTING_STARTED", "GENERATION_STARTED",
                "JUDGE_PHASE_NOTICE", "VERDICT_ANNOUNCED", "JUDGE_PHASE_NOTICE", "TRIAL_ENDED");
        assertThat(eventRepository.findByTrialIdOrderBySequenceNoAsc(trial.getId())).hasSize(23);
    }

    private void readyParty(TrialSide side, String argument) {
        TrialPartyEntity party = new TrialPartyEntity(trial, side, side + "측");
        party.markReady();
        partyRepository.save(party);
        TrialStatementEntity statement = new TrialStatementEntity(
                party, "어제", "상황", "상대 행동", "내 행동", "대화", "해결");
        statement.updateArgumentDraft("사실", argument);
        statementRepository.save(statement);
    }

    private StompSession connect() throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("X-Demo-User-Id", UUID.randomUUID().toString());
        return client.connectAsync("ws://127.0.0.1:" + port + "/ws",
                new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() { })
                .get(5, TimeUnit.SECONDS);
    }

    private LinkedBlockingQueue<TrialEventMessage> subscribe(StompSession session, String destination) {
        LinkedBlockingQueue<TrialEventMessage> messages = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return TrialEventMessage.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((TrialEventMessage) payload);
            }
        });
        return messages;
    }

    private List<TrialEventMessage> receiveEvents(LinkedBlockingQueue<TrialEventMessage> queue, int count)
            throws InterruptedException {
        List<TrialEventMessage> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TrialEventMessage event = queue.poll(8, TimeUnit.SECONDS);
            assertThat(event).isNotNull();
            events.add(event);
        }
        return events;
    }

    private void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (condition.getAsBoolean()) { return; }
            Thread.sleep(20);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
