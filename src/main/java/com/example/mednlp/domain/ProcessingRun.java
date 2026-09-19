package com.example.mednlp.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Audit row for one analysis. Holds counts and timing only, never chart text (PHI). */
@Entity
@Table(name = "processing_run")
public class ProcessingRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant createdAt;
    private int charCount;
    private int conceptCount;
    private int negatedCount;
    private int phiCount;
    private long durationMs;

    protected ProcessingRun() {
    }

    public ProcessingRun(int charCount, int conceptCount, int negatedCount, int phiCount, long durationMs) {
        this.createdAt = Instant.now();
        this.charCount = charCount;
        this.conceptCount = conceptCount;
        this.negatedCount = negatedCount;
        this.phiCount = phiCount;
        this.durationMs = durationMs;
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public int getCharCount() { return charCount; }
    public int getConceptCount() { return conceptCount; }
    public int getNegatedCount() { return negatedCount; }
    public int getPhiCount() { return phiCount; }
    public long getDurationMs() { return durationMs; }
}
