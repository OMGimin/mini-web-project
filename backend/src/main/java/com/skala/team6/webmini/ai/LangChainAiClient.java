package com.skala.team6.webmini.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

// langchain 모드에서만 선택되는 어댑터다. Spring은 Python 서비스에 요청하고 Python이 LLM을 호출한다.
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "langchain")
public class LangChainAiClient implements AiClient {
    private final RestClient client;

    public LangChainAiClient(@Value("${app.ai.base-url:http://ai-service:8000}") String baseUrl,
                             @Value("${app.ai.timeout-seconds:60}") int timeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 10)))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public LawyerQuestionsResponse createGuideQuestions(AiRequestContext context, LawyerQuestionsRequest request) {
        return post("/lawyer/questions", request, LawyerQuestionsResponse.class);
    }

    @Override
    public LawyerArgumentResponse createArgumentDraft(AiRequestContext context, LawyerArgumentRequest request) {
        return post("/lawyer/argument", request, LawyerArgumentResponse.class);
    }

    @Override
    public LawyerDebateResponse createDebateTurn(AiRequestContext context, LawyerDebateRequest request) {
        return post("/lawyer/debate", request, LawyerDebateResponse.class);
    }

    @Override
    public JudgeVerdictResponse createVerdict(AiRequestContext context, JudgeVerdictRequest request) {
        return post("/judge/verdict", request, JudgeVerdictResponse.class);
    }

    // Java 요청 객체를 JSON으로 전송하고, 구조화된 응답을 지정한 DTO로 읽는다.
    private <T> T post(String path, Object body, Class<T> responseType) {
        return client.post().uri(path).body(body).retrieve().body(responseType);
    }
}
