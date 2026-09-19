package com.example.mednlp.uima.annotators;

import com.example.mednlp.rules.NormalizedText;
import com.example.mednlp.rules.RuleSet;
import com.example.mednlp.rules.RuleSet.TriggerType;
import com.example.mednlp.rules.TermMatcher.Match;
import com.example.mednlp.types.ChartSentence;
import com.example.mednlp.types.ClinicalConcept;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

/**
 * Stage 5. NegEx-style negation detection, scoped to a sentence.
 *
 * <ul>
 *   <li>PRE triggers ("denies", "no evidence of") negate concepts that FOLLOW them, POST triggers
 *       ("was ruled out") negate concepts that PRECEDE them, within N tokens
 *       ({@code mednlp.negation-window-tokens}).</li>
 *   <li>A TERMINATION trigger ("but", "however") between trigger and concept stops the negation.</li>
 *   <li>PSEUDO triggers ("no increase", "not certain") look like negation but are not; they win
 *       over the shorter real trigger they contain and are then ignored.</li>
 * </ul>
 * Triggers are matched with the same longest-match rule as concepts, so "no evidence of" beats "no".
 */
public class NegationAnnotator extends RuleAwareAnnotator {

    @Override
    public void process(JCas jcas) throws AnalysisEngineProcessException {
        RuleSet rules = rules();
        List<ClinicalConcept> concepts = new ArrayList<>(JCasUtil.select(jcas, ClinicalConcept.class));
        if (concepts.isEmpty()) {
            return;
        }
        List<ChartSentence> sentences = new ArrayList<>(JCasUtil.select(jcas, ChartSentence.class));

        Map<ChartSentence, List<ClinicalConcept>> bySentence = new LinkedHashMap<>();
        for (ClinicalConcept concept : concepts) {
            ChartSentence sentence = sentenceAt(sentences, concept.getBegin());
            if (sentence != null) {
                bySentence.computeIfAbsent(sentence, s -> new ArrayList<>()).add(concept);
            }
        }
        bySentence.forEach((sentence, inSentence) -> negate(rules, sentence, inSentence));
    }

    private void negate(RuleSet rules, ChartSentence sentence, List<ClinicalConcept> concepts) {
        NormalizedText nt = NormalizedText.of(sentence.getCoveredText());
        String text = nt.text();

        // concept ranges in normalised sentence coordinates
        int[][] ranges = new int[concepts.size()][];
        for (int i = 0; i < concepts.size(); i++) {
            ClinicalConcept c = concepts.get(i);
            int relBegin = c.getBegin() - sentence.getBegin();
            int relEnd = Math.min(c.getEnd(), sentence.getEnd()) - sentence.getBegin();
            ranges[i] = relEnd > relBegin ? new int[] {nt.fromOriginalStart(relBegin), nt.fromOriginalEnd(relEnd)} : null;
        }

        // real triggers only: pseudo triggers have already consumed their text; triggers inside a concept are not triggers
        List<Match<TriggerType>> triggers = new ArrayList<>();
        for (Match<TriggerType> m : rules.triggerMatcher().findAll(text)) {
            if (m.payload() != TriggerType.PSEUDO && !overlapsAny(ranges, m.start(), m.end())) {
                triggers.add(m);
            }
        }
        if (triggers.isEmpty()) {
            return;
        }

        int window = rules.options().negationWindowTokens();
        for (int i = 0; i < concepts.size(); i++) {
            if (ranges[i] == null) {
                continue;
            }
            Match<TriggerType> cause = findNegatingTrigger(triggers, text, ranges[i][0], ranges[i][1], window);
            if (cause != null) {
                concepts.get(i).setNegated(true);
                concepts.get(i).setNegationTrigger(cause.text());
            }
        }
    }

    private static Match<TriggerType> findNegatingTrigger(List<Match<TriggerType>> triggers, String text,
                                                          int conceptStart, int conceptEnd, int window) {
        // nearest trigger before the concept
        for (int i = triggers.size() - 1; i >= 0; i--) {
            Match<TriggerType> t = triggers.get(i);
            if (t.end() > conceptStart) {
                continue;
            }
            if (t.payload() == TriggerType.TERMINATION) {
                break;
            }
            if (t.payload() == TriggerType.PRE) {
                if (countTokens(text, t.end(), conceptStart) <= window) {
                    return t;
                }
            }
            break; // only the nearest preceding trigger can apply
        }
        // nearest trigger after the concept
        for (Match<TriggerType> t : triggers) {
            if (t.start() < conceptEnd) {
                continue;
            }
            if (t.payload() == TriggerType.POST && countTokens(text, conceptEnd, t.start()) <= window) {
                return t;
            }
            break;
        }
        return null;
    }

    private static boolean overlapsAny(int[][] ranges, int start, int end) {
        for (int[] r : ranges) {
            if (r != null && start < r[1] && r[0] < end) {
                return true;
            }
        }
        return false;
    }

    /** Whitespace-separated tokens containing at least one letter or digit in {@code text[from, to)}. */
    static int countTokens(String text, int from, int to) {
        int count = 0;
        boolean inToken = false;
        boolean hasWordChar = false;
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                if (inToken && hasWordChar) {
                    count++;
                }
                inToken = false;
                hasWordChar = false;
            } else {
                inToken = true;
                hasWordChar |= Character.isLetterOrDigit(c);
            }
        }
        if (inToken && hasWordChar) {
            count++;
        }
        return count;
    }

    private static ChartSentence sentenceAt(List<ChartSentence> sentences, int offset) {
        int lo = 0;
        int hi = sentences.size() - 1;
        ChartSentence candidate = null;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (sentences.get(mid).getBegin() <= offset) {
                candidate = sentences.get(mid);
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return candidate != null && offset < candidate.getEnd() ? candidate : null;
    }
}
