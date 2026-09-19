package com.example.mednlp.rules;

import com.example.mednlp.config.MedNlpProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

/**
 * Serves the compiled {@link RuleSet} from an in-process Caffeine cache in front of MySQL.
 *
 * <ul>
 *   <li>Compiling regexes and building the Aho-Corasick tries is expensive, so it happens once per
 *       load, not once per request.</li>
 *   <li>{@code refreshAfterWrite}: once the TTL passes, the next call still gets the old rules
 *       instantly while a background reload runs. If the database is down that reload fails and the
 *       last good rules keep serving.</li>
 *   <li>{@link #refresh()} reloads on demand (admin endpoint) after operators edit rule tables.</li>
 * </ul>
 */
@Service
@DependsOn("seedDataLoader")
public class RuleService implements RuleProvider {

    private static final Logger log = LoggerFactory.getLogger(RuleService.class);
    private static final String KEY = "active";

    private final LoadingCache<String, RuleSet> cache;
    private final String providerId = "rules-" + UUID.randomUUID();

    public RuleService(RuleDefinitionSource source, MedNlpProperties props) {
        RuleSet.Options options = new RuleSet.Options(props.negationWindowTokens(), props.regexTimeoutMs());
        this.cache = Caffeine.newBuilder()
                .refreshAfterWrite(props.ruleCacheTtl())
                .build(key -> {
                    RuleSet loaded = RuleSetCompiler.compile(source.load(), options);
                    log.info("Rules loaded: {} concepts, {} trigger terms, {} regex rules, {} negation triggers, {} rejected",
                            loaded.conceptCount(), loaded.triggerTermCount(), loaded.regexRuleCount(),
                            loaded.negationTriggerCount(), loaded.rejected().size());
                    return loaded;
                });
    }

    @PostConstruct
    void warmUpAndRegister() {
        cache.get(KEY); // fail fast at startup if rules cannot be loaded
        RuleProviderRegistry.register(providerId, this);
    }

    @PreDestroy
    void unregister() {
        RuleProviderRegistry.unregister(providerId);
    }

    /** Id under which this service is registered for UIMA annotators (unique per instance, so app contexts never mix rules). */
    public String providerId() {
        return providerId;
    }

    @Override
    public RuleSet current() {
        return cache.get(KEY);
    }

    /** Reloads from the database now. On failure the previous rules stay active and the error is rethrown. */
    public RuleSet refresh() {
        try {
            return cache.refresh(KEY).join();
        } catch (CompletionException e) {
            throw new RuleLoadException("Could not reload rules; still serving the previous rule set", e.getCause());
        }
    }

    public static class RuleLoadException extends RuntimeException {
        public RuleLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
