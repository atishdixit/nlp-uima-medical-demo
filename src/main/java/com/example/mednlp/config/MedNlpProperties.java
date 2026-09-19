package com.example.mednlp.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunables under the {@code mednlp.*} prefix; see application.yml for what each one means. */
@ConfigurationProperties(prefix = "mednlp")
public record MedNlpProperties(
        @DefaultValue("4") int poolSize,
        @DefaultValue("5000") long poolBorrowTimeoutMs,
        @DefaultValue("1000000") int maxChars,
        @DefaultValue("6") int negationWindowTokens,
        @DefaultValue("2000") long regexTimeoutMs,
        @DefaultValue("PT5M") Duration ruleCacheTtl,
        @DefaultValue("true") boolean auditEnabled,
        @DefaultValue("true") boolean seedEnabled) {
}
