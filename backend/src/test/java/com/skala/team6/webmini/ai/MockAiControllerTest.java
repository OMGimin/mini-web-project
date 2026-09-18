package com.skala.team6.webmini.ai;

import com.skala.team6.webmini.common.config.AppAiProperties;
import com.skala.team6.webmini.common.exception.GlobalExceptionHandler;
import com.skala.team6.webmini.common.model.RelationshipType;
import com.skala.team6.webmini.common.model.TrialSide;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MockAiControllerTest {

    private final CapturingAiClient aiClient = new CapturingAiClient();
    private final MockAiController controller = new MockAiController(aiClient);
    private final StatementPayload statement = new StatementPayload(
            "어제 저녁",
            "연락 문제로 다투었습니다.",
            "답장이 늦었습니다.",
            "반복해서 연락했습니다.",
            "감정이 상한 채 대화가 끝났습니다.",
            "연락 기준을 합의하고 싶습니다."
    );

    @Test
    void passesRequestIdAndQuestionsPromptVersionToAiClient() {
        controller.createGuideQuestions(new LawyerQuestionsRequest(
                10L, TrialSide.A, RelationshipType.COUPLE, statement));

        assertThatCodeIsUuid(aiClient.context.aiRequestId());
        assertThat(aiClient.context.promptVersion()).isEqualTo("lawyer-questions-v1");
    }

    @Test
    void passesRequestIdAndArgumentPromptVersionToAiClient() {
        controller.createArgumentDraft(new LawyerArgumentRequest(
                10L,
                TrialSide.B,
                statement,
                List.of(new GuideAnswerItem(1, "질문", "답변"))
        ));

        assertThatCodeIsUuid(aiClient.context.aiRequestId());
        assertThat(aiClient.context.promptVersion()).isEqualTo("lawyer-argument-v1");
    }

    @Test
    void returnsQuestionsUsingApprovedJsonSchema() throws Exception {
        MockMvc mockMvc = mockMvcWithRealMockClient();

        mockMvc.perform(post("/api/v1/mock-ai/lawyer/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trialId": 10,
                                  "side": "A",
                                  "relationshipType": "COUPLE",
                                  "statement": {
                                    "incidentTime": "어제 저녁",
                                    "situation": "연락 문제로 다투었습니다.",
                                    "counterpartAction": "답장이 늦었습니다.",
                                    "ownAction": "반복해서 연락했습니다.",
                                    "afterConversation": "감정이 상한 채 대화가 끝났습니다.",
                                    "desiredResolution": "연락 기준을 합의하고 싶습니다."
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(3))
                .andExpect(jsonPath("$.data.questions[0].sequence").value(1))
                .andExpect(jsonPath("$.data.questions[0].question").value(
                        org.hamcrest.Matchers.containsString("A측")))
                .andExpect(jsonPath("$.data.schemaVersion").value("1.0"));
    }

    @Test
    void returnsArgumentUsingApprovedJsonSchema() throws Exception {
        MockMvc mockMvc = mockMvcWithRealMockClient();

        mockMvc.perform(post("/api/v1/mock-ai/lawyer/argument")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trialId": 10,
                                  "side": "B",
                                  "statement": {
                                    "incidentTime": "어제 저녁",
                                    "situation": "연락 문제로 다투었습니다.",
                                    "counterpartAction": "연락을 반복해서 받았습니다.",
                                    "ownAction": "답장을 미뤘습니다.",
                                    "afterConversation": "대화를 중단했습니다.",
                                    "desiredResolution": "연락 시간을 합의하고 싶습니다."
                                  },
                                  "guideAnswers": [
                                    {
                                      "sequence": 1,
                                      "question": "연락 기준이 있었나요?",
                                      "answer": "명확한 기준은 없었습니다."
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.factSummary").isString())
                .andExpect(jsonPath("$.data.argumentText").value(
                        org.hamcrest.Matchers.containsString("B측")))
                .andExpect(jsonPath("$.data.schemaVersion").value("1.0"));
    }

    @Test
    void returnsJudgeVerdictUsingApprovedJsonSchema() throws Exception {
        MockMvc mockMvc = mockMvcWithRealMockClient();

        mockMvc.perform(post("/api/v1/mock-ai/judge/verdict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trialId": 10,
                                  "postSummary": "연락 빈도로 발생한 갈등",
                                  "arguments": {
                                    "A": "A측의 1차 변론입니다.",
                                    "B": "B측의 1차 변론입니다."
                                  },
                                  "promptVersion": "judge-v1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.winnerSide").value("B"))
                .andExpect(jsonPath("$.data.aFaultRatio").value(60))
                .andExpect(jsonPath("$.data.bFaultRatio").value(40))
                .andExpect(jsonPath("$.data.summary").value(
                        org.hamcrest.Matchers.startsWith("주문:")))
                .andExpect(jsonPath("$.data.summary").value(
                        org.hamcrest.Matchers.hasLength(org.hamcrest.Matchers.greaterThanOrEqualTo(60))))
                .andExpect(jsonPath("$.data.grounds.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.grounds[0]").value(
                        org.hamcrest.Matchers.hasLength(org.hamcrest.Matchers.greaterThanOrEqualTo(30))))
                .andExpect(jsonPath("$.data.recommendations.a").isNotEmpty())
                .andExpect(jsonPath("$.data.recommendations.b").isNotEmpty())
                .andExpect(jsonPath("$.data.schemaVersion").value("1.0"))
                .andExpect(jsonPath("$.data.promptVersion").value("judge-v1"));
    }

    @Test
    void rejectsJudgeRequestWithoutBothFirstArguments() throws Exception {
        MockMvc mockMvc = mockMvcWithRealMockClient();

        mockMvc.perform(post("/api/v1/mock-ai/judge/verdict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trialId": 10,
                                  "postSummary": "연락 빈도로 발생한 갈등",
                                  "arguments": {"A": "A측 변론만 존재"},
                                  "promptVersion": "judge-v1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void retriesInvalidJudgeResponseAndReturnsContractError() {
        CapturingAiClient invalidClient = new CapturingAiClient();
        invalidClient.judgeResponse = new JudgeVerdictResponse(
                TrialSide.A, 80, 10, "", List.of(),
                new RecommendationPair("", ""), "0.1", "judge-v1");
        JudgeAiService service = new JudgeAiService(invalidClient,
                new AppAiProperties("mock", "judge-v1"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createVerdict(
                        10L, "요약", java.util.Map.of(
                                TrialSide.A, "A 변론", TrialSide.B, "B 변론")))
                .isInstanceOf(com.skala.team6.webmini.common.exception.ApiException.class)
                .hasMessageContaining("Mock AI 응답 검증");
        assertThat(invalidClient.verdictCalls).isEqualTo(2);
    }

    @Test
    void rejectsInvalidQuestionsRequest() throws Exception {
        MockMvc mockMvc = mockMvcWithRealMockClient();

        mockMvc.perform(post("/api/v1/mock-ai/lawyer/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trialId": 10,
                                  "side": "A",
                                  "relationshipType": "COUPLE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private MockMvc mockMvcWithRealMockClient() {
        MockAiClient realMockClient =
                new MockAiClient(new AppAiProperties("mock", "judge-v1"));
        return MockMvcBuilders.standaloneSetup(new MockAiController(
                        realMockClient,
                        new JudgeAiService(realMockClient,
                                new AppAiProperties("mock", "judge-v1"))))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void assertThatCodeIsUuid(String value) {
        assertThat(UUID.fromString(value).toString()).isEqualTo(value);
    }

    private static final class CapturingAiClient implements AiClient {
        private AiRequestContext context;
        private JudgeVerdictResponse judgeResponse;
        private int verdictCalls;

        @Override
        public LawyerQuestionsResponse createGuideQuestions(
                AiRequestContext context,
                LawyerQuestionsRequest request
        ) {
            this.context = context;
            return new LawyerQuestionsResponse(List.of(), "1.0");
        }

        @Override
        public LawyerArgumentResponse createArgumentDraft(
                AiRequestContext context,
                LawyerArgumentRequest request
        ) {
            this.context = context;
            return new LawyerArgumentResponse("요약", "변론", "1.0");
        }

        @Override
        public LawyerDebateResponse createDebateTurn(
                AiRequestContext context, LawyerDebateRequest request
        ) {
            return new LawyerDebateResponse("공방", "1.0", request.promptVersion());
        }

        @Override
        public JudgeVerdictResponse createVerdict(
                AiRequestContext context,
                JudgeVerdictRequest request
        ) {
            verdictCalls++;
            return judgeResponse;
        }
    }
}
