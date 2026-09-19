package com.example.mednlp.uima.annotators;

import com.example.mednlp.types.ChartSentence;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.jcas.JCas;

/**
 * Stage 2. Rule-based sentence/clause splitter tuned for clinical charts, where the sentence is
 * the scope boundary for negation. A boundary is: a line break, a semicolon, or .!? followed by
 * whitespace. A period is NOT a boundary inside decimals (98.6), after known abbreviations
 * (Dr., Pt., e.g.), after a single-letter initial (J. Smith), after a list number ("1. Aspirin"),
 * or when the next word starts in lower case.
 * Limitation: a sentence hard-wrapped over several lines is split at each line break.
 */
public class SentenceAnnotator extends JCasAnnotator_ImplBase {

    /**
     * Abbreviations that almost never end a sentence. Units and frequencies (mg, b.i.d.) are
     * deliberately absent: "Take 5 mg. Continue exercise." must still split.
     */
    private static final Set<String> ABBREVIATIONS = Set.of(
            "dr", "mr", "mrs", "ms", "prof", "vs", "pt", "approx", "hx", "dx", "tx", "sx", "fx", "rx",
            "e.g", "i.e", "st");

    @Override
    public void process(JCas jcas) throws AnalysisEngineProcessException {
        for (int[] span : split(jcas.getDocumentText())) {
            new ChartSentence(jcas, span[0], span[1]).addToIndexes();
        }
    }

    /** Returns [begin, end) offsets of each sentence, whitespace-trimmed; exposed for unit tests. */
    static List<int[]> split(String text) {
        List<int[]> spans = new ArrayList<>();
        int start = 0;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                addTrimmed(spans, text, start, i);
                start = i + 1;
            } else if (c == ';') {
                addTrimmed(spans, text, start, i + 1);
                start = i + 1;
            } else if ((c == '.' || c == '!' || c == '?') && isBoundary(text, i, start)) {
                addTrimmed(spans, text, start, i + 1);
                start = i + 1;
            }
        }
        addTrimmed(spans, text, start, n);
        return spans;
    }

    private static boolean isBoundary(String text, int i, int sentenceStart) {
        int n = text.length();
        if (i + 1 < n && !Character.isWhitespace(text.charAt(i + 1))) {
            return false; // 98.6, e.g, "?!" (the last mark decides), URLs, dotted abbreviations
        }
        if (text.charAt(i) != '.') {
            return true;
        }
        int j = i + 1;
        while (j < n && (text.charAt(j) == ' ' || text.charAt(j) == '\t')) {
            j++;
        }
        if (j >= n || text.charAt(j) == '\n' || text.charAt(j) == '\r') {
            return true; // period ends the line
        }
        if (Character.isLowerCase(text.charAt(j))) {
            return false; // "... Dr. smith", "fever. and cough" - the sentence continues
        }
        int tokenStart = i;
        while (tokenStart > 0 && !Character.isWhitespace(text.charAt(tokenStart - 1))) {
            tokenStart--;
        }
        String token = text.substring(tokenStart, i).replaceAll("^[(\\[\"']+", "").toLowerCase(Locale.ROOT);
        boolean temperatureUnit = token.equals("f") || token.equals("c"); // "98.6 F. BP normal."
        if (token.length() == 1 && Character.isLetter(token.charAt(0)) && !temperatureUnit) {
            return false; // initial, as in "J. Smith"
        }
        if (ABBREVIATIONS.contains(token)) {
            return false;
        }
        boolean listNumber = token.length() <= 2 && !token.isEmpty() && token.chars().allMatch(Character::isDigit);
        return !(listNumber && isAtSentenceStart(text, tokenStart, sentenceStart));
    }

    private static boolean isAtSentenceStart(String text, int tokenStart, int sentenceStart) {
        for (int k = sentenceStart; k < tokenStart; k++) {
            if (!Character.isWhitespace(text.charAt(k)) && text.charAt(k) != '(') {
                return false;
            }
        }
        return true;
    }

    private static void addTrimmed(List<int[]> spans, String text, int begin, int end) {
        while (begin < end && Character.isWhitespace(text.charAt(begin))) {
            begin++;
        }
        while (end > begin && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (begin >= end) {
            return;
        }
        for (int k = begin; k < end; k++) {
            if (Character.isLetterOrDigit(text.charAt(k))) {
                spans.add(new int[] {begin, end});
                return;
            }
        }
    }
}
