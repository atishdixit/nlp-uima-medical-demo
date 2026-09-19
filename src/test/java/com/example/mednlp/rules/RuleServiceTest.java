package com.example.mednlp.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mednlp.config.MedNlpProperties;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** EC-23: the database going away after startup must not take the pipeline down. */
class RuleServiceTest {

    private static MedNlpProperties props(Duration ttl) {
        return new MedNlpProperties(4, 5000, 1_000_000, 6, 2000, ttl, true, true);
    }

    @Test
    void keepsServingTheLastGoodRulesWhenAReloadFails() {
        AtomicBoolean dbUp = new AtomicBoolean(true);
        RuleDefinitionSource source = () -> {
            if (!dbUp.get()) {
                throw new IllegalStateException("database is down");
            }
            return SeedFile.load().toDefinitions();
        };
        RuleService service = new RuleService(source, props(Duration.ofMinutes(5)));
        service.warmUpAndRegister();
        try {
            RuleSet before = service.current();
            assertThat(before.conceptCount()).isGreaterThan(30);

            dbUp.set(false);
            assertThatThrownBy(service::refresh).isInstanceOf(RuleService.RuleLoadException.class)
                    .hasMessageContaining("still serving the previous rule set");

            assertThat(service.current()).isSameAs(before);
            assertThat(service.current().conceptCount()).isGreaterThan(30);
        } finally {
            service.unregister();
        }
    }

    @Test
    void refreshPicksUpChangedDefinitions() {
        AtomicInteger version = new AtomicInteger(0);
        RuleDefinitionSource source = () -> version.get() == 0 ? RuleDefinitions.empty() : SeedFile.load().toDefinitions();
        RuleService service = new RuleService(source, props(Duration.ofMinutes(5)));
        service.warmUpAndRegister();
        try {
            assertThat(service.current().conceptCount()).isZero();
            version.set(1);
            assertThat(service.current().conceptCount()).as("still cached").isZero();
            assertThat(service.refresh().conceptCount()).isGreaterThan(30);
            assertThat(service.current().conceptCount()).isGreaterThan(30);
        } finally {
            service.unregister();
        }
    }

    @Test
    void startupFailsFastWhenRulesCannotBeLoaded() {
        RuleDefinitionSource broken = () -> {
            throw new IllegalStateException("cannot reach the database");
        };
        RuleService service = new RuleService(broken, props(Duration.ofMinutes(5)));
        assertThatThrownBy(service::warmUpAndRegister).isInstanceOf(RuntimeException.class);
    }

    @Test
    void eachServiceRegistersUnderItsOwnId() {
        RuleService a = new RuleService(RuleDefinitions::empty, props(Duration.ofMinutes(5)));
        RuleService b = new RuleService(RuleDefinitions::empty, props(Duration.ofMinutes(5)));
        assertThat(a.providerId()).isNotEqualTo(b.providerId());
    }
}
