package com.skala.team6.webmini.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

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

    private <T> T post(String path, Object body, Class<T> responseType) {
        return client.post().uri(path).body(body).retrieve().body(responseType);
    }
}
