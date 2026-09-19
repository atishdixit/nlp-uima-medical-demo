package com.example.mednlp.uima.annotators;

import com.example.mednlp.rules.NormalizedText;
import com.example.mednlp.rules.RuleSet;
import com.example.mednlp.rules.RuleSet.ConceptInfo;
import com.example.mednlp.rules.TermMatcher;
import com.example.mednlp.types.ChartSection;
import com.example.mednlp.types.ClinicalConcept;
import java.util.ArrayList;
import java.util.List;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

/**
 * Stage 4. Dictionary lookup of trigger terms (Aho-Corasick) that maps text to vocabulary codes.
 * Longest match wins ("type 2 diabetes mellitus" beats "diabetes"), matches must sit on word
 * boundaries, and whitespace differences (double spaces, line breaks) are tolerated. Each concept
 * records the section it sits in and, from that section, who the finding refers to.
 */
public class ClinicalConceptAnnotator extends RuleAwareAnnotator {

    @Override
    public void process(JCas jcas) throws AnalysisEngineProcessException {
        RuleSet rules = rules();
        String text = jcas.getDocumentText();
        NormalizedText normalized = NormalizedText.of(text);
        List<TermMatcher.Match<ConceptInfo>> matches = rules.conceptMatcher().findAll(normalized.text());
        if (matches.isEmpty()) {
            return;
        }
        List<ChartSection> sections = new ArrayList<>(JCasUtil.select(jcas, ChartSection.class));

        for (TermMatcher.Match<ConceptInfo> match : matches) {
            int begin = normalized.toOriginalStart(match.start());
            int end = normalized.toOriginalEnd(match.end());
            ConceptInfo info = match.payload();
            ChartSection section = sectionAt(sections, begin);

            ClinicalConcept concept = new ClinicalConcept(jcas, begin, end);
            concept.setCode(info.code());
            concept.setCodeSystem(info.codeSystem());
            concept.setPreferredName(info.preferredName());
            concept.setCategory(info.category());
            concept.setNegated(false);
            concept.setSectionName(section == null ? "UNSECTIONED" : section.getName());
            concept.setExperiencer(section == null ? "PATIENT" : section.getExperiencer());
            concept.addToIndexes();
        }
    }

    /** Sections are ordered and non-overlapping, so binary search on the begin offset. */
    static ChartSection sectionAt(List<ChartSection> sections, int offset) {
        int lo = 0;
        int hi = sections.size() - 1;
        ChartSection candidate = null;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (sections.get(mid).getBegin() <= offset) {
                candidate = sections.get(mid);
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return candidate != null && offset < candidate.getEnd() ? candidate : null;
    }
}
