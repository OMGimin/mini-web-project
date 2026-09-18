package com.skala.team6.webmini.trial;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TrialAiExecutorConfig {
    @Bean("trialAiExecutor")
    public ThreadPoolTaskExecutor trialAiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("trial-ai-");
        executor.initialize();
        return executor;
    }

}
