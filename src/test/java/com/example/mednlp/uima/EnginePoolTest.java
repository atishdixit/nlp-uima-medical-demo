package com.example.mednlp.uima;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mednlp.rules.RuleProviderRegistry;
import com.example.mednlp.rules.RuleSet;
import com.example.mednlp.rules.RuleSetCompiler;
import com.example.mednlp.rules.SeedFile;
import com.example.mednlp.types.ClinicalConcept;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.uima.fit.util.JCasUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** EC-25 (pool exhaustion answers 503) and recovery after a failed analysis. */
class EnginePoolTest {

    private String providerId;

    @BeforeEach
    void register() {
        providerId = "pool-test-" + UUID.randomUUID();
        RuleSet rules = RuleSetCompiler.compile(SeedFile.load().toDefinitions(), new RuleSet.Options(6, 2000));
        RuleProviderRegistry.register(providerId, () -> rules);
    }

    @AfterEach
    void unregister() {
        RuleProviderRegistry.unregister(providerId);
    }

    private static int conceptCount(org.apache.uima.jcas.JCas jcas) {
        return JCasUtil.select(jcas, ClinicalConcept.class).size();
    }

    @Test
    void analysesAndReusesEngines() {
        EnginePool pool = new EnginePool(providerId, 2, 1000);
        try {
            for (int i = 0; i < 10; i++) {
                assertThat(pool.analyze("fever and cough", EnginePoolTest::conceptCount)).isEqualTo(2);
            }
        } finally {
            pool.destroy();
        }
    }

    @Test
    void failsFastWithEngineBusyWhenEveryEngineIsLeased() throws Exception {
        EnginePool pool = new EnginePool(providerId, 1, 150);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<Integer> holder = executor.submit(() -> pool.analyze("fever", jcas -> {
                holding.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return conceptCount(jcas);
            }));
            assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> pool.analyze("cough", EnginePoolTest::conceptCount))
                    .isInstanceOf(EnginePool.EngineBusyException.class);

            release.countDown();
            assertThat(holder.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(pool.analyze("cough", EnginePoolTest::conceptCount)).as("pool recovers").isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
            pool.destroy();
        }
    }

    @Test
    void anExtractorFailureDoesNotPoisonThePool() {
        EnginePool pool = new EnginePool(providerId, 1, 1000);
        try {
            assertThatThrownBy(() -> pool.analyze("fever", jcas -> {
                throw new IllegalStateException("mapping bug");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(pool.analyze("cough", EnginePoolTest::conceptCount)).isEqualTo(1);
        } finally {
            pool.destroy();
        }
    }

    @Test
    void poolSizeMustBePositive() {
        assertThatThrownBy(() -> new EnginePool(providerId, 0, 100)).isInstanceOf(IllegalArgumentException.class);
    }
}
