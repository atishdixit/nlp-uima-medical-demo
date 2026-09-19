package com.example.mednlp.uima;

import com.example.mednlp.uima.annotators.ClinicalConceptAnnotator;
import com.example.mednlp.uima.annotators.MeasurementAnnotator;
import com.example.mednlp.uima.annotators.NegationAnnotator;
import com.example.mednlp.uima.annotators.PhiAnnotator;
import com.example.mednlp.uima.annotators.RuleAwareAnnotator;
import com.example.mednlp.uima.annotators.SectionAnnotator;
import com.example.mednlp.uima.annotators.SentenceAnnotator;
import java.io.IOException;
import java.net.URL;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLInputSource;

/**
 * Assembles the single demo pipeline (an aggregate analysis engine with a fixed flow):
 * Section -> Sentence -> PHI -> ClinicalConcept -> Negation -> Measurement.
 * Order matters: Negation needs sentences and concepts; concepts want sections.
 */
public final class MedicalPipeline {

    public static final String TYPE_SYSTEM_RESOURCE = "desc/MedicalTypeSystem.xml";

    private MedicalPipeline() {
    }

    public static TypeSystemDescription typeSystem() {
        URL url = MedicalPipeline.class.getClassLoader().getResource(TYPE_SYSTEM_RESOURCE);
        if (url == null) {
            throw new IllegalStateException("Type system not found on classpath: " + TYPE_SYSTEM_RESOURCE);
        }
        try {
            TypeSystemDescription description = UIMAFramework.getXMLParser().parseTypeSystemDescription(new XMLInputSource(url));
            description.resolveImports();
            return description;
        } catch (IOException | InvalidXMLException e) {
            throw new IllegalStateException("Cannot parse " + TYPE_SYSTEM_RESOURCE, e);
        }
    }

    public static AnalysisEngineDescription description(String ruleProviderId) {
        try {
            TypeSystemDescription ts = typeSystem();
            String param = RuleAwareAnnotator.PARAM_RULE_PROVIDER_ID;
            return AnalysisEngineFactory.createEngineDescription(
                    AnalysisEngineFactory.createEngineDescription(SectionAnnotator.class, ts, param, ruleProviderId),
                    AnalysisEngineFactory.createEngineDescription(SentenceAnnotator.class, ts),
                    AnalysisEngineFactory.createEngineDescription(PhiAnnotator.class, ts, param, ruleProviderId),
                    AnalysisEngineFactory.createEngineDescription(ClinicalConceptAnnotator.class, ts, param, ruleProviderId),
                    AnalysisEngineFactory.createEngineDescription(NegationAnnotator.class, ts, param, ruleProviderId),
                    AnalysisEngineFactory.createEngineDescription(MeasurementAnnotator.class, ts, param, ruleProviderId));
        } catch (ResourceInitializationException e) {
            throw new IllegalStateException("Cannot build the medical pipeline description", e);
        }
    }

    public static AnalysisEngine createEngine(String ruleProviderId) {
        try {
            return AnalysisEngineFactory.createEngine(description(ruleProviderId));
        } catch (ResourceInitializationException e) {
            throw new IllegalStateException("Cannot create the medical analysis engine", e);
        }
    }
}
