package com.skala.team6.webmini;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.skala.team6.webmini.post.PostService;
import com.skala.team6.webmini.database.repository.PostRepository;
import com.skala.team6.webmini.database.repository.UserRepository;
import com.skala.team6.webmini.trial.TrialCreationService;
import com.skala.team6.webmini.trial.TrialQueryService;
import com.skala.team6.webmini.trial.GuideAnswerService;
import com.skala.team6.webmini.trial.TrialArgumentService;
import com.skala.team6.webmini.trial.TrialStartService;
import com.skala.team6.webmini.trial.TrialStatementService;
import com.skala.team6.webmini.trial.TrialPreparationAiService;
import com.skala.team6.webmini.trial.TrialChatQueryService;
import com.skala.team6.webmini.trial.TrialChatService;
import com.skala.team6.webmini.trial.TrialEventWriter;
import com.skala.team6.webmini.trial.TrialPhaseService;
import com.skala.team6.webmini.trial.TrialGenerationService;
import com.skala.team6.webmini.trial.TrialVoteService;
import com.skala.team6.webmini.trial.TrialResultService;
import com.skala.team6.webmini.database.repository.VoteRepository;
import com.skala.team6.webmini.common.exception.ApiException;
import com.skala.team6.webmini.common.exception.ErrorCode;
import com.skala.team6.webmini.common.model.TrialSide;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.autoconfigure.exclude="
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "app.trial.scheduler-enabled=false"})
@AutoConfigureMockMvc
class ApiDocumentationSmokeTest {

    @MockitoBean
    private PostService postService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private PostRepository postRepository;
    @MockitoBean
    private TrialCreationService trialCreationService;
    @MockitoBean
    private TrialQueryService trialQueryService;
    @MockitoBean
    private TrialStatementService trialStatementService;
    @MockitoBean
    private GuideAnswerService guideAnswerService;
    @MockitoBean
    private TrialArgumentService trialArgumentService;
    @MockitoBean
    private TrialStartService trialStartService;
    @MockitoBean
    private TrialPreparationAiService trialPreparationAiService;
    @MockitoBean
    private TrialChatQueryService trialChatQueryService;
    @MockitoBean
    private TrialChatService trialChatService;
    @MockitoBean
    private TrialEventWriter trialEventWriter;
    @MockitoBean
    private TrialPhaseService trialPhaseService;
    @MockitoBean
    private TrialGenerationService trialGenerationService;
    @MockitoBean
    private TrialVoteService trialVoteService;
    @MockitoBean
    private TrialResultService trialResultService;
    @MockitoBean
    private VoteRepository voteRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/posts']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/trials/{trialId}/parties/{side}/statement']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/mock-ai/lawyer/questions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/mock-ai/lawyer/argument']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/mock-ai/judge/verdict']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.demoUserHeader").exists());
    }

    @Test
    void returnsDemoUserRequiredWhenHeaderMissing() throws Exception {
        mockMvc.perform(put("/api/v1/trials/10/parties/A/statement")
                        .contentType("application/json")
                        .content("""
                                {
                                  "incidentTime": "어제 저녁",
                                  "situation": "연락 문제로 다투었습니다.",
                                  "counterpartAction": "답장이 늦었습니다.",
                                  "ownAction": "반복해서 연락했습니다.",
                                  "afterConversation": "감정이 상한 채 대화가 끝났습니다.",
                                  "desiredResolution": "연락 기준을 합의하고 싶습니다."
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEMO_USER_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/api/v1/trials/10/parties/A/statement"));
    }

    @Test
    void convertsMockAiFailureToCommonErrorResponse() throws Exception {
        when(trialPreparationAiService.createGuideQuestions(10L, TrialSide.A))
                .thenThrow(new ApiException(ErrorCode.MOCK_AI_RESPONSE_INVALID));

        mockMvc.perform(post("/api/v1/trials/10/parties/A/guide-questions"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("MOCK_AI_RESPONSE_INVALID"))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/trials/10/parties/A/guide-questions"));
    }
}
