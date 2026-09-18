package com.skala.team6.webmini;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.skala.team6.webmini.database.repository.PostRepository;
import com.skala.team6.webmini.database.repository.UserRepository;
import com.skala.team6.webmini.database.repository.TrialPartyRepository;
import com.skala.team6.webmini.database.repository.TrialRepository;
import com.skala.team6.webmini.database.repository.AiGuideQuestionRepository;
import com.skala.team6.webmini.database.repository.TrialStatementRepository;
import com.skala.team6.webmini.database.repository.ChatMessageRepository;
import com.skala.team6.webmini.database.repository.TrialEventRepository;
import com.skala.team6.webmini.database.repository.VerdictRepository;
import com.skala.team6.webmini.database.repository.VoteRepository;
import com.skala.team6.webmini.trial.TrialGenerationService;
import com.skala.team6.webmini.trial.TrialPreparationAiService;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration")
class WebMiniBackendApplicationTests {

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private PostRepository postRepository;
    @MockitoBean
    private TrialRepository trialRepository;
    @MockitoBean
    private TrialPartyRepository trialPartyRepository;
    @MockitoBean
    private AiGuideQuestionRepository aiGuideQuestionRepository;
    @MockitoBean
    private TrialStatementRepository trialStatementRepository;
    @MockitoBean
    private ChatMessageRepository chatMessageRepository;
    @MockitoBean
    private TrialEventRepository trialEventRepository;
    @MockitoBean
    private VerdictRepository verdictRepository;
    @MockitoBean
    private VoteRepository voteRepository;
    @MockitoBean
    private TrialGenerationService trialGenerationService;
    @MockitoBean
    private TrialPreparationAiService trialPreparationAiService;

    @Test
    void contextLoads() {
    }

}
