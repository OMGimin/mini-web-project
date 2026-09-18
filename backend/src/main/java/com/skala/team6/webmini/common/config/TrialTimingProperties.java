package com.skala.team6.webmini.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.trial")
public record TrialTimingProperties(
        @Min(1) long introductionSeconds,
        @Min(1) long argumentSeconds,
        @Min(4) @Max(8) long debateTurns,
        @Min(1) long debateIntervalSeconds,
        @Min(1) long votingSeconds
) {
    @AssertTrue(message = "debate-turns must be 4 or 8")
    public boolean isBalancedDebateTurns() {
        return debateTurns == 4 || debateTurns == 8;
    }
}
