package com.example.mednlp.uima;

import com.example.mednlp.config.MedNlpProperties;
import com.example.mednlp.rules.RuleService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fixed-size pool of UIMA analysis engines, each paired with a reusable JCas. An engine and its CAS
 * are not safe for concurrent use, so a request leases one pair for the duration of its analysis.
 * When all are busy a request waits up to {@code mednlp.pool-borrow-timeout-ms}, then fails with
 * {@link EngineBusyException} (HTTP 503) instead of queueing without bound.
 */
@Component
public class EnginePool implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(EnginePool.class);

    private record Lease(AnalysisEngine engine, JCas jcas) {
    }

    private final String ruleProviderId;
    private final long borrowTimeoutMs;
    private final BlockingQueue<Lease> idle;
    private final int size;

    @Autowired
    public EnginePool(MedNlpProperties props, RuleService rules) {
        this(rules.providerId(), props.poolSize(), props.poolBorrowTimeoutMs());
    }

    public EnginePool(String ruleProviderId, int size, long borrowTimeoutMs) {
        if (size < 1) {
            throw new IllegalArgumentException("pool size must be >= 1");
        }
        this.ruleProviderId = ruleProviderId;
        this.borrowTimeoutMs = borrowTimeoutMs;
        this.size = size;
        this.idle = new ArrayBlockingQueue<>(size);
        for (int i = 0; i < size; i++) {
            idle.add(newLease());
        }
        log.info("UIMA engine pool ready: {} engines", size);
    }

    public int size() {
        return size;
    }

    /**
     * Runs the pipeline over {@code text} and lets {@code extractor} read the annotations while the
     * lease is still held (the JCas is reset as soon as it is returned).
     */
    public <R> R analyze(String text, Function<JCas, R> extractor) {
        Lease lease = borrow();
        boolean healthy = false;
        try {
            lease.jcas().setDocumentText(text);
            lease.engine().process(lease.jcas());
            R result = extractor.apply(lease.jcas());
            healthy = true;
            return result;
        } catch (AnalysisEngineProcessException e) {
            throw new PipelineException("Analysis failed", e);
        } finally {
            giveBack(lease, healthy);
        }
    }

    private Lease borrow() {
        try {
            Lease lease = idle.poll(borrowTimeoutMs, TimeUnit.MILLISECONDS);
            if (lease == null) {
                throw new EngineBusyException("All " + size + " analysis engines are busy");
            }
            return lease;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineBusyException("Interrupted while waiting for an analysis engine");
        }
    }

    private void giveBack(Lease lease, boolean healthy) {
        Lease toReturn = lease;
        try {
            lease.jcas().reset();
            if (!healthy) {
                // a failed run may leave the engine in an unknown state: replace it rather than reuse it
                lease.engine().destroy();
                toReturn = newLease();
            }
        } catch (RuntimeException e) {
            log.warn("Replacing an analysis engine after cleanup failed", e);
            toReturn = newLease();
        }
        idle.add(toReturn);
    }

    private Lease newLease() {
        AnalysisEngine engine = MedicalPipeline.createEngine(ruleProviderId);
        try {
            return new Lease(engine, engine.newJCas());
        } catch (ResourceInitializationException e) {
            throw new IllegalStateException("Cannot create a JCas", e);
        }
    }

    @Override
    public void destroy() {
        Lease lease;
        while ((lease = idle.poll()) != null) {
            lease.engine().destroy();
        }
    }

    public static class EngineBusyException extends RuntimeException {
        public EngineBusyException(String message) {
            super(message);
        }
    }

    public static class PipelineException extends RuntimeException {
        public PipelineException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
