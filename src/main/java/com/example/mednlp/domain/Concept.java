package com.example.mednlp.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/** A vocabulary concept (ICD-10-CM, RxNorm, LOINC ...) and the trigger terms that evoke it. */
@Entity
@Table(name = "concept")
public class Concept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String codeSystem;

    @Column(nullable = false)
    private String preferredName;

    @Column(nullable = false)
    private String category;

    private boolean enabled = true;

    @OneToMany(mappedBy = "concept", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TriggerTerm> terms = new ArrayList<>();

    protected Concept() {
    }

    public Concept(String code, String codeSystem, String preferredName, String category) {
        this.code = code;
        this.codeSystem = codeSystem;
        this.preferredName = preferredName;
        this.category = category;
    }

    public Concept addTerm(String term, boolean caseSensitive) {
        terms.add(new TriggerTerm(term, caseSensitive, this));
        return this;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getCodeSystem() { return codeSystem; }
    public String getPreferredName() { return preferredName; }
    public String getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<TriggerTerm> getTerms() { return terms; }
}
