package com.example.mednlp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "trigger_term")
public class TriggerTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String term;

    private boolean caseSensitive;

    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concept_id", nullable = false)
    private Concept concept;

    protected TriggerTerm() {
    }

    public TriggerTerm(String term, boolean caseSensitive, Concept concept) {
        this.term = term;
        this.caseSensitive = caseSensitive;
        this.concept = concept;
    }

    public Long getId() { return id; }
    public String getTerm() { return term; }
    public boolean isCaseSensitive() { return caseSensitive; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Concept getConcept() { return concept; }
}
