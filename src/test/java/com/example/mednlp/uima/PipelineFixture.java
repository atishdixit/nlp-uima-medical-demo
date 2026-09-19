package com.example.mednlp.uima;

import com.example.mednlp.rules.RuleDefinitions;
import com.example.mednlp.rules.RuleProviderRegistry;
import com.example.mednlp.rules.RuleSet;
import com.example.mednlp.rules.RuleSetCompiler;
import com.example.mednlp.rules.SeedFile;
import com.example.mednlp.types.ChartSection;
import com.example.mednlp.types.ChartSentence;
import com.example.mednlp.types.ClinicalConcept;
import com.example.mednlp.types.Measurement;
import com.example.mednlp.types.PhiSpan;
import java.util.List;
import java.util.UUID;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

/**
 * Test harness that runs the real UIMA pipeline without Spring or a database: rules come straight
 * from the seed JSON (or from hand-built definitions) through the production compiler.
 */
public final class PipelineFixture implements AutoCloseable {

    private final String providerId = "test-" + UUID.randomUUID();
    private final AnalysisEngine engine;
    private final JCas jcas;
    private final RuleSet rules;

    public PipelineFixture(RuleDefinitions definitions, RuleSet.Options options) {
        this.rules = RuleSetCompiler.compile(definitions, options);
        RuleProviderRegistry.register(providerId, () -> rules);
        this.engine = MedicalPipeline.createEngine(providerId);
        try {
            this.jcas = engine.newJCas();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static PipelineFixture seeded() {
        return new PipelineFixture(SeedFile.load().toDefinitions(), new RuleSet.Options(6, 2000));
    }

    public static PipelineFixture with(RuleDefinitions definitions) {
        return new PipelineFixture(definitions, new RuleSet.Options(6, 2000));
    }

    public PipelineFixture run(String text) {
        try {
            jcas.reset();
            jcas.setDocumentText(text);
            engine.process(jcas);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return this;
    }

    public RuleSet rules() {
        return rules;
    }

    public List<ClinicalConcept> concepts() {
        return List.copyOf(JCasUtil.select(jcas, ClinicalConcept.class));
    }

    public List<ChartSection> sections() {
        return List.copyOf(JCasUtil.select(jcas, ChartSection.class));
    }

    public List<ChartSentence> sentences() {
        return List.copyOf(JCasUtil.select(jcas, ChartSentence.class));
    }

    public List<PhiSpan> phi() {
        return List.copyOf(JCasUtil.select(jcas, PhiSpan.class));
    }

    public List<Measurement> measurements() {
        return List.copyOf(JCasUtil.select(jcas, Measurement.class));
    }

    /** Covered texts of negated concepts, in document order. */
    public List<String> negated() {
        return concepts().stream().filter(ClinicalConcept::getNegated).map(ClinicalConcept::getCoveredText).toList();
    }

    /** Covered texts of concepts that are not negated, in document order. */
    public List<String> affirmed() {
        return concepts().stream().filter(c -> !c.getNegated()).map(ClinicalConcept::getCoveredText).toList();
    }

    public ClinicalConcept concept(String coveredText) {
        return concepts().stream().filter(c -> c.getCoveredText().equalsIgnoreCase(coveredText)).findFirst()
                .orElseThrow(() -> new AssertionError("No concept covering '" + coveredText + "' in " + concepts().stream()
                        .map(ClinicalConcept::getCoveredText).toList()));
    }

    @Override
    public void close() {
        engine.destroy();
        RuleProviderRegistry.unregister(providerId);
    }
}
